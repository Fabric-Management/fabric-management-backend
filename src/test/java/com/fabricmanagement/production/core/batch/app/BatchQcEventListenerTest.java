package com.fabricmanagement.production.core.batch.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.quality.decision.app.QualityDecisionService;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import com.fabricmanagement.production.quality.result.domain.event.BatchConcessionReviewRequiredEvent;
import com.fabricmanagement.production.quality.result.domain.event.FiberTestResultApprovedEvent;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BatchQcEventListenerTest {

  // Detect a regression if the listener takes a dependency on stock decisions again.
  @Mock private QualityDecisionService decisionService;
  @Mock private BatchRepository batchRepository;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private IdempotentEventHandler idempotentEventHandler;
  @InjectMocks private BatchQcEventListener listener;

  @ParameterizedTest
  @EnumSource(
      value = TestApprovalStatus.class,
      names = {"APPROVED", "REJECTED"})
  void approvedOrRejectedEventRecordsNoDecision(TestApprovalStatus status) {
    executeHandlerBody();
    // Exercise the former selected-bale release and whole-lot rejection paths.
    UUID unitId = status == TestApprovalStatus.APPROVED ? UUID.randomUUID() : null;
    FiberTestResultApprovedEvent event =
        new FiberTestResultApprovedEvent(
            UUID.randomUUID(), UUID.randomUUID(), unitId, status, UUID.randomUUID());

    listener.onFiberTestResultApproved(event);

    verifyNoInteractions(decisionService, batchRepository, eventPublisher);
  }

  @Test
  void conditionalAcceptLeavesDispositionPendingAndNotifiesForConcessionReview() {
    executeHandlerBody();
    Batch batch = mock(Batch.class);
    when(batch.getBatchCode()).thenReturn("B-2026-0042");
    when(batchRepository.findById(any())).thenReturn(Optional.of(batch));
    FiberTestResultApprovedEvent event =
        new FiberTestResultApprovedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            TestApprovalStatus.CONDITIONAL_ACCEPT,
            UUID.randomUUID());

    listener.onFiberTestResultApproved(event);

    verifyNoInteractions(decisionService);
    ArgumentCaptor<BatchConcessionReviewRequiredEvent> published =
        ArgumentCaptor.forClass(BatchConcessionReviewRequiredEvent.class);
    verify(eventPublisher).publish(published.capture());
    assertThat(published.getValue().getTenantId()).isEqualTo(event.getTenantId());
    assertThat(published.getValue().getBatchId()).isEqualTo(event.getBatchId());
    assertThat(published.getValue().getBatchCode()).isEqualTo("B-2026-0042");
  }

  private void executeHandlerBody() {
    doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(3).run();
              return null;
            })
        .when(idempotentEventHandler)
        .executeOnce(any(), any(), any(), any());
  }
}
