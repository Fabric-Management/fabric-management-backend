package com.fabricmanagement.flowboard.decision.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.flowboard.decision.app.DecisionQueueService;
import com.fabricmanagement.flowboard.decision.app.listener.DecisionFollowEventListener;
import com.fabricmanagement.flowboard.decision.infra.repository.*;
import com.fabricmanagement.flowboard.decision.infra.repository.DecisionSubjectProjectionWriter.ApplyResult;
import com.fabricmanagement.flowboard.task.domain.event.TaskAssignedEvent;
import com.fabricmanagement.sales.salesorder.app.OrderCoverProjectionService;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverProjectionPort.Facts;
import com.fabricmanagement.sales.salesorder.infra.OrderCoverIntegrationSupport;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class DecisionProjectionReplayIT extends OrderCoverIntegrationSupport {
  @MockitoSpyBean private OrderCoverProjectionService source;
  @MockitoSpyBean private DecisionSubjectProjectionWriter writer;
  @Autowired private DecisionSubjectProjectionRepository projections;
  @Autowired private DecisionFollowEventListener followListener;
  @Autowired private DecisionQueueService queue;
  @Autowired private IncompleteEventPublications incompletePublications;
  @MockitoSpyBean private DecisionFollowRepository follows;

  @Test
  void taskAttachmentCannotBeRevertedByAnOlderSameRevisionDelivery() {
    Cover cover = governed(1);
    Facts attached = source.facts(tenant, java.util.List.of(cover.caseId())).getFirst();
    awaitCaseEventsSettled(cover.caseId());
    writer.delete(tenant, cover.caseId());
    assertThat(writer.apply(attached, UUID.randomUUID())).isEqualTo(ApplyResult.INSERTED);
    assertThat(writer.apply(withTask(attached, null), UUID.randomUUID()))
        .isEqualTo(ApplyResult.SKIPPED);
    assertThat(
            projections.findByTenantIdAndCaseId(tenant, cover.caseId()).orElseThrow().getTaskId())
        .isEqualTo(cover.taskId());
  }

  @Test
  void sameEvidenceWithNewerCaseRevisionWinsEvenWhenTheOldWriteCommitsLast() {
    Cover cover = governed(1);
    Facts base = source.facts(tenant, java.util.List.of(cover.caseId())).getFirst();
    awaitCaseEventsSettled(cover.caseId());
    writer.delete(tenant, cover.caseId());
    Facts newer = withRevisions(base, base.caseRevision() + 1, base.evidenceRevision());
    writer.apply(newer, UUID.randomUUID());
    assertThat(writer.apply(base, UUID.randomUUID())).isEqualTo(ApplyResult.SKIPPED);
    assertThat(
            projections
                .findByTenantIdAndCaseId(tenant, cover.caseId())
                .orElseThrow()
                .getCaseRevision())
        .isEqualTo(newer.caseRevision());
  }

  @Test
  void replayOfAnIdenticalFactIsAStableNoOp() {
    Cover cover = governed(1);
    Facts facts = source.facts(tenant, java.util.List.of(cover.caseId())).getFirst();
    awaitCaseEventsSettled(cover.caseId());
    writer.delete(tenant, cover.caseId());
    writer.apply(facts, UUID.randomUUID());
    assertThat(writer.apply(facts, UUID.randomUUID())).isEqualTo(ApplyResult.SKIPPED);
  }

  @Test
  void sourceFactsUsesItsOwnReadOnlyRepeatableReadTransaction() {
    Cover cover = governed(1);
    AtomicBoolean observed = new AtomicBoolean();
    doAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
              assertThat(jdbc.queryForObject("show transaction_isolation", String.class))
                  .isEqualTo("repeatable read");
              observed.set(true);
              return invocation.callRealMethod();
            })
        .when(sourceTarget())
        .facts(eq(tenant), eq(java.util.List.of(cover.caseId())));

    TransactionTemplate outer = new TransactionTemplate(transactions);
    outer.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    outer.executeWithoutResult(ignored -> source.facts(tenant, java.util.List.of(cover.caseId())));

    assertThat(observed).isTrue();
  }

  @Test
  void projectionWriterEntryPointsAlwaysJoinATenantBoundTransaction() throws Exception {
    assertThat(
            DecisionSubjectProjectionWriter.class
                .getMethod("apply", Facts.class, UUID.class)
                .getAnnotation(Transactional.class))
        .isNotNull();
    assertThat(
            DecisionSubjectProjectionWriter.class
                .getMethod("delete", UUID.class, UUID.class)
                .getAnnotation(Transactional.class))
        .isNotNull();
    Transactional snapshot =
        DecisionSubjectProjectionWriter.class
            .getMethod("caseIdsSnapshot", UUID.class, int.class)
            .getAnnotation(Transactional.class);
    assertThat(snapshot).isNotNull();
    assertThat(snapshot.readOnly()).isTrue();
    assertThat(snapshot.isolation())
        .isEqualTo(org.springframework.transaction.annotation.Isolation.REPEATABLE_READ);
  }

  @Test
  void failedTaskAssignmentFollowIsRetriedAndEventuallyDelivered() {
    Cover cover = governed(1);
    var follower = user("Retry follower", "sales:read", "flowboard:read");
    UUID assignment = UUID.randomUUID();
    doThrow(new TransientDataAccessResourceException("retry once"))
        .doCallRealMethod()
        .when(follows)
        .record(
            tenant,
            cover.caseId(),
            follower.getId(),
            com.fabricmanagement.flowboard.decision.domain.DecisionFollowSource.ASSIGNED,
            assignment);

    followListener.onTaskAssigned(
        new TaskAssignedEvent(tenant, cover.taskId(), assignment, follower.getId(), actor.getId()));

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              verify(follows, times(2))
                  .record(
                      tenant,
                      cover.caseId(),
                      follower.getId(),
                      com.fabricmanagement.flowboard.decision.domain.DecisionFollowSource.ASSIGNED,
                      assignment);
              assertThat(follows.isEffective(tenant, cover.caseId(), follower.getId())).isTrue();
            });
  }

  @Test
  void failedProjectionDeliveryLeavesTheRowUnchangedAndMakesSummaryStaleUntilRedelivery() {
    Cover cover = governed(1);
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () ->
                assertThat(projections.findByTenantIdAndCaseId(tenant, cover.caseId()))
                    .isPresent());
    var before = projections.findByTenantIdAndCaseId(tenant, cover.caseId()).orElseThrow();
    var projectedAt = before.getProjectedAt();
    doThrow(new TransientDataAccessResourceException("injected projection failure"))
        .when(writerTarget())
        .apply(argThat(facts -> facts.caseId().equals(cover.caseId())), any());

    refresh(cover);
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () ->
                assertThat(
                        jdbc.queryForObject(
                            """
                            select count(*) from event_publication
                            where listener_id like '%DecisionProjectionListener.%'
                              and serialized_event like ? and completion_date is null
                            """,
                            Integer.class, "%" + cover.caseId() + "%"))
                    .isOne());
    assertThat(
            projections
                .findByTenantIdAndCaseId(tenant, cover.caseId())
                .orElseThrow()
                .getProjectedAt())
        .isEqualTo(projectedAt);
    jdbc.update(
        """
        update event_publication set publication_date=now() - interval '31 seconds'
        where listener_id like '%DecisionProjectionListener.%'
          and serialized_event like ? and completion_date is null
        """,
        "%" + cover.caseId() + "%");
    assertThat(queue.summary(tenant, actor.getId()).stale()).isTrue();

    doCallRealMethod().when(writerTarget()).apply(any(Facts.class), any());
    incompletePublications.resubmitIncompletePublicationsOlderThan(Duration.ZERO);

    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              assertThat(
                      projections
                          .findByTenantIdAndCaseId(tenant, cover.caseId())
                          .orElseThrow()
                          .getEvidenceRevision())
                  .isEqualTo(1L);
              assertThat(queue.summary(tenant, actor.getId()).stale()).isFalse();
            });
  }

  private Facts withTask(Facts value, UUID taskId) {
    return new Facts(
        value.tenantId(),
        value.caseId(),
        value.orderId(),
        value.orderNumber(),
        value.orderCreatedBy(),
        taskId,
        value.caseState(),
        value.caseRevision(),
        value.unresolvedLineCount(),
        value.openedAt(),
        value.closedAt(),
        value.evidenceRevision(),
        value.verdictCode());
  }

  private DecisionSubjectProjectionWriter writerTarget() {
    return AopTestUtils.getUltimateTargetObject(writer);
  }

  private OrderCoverProjectionService sourceTarget() {
    return AopTestUtils.getUltimateTargetObject(source);
  }

  private Facts withRevisions(Facts value, long caseRevision, Long evidenceRevision) {
    return new Facts(
        value.tenantId(),
        value.caseId(),
        value.orderId(),
        value.orderNumber(),
        value.orderCreatedBy(),
        value.taskId(),
        value.caseState(),
        caseRevision,
        value.unresolvedLineCount(),
        value.openedAt(),
        value.closedAt(),
        evidenceRevision,
        value.verdictCode());
  }
}
