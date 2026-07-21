package com.fabricmanagement.production.execution.batch.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.platform.communication.app.InAppNotificationService;
import com.fabricmanagement.production.quality.decision.app.QualityDecisionCommand;
import com.fabricmanagement.production.quality.decision.app.QualityDecisionService;
import com.fabricmanagement.production.quality.decision.app.TrustedDecisionContext;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionOutcome;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionScope;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import com.fabricmanagement.production.quality.result.domain.event.FiberTestResultApprovedEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BatchQcEventListenerTest {

  @Mock private QualityDecisionService decisionService;
  @Mock private InAppNotificationService notificationService;
  @Mock private IdempotentEventHandler idempotentEventHandler;
  @InjectMocks private BatchQcEventListener listener;

  @Test
  void approvedUnitEventCreatesSelectedReleasedDecisionWithEventProvenance() {
    executeHandlerBody();
    UUID tenantId = UUID.randomUUID();
    UUID batchId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    FiberTestResultApprovedEvent event =
        new FiberTestResultApprovedEvent(
            tenantId, batchId, unitId, TestApprovalStatus.APPROVED, actorId);

    listener.onFiberTestResultApproved(event);

    ArgumentCaptor<TrustedDecisionContext> contextCaptor =
        ArgumentCaptor.forClass(TrustedDecisionContext.class);
    ArgumentCaptor<QualityDecisionCommand> commandCaptor =
        ArgumentCaptor.forClass(QualityDecisionCommand.class);
    verify(decisionService).recordDecision(contextCaptor.capture(), commandCaptor.capture());
    assertThat(contextCaptor.getValue().actorId()).isEqualTo(actorId);
    assertThat(contextCaptor.getValue().sourceEventId()).isEqualTo(event.getEventId());
    assertThat(commandCaptor.getValue().scope()).isEqualTo(QualityDecisionScope.SELECTED_UNITS);
    assertThat(commandCaptor.getValue().outcome()).isEqualTo(QualityDecisionOutcome.RELEASED);
    assertThat(commandCaptor.getValue().stockUnitIds()).containsExactly(unitId);
  }

  @Test
  void conditionalAcceptLeavesDispositionPendingAndNotifiesForConcessionReview() {
    executeHandlerBody();
    FiberTestResultApprovedEvent event =
        new FiberTestResultApprovedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            TestApprovalStatus.CONDITIONAL_ACCEPT,
            UUID.randomUUID());

    listener.onFiberTestResultApproved(event);

    verify(decisionService, never()).recordDecision(any(), any());
    verify(notificationService)
        .sendToTenantRoles(
            eq(event.getTenantId()),
            eq(InAppNotificationService.QUARANTINE_NOTIFY_ROLES),
            any(),
            eq("Concession review required"),
            any(),
            eq(event.getBatchId()),
            eq("BATCH"),
            any());
  }

  @Test
  void rejectedBatchEventCreatesFullLotNonconformingDecision() {
    executeHandlerBody();
    FiberTestResultApprovedEvent event =
        new FiberTestResultApprovedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            TestApprovalStatus.REJECTED,
            UUID.randomUUID());

    listener.onFiberTestResultApproved(event);

    ArgumentCaptor<QualityDecisionCommand> commandCaptor =
        ArgumentCaptor.forClass(QualityDecisionCommand.class);
    verify(decisionService).recordDecision(any(), commandCaptor.capture());
    assertThat(commandCaptor.getValue().scope()).isEqualTo(QualityDecisionScope.FULL_LOT);
    assertThat(commandCaptor.getValue().outcome()).isEqualTo(QualityDecisionOutcome.NONCONFORMING);
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
