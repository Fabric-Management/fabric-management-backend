package com.fabricmanagement.common.infrastructure.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FollowUpFeedbackServiceTest {

  @Mock private IncompleteFollowUpFlagRepository flagRepository;
  @Mock private StuckEventFeedbackSender feedbackSender;

  private FollowUpFeedbackService service;
  private UUID tenantId;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);
    service = new FollowUpFeedbackService(flagRepository, feedbackSender);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void firstReportSendsOnceAndMarksFlag() {
    IncompleteFollowUpFlag flag = flag();
    when(flagRepository.findByTenantIdAndIdForUpdate(tenantId, flag.getId()))
        .thenReturn(Optional.of(flag));

    service.report(flag.getId());

    ArgumentCaptor<FollowUpFeedbackReport> reportCaptor =
        ArgumentCaptor.forClass(FollowUpFeedbackReport.class);
    verify(feedbackSender).sendOpsReport(reportCaptor.capture());
    assertThat(reportCaptor.getValue().tenantId()).isEqualTo(tenantId);
    assertThat(reportCaptor.getValue().publicationId()).isEqualTo(flag.getPublicationId());
    assertThat(flag.getFeedbackReportedAt()).isNotNull();
    verify(flagRepository).save(flag);
    verify(flagRepository).findByTenantIdAndIdForUpdate(tenantId, flag.getId());
  }

  @Test
  void alreadyReportedFlagReturnsWithoutSendingAgain() {
    IncompleteFollowUpFlag flag = flag();
    flag.markFeedbackReported(Instant.now().minusSeconds(60));
    when(flagRepository.findByTenantIdAndIdForUpdate(tenantId, flag.getId()))
        .thenReturn(Optional.of(flag));

    service.report(flag.getId());

    verifyNoInteractions(feedbackSender);
    verify(flagRepository, never()).save(flag);
  }

  @Test
  void missingFlagSignalsNotFoundWithoutSending() {
    UUID flagId = UUID.randomUUID();
    when(flagRepository.findByTenantIdAndIdForUpdate(tenantId, flagId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.report(flagId))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(flagId.toString());

    verifyNoInteractions(feedbackSender);
  }

  private IncompleteFollowUpFlag flag() {
    UUID entityId = UUID.randomUUID();
    IncompleteFollowUpFlag flag =
        IncompleteFollowUpFlag.raise(
            tenantId,
            UUID.randomUUID(),
            "SUPPLIER_QUOTE_ACCEPTED",
            new StuckEventPresentation(
                "SUPPLIER_QUOTE",
                entityId,
                "SQ-1042",
                "Purchase order creation for quote SQ-1042 did not complete.",
                "SUPPLIER_QUOTE",
                entityId,
                UUID.randomUUID()));
    flag.setId(UUID.randomUUID());
    flag.setCreatedAt(Instant.now().minusSeconds(900));
    return flag;
  }
}
