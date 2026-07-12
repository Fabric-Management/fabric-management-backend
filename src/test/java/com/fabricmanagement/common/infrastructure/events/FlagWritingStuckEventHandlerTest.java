package com.fabricmanagement.common.infrastructure.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class FlagWritingStuckEventHandlerTest {

  private static final String EVENT_TYPE = "SUPPLIER_QUOTE_ACCEPTED";

  @Mock private IncompleteFollowUpFlagRepository flagRepository;
  @Mock private StuckEventPresenter presenter;
  @Mock private TenantSessionBinder tenantSessionBinder;
  @Mock private ObjectProvider<FollowUpResolutionNotifier> notifierProvider;
  @Mock private FollowUpResolutionNotifier notifier;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private FlagWritingStuckEventHandler handler;
  private UUID tenantId;
  private StuckEventPresentation presentation;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    presentation =
        new StuckEventPresentation(
            "SUPPLIER_QUOTE",
            UUID.randomUUID(),
            "SQ-1042",
            "Purchase order creation for quote SQ-1042 did not complete.",
            "SUPPLIER_QUOTE",
            UUID.randomUUID(),
            UUID.randomUUID());
    lenient()
        .doAnswer(
            invocation -> {
              Consumer<FollowUpResolutionNotifier> consumer = invocation.getArgument(0);
              consumer.accept(notifier);
              return null;
            })
        .when(notifierProvider)
        .ifAvailable(any());
    handler =
        new FlagWritingStuckEventHandler(
            flagRepository,
            List.of(presenter, new GenericStuckEventPresenter()),
            objectMapper,
            tenantSessionBinder,
            notifierProvider);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void newlyStuckWritesOneActiveFlagInsideTenantContext() {
    StuckEventContext context = context(tenantId, UUID.randomUUID());
    stubPresentation();
    when(flagRepository.findByTenantIdAndPublicationId(tenantId, context.publicationId()))
        .thenReturn(Optional.empty());

    handler.onNewlyStuck(context);

    ArgumentCaptor<IncompleteFollowUpFlag> flagCaptor =
        ArgumentCaptor.forClass(IncompleteFollowUpFlag.class);
    verify(flagRepository).save(flagCaptor.capture());
    IncompleteFollowUpFlag saved = flagCaptor.getValue();
    assertThat(saved.getTenantId()).isEqualTo(tenantId);
    assertThat(saved.getPublicationId()).isEqualTo(context.publicationId());
    assertThat(saved.getStatus()).isEqualTo(FollowUpFlagStatus.ACTIVE);
    assertThat(saved.getEntityRef()).isEqualTo("SQ-1042");
    verify(tenantSessionBinder).bindToCurrentSession(tenantId);
  }

  @Test
  void secondCallForSameActivePublicationDoesNotCreateDuplicate() {
    StuckEventContext context = context(tenantId, UUID.randomUUID());
    IncompleteFollowUpFlag existing =
        IncompleteFollowUpFlag.raise(
            tenantId, context.publicationId(), context.eventType(), presentation);
    stubPresentation();
    when(flagRepository.findByTenantIdAndPublicationId(tenantId, context.publicationId()))
        .thenReturn(Optional.empty(), Optional.of(existing));

    handler.onNewlyStuck(context);
    handler.onNewlyStuck(context);

    verify(flagRepository, times(1)).save(any(IncompleteFollowUpFlag.class));
  }

  @Test
  void resolvedPublicationFlipsActiveFlagAndSetsResolvedAt() {
    StuckEventContext context = context(tenantId, UUID.randomUUID());
    IncompleteFollowUpFlag existing =
        IncompleteFollowUpFlag.raise(
            tenantId, context.publicationId(), context.eventType(), presentation);
    when(flagRepository.findByTenantIdAndPublicationId(tenantId, context.publicationId()))
        .thenReturn(Optional.of(existing));

    handler.onResolved(context);

    assertThat(existing.getStatus()).isEqualTo(FollowUpFlagStatus.RESOLVED);
    assertThat(existing.getResolvedAt()).isNotNull().isBeforeOrEqualTo(Instant.now());
    InOrder notificationOrder = inOrder(flagRepository, notifier);
    notificationOrder.verify(flagRepository).save(existing);
    notificationOrder.verify(notifier).notifyResolved(any(ResolvedFollowUp.class));
    verify(tenantSessionBinder).bindToCurrentSession(tenantId);
  }

  @Test
  void resolvedPublicationWithoutActiveFlagDoesNotNotify() {
    StuckEventContext context = context(tenantId, UUID.randomUUID());
    when(flagRepository.findByTenantIdAndPublicationId(tenantId, context.publicationId()))
        .thenReturn(Optional.empty());

    handler.onResolved(context);

    verifyNoInteractions(notifier);
  }

  @Test
  void notifierFailureDoesNotPreventFlagResolution(CapturedOutput output) {
    StuckEventContext context = context(tenantId, UUID.randomUUID());
    IncompleteFollowUpFlag existing =
        IncompleteFollowUpFlag.raise(
            tenantId, context.publicationId(), context.eventType(), presentation);
    when(flagRepository.findByTenantIdAndPublicationId(tenantId, context.publicationId()))
        .thenReturn(Optional.of(existing));
    doThrow(new RuntimeException("notification unavailable"))
        .when(notifier)
        .notifyResolved(any(ResolvedFollowUp.class));

    assertThatCode(() -> handler.onResolved(context)).doesNotThrowAnyException();

    assertThat(existing.getStatus()).isEqualTo(FollowUpFlagStatus.RESOLVED);
    assertThat(existing.getResolvedAt()).isNotNull();
    verify(flagRepository).save(existing);
    assertThat(output.getAll()).contains("Failed to notify resolved follow-up");
  }

  @Test
  void nullTenantIsSkippedWithWarning(CapturedOutput output) {
    StuckEventContext context = context(null, UUID.randomUUID());

    handler.onNewlyStuck(context);

    verifyNoInteractions(flagRepository, tenantSessionBinder);
    assertThat(output.getAll()).contains("Skipping follow-up flag without tenant");
  }

  @Test
  void genericPresenterMatchesEveryEventType() {
    GenericStuckEventPresenter generic = new GenericStuckEventPresenter();

    assertThat(generic.supports("UNREGISTERED_EVENT")).isTrue();
    assertThat(generic.present(tenantId, objectMapper.createObjectNode()).entityType())
        .isEqualTo("UNKNOWN");
  }

  private void stubPresentation() {
    when(presenter.supports(EVENT_TYPE)).thenReturn(true);
    when(presenter.present(any(UUID.class), any(JsonNode.class))).thenReturn(presentation);
  }

  private StuckEventContext context(UUID contextTenantId, UUID publicationId) {
    return new StuckEventContext(
        publicationId,
        EVENT_TYPE,
        "listener",
        contextTenantId,
        "{\"quoteId\":\"" + presentation.entityId() + "\"}",
        Instant.now().minusSeconds(60));
  }
}
