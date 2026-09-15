package com.fabricmanagement.flowboard.task.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.task.app.TaskGenerationKey;
import com.fabricmanagement.flowboard.task.app.TaskProvisioningService;
import com.fabricmanagement.flowboard.task.domain.ModuleType;
import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskCreation;
import com.fabricmanagement.flowboard.task.domain.TaskSubject;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.fabricmanagement.flowboard.task.domain.event.TaskCreatedEvent;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class TaskProvisioningRaceIT extends AbstractIntegrationTest {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private TaskProvisioningService provisioningService;
  @MockitoSpyBean private DomainEventPublisher eventPublisher;

  @Test
  void concurrentServiceCallsReturnOneActiveExecution() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    UUID boardId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    UUID lineId = UUID.randomUUID();
    String generationKey =
        TaskGenerationKey.subject("SALES_ORDER", orderId, TaskType.ORDER_COVER, null);
    seedBoard(tenantId, boardId);
    clearInvocations(eventPublisher);

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () ->
                  provision(
                      tenantId,
                      actorId,
                      command(boardId, orderId, lineId, generationKey),
                      ready,
                      start));
      var second =
          executor.submit(
              () ->
                  provision(
                      tenantId,
                      actorId,
                      command(boardId, orderId, lineId, generationKey),
                      ready,
                      start));
      ready.await();
      start.countDown();

      assertThat(first.get().getId()).isEqualTo(second.get().getId());
    }

    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM flowboard.task
                WHERE tenant_id = ? AND generation_key = ?
                  AND is_active = TRUE AND closed_at IS NULL
                """,
                Integer.class,
                tenantId,
                generationKey))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM flowboard.task_affected_subject
                WHERE tenant_id = ? AND subject_id = ? AND is_active = TRUE
                """,
                Integer.class,
                tenantId,
                lineId))
        .isEqualTo(1);
    verify(eventPublisher, times(1)).publish(isA(TaskCreatedEvent.class));
  }

  private Task provision(
      UUID tenantId, UUID actorId, TaskCreation command, CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    ready.countDown();
    start.await();
    try {
      TenantContext.setCurrentTenantId(tenantId);
      TenantContext.setCurrentTenantUid("RACE-001");
      TenantContext.setCurrentUserId(actorId);
      return new TransactionTemplate(transactionManager)
          .execute(ignored -> provisioningService.createOrSynchronizeActive(command));
    } finally {
      TenantContext.clear();
    }
  }

  private static TaskCreation command(
      UUID boardId, UUID orderId, UUID lineId, String generationKey) {
    return new TaskCreation(
        boardId,
        "Review order cover",
        null,
        TaskType.ORDER_COVER,
        ModuleType.GENERAL,
        Priority.HIGH,
        null,
        null,
        "SALES_ORDER",
        orderId,
        "TEMPLATE",
        UUID.randomUUID(),
        generationKey,
        Set.of(new TaskSubject("SALES_ORDER_LINE", lineId)));
  }

  private void seedBoard(UUID tenantId, UUID boardId) {
    jdbc.update(
        """
        INSERT INTO flowboard.board (
            id, tenant_id, uid, name, board_type, wip_limit_default, default_view_type,
            is_active, created_at, updated_at, version
        ) VALUES (?, ?, ?, 'Race board', 'GLOBAL', 5, 'KANBAN', TRUE, NOW(), NOW(), 0)
        """,
        boardId,
        tenantId,
        "race-board-" + boardId);
  }
}
