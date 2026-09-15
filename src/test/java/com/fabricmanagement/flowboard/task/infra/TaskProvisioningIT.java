package com.fabricmanagement.flowboard.task.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.task.app.TaskGenerationKey;
import com.fabricmanagement.flowboard.task.app.TaskProvisioningService;
import com.fabricmanagement.flowboard.task.app.TaskService;
import com.fabricmanagement.flowboard.task.app.TaskWorkflowRegistry;
import com.fabricmanagement.flowboard.task.domain.AssignedBy;
import com.fabricmanagement.flowboard.task.domain.ModuleType;
import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskActionCommand;
import com.fabricmanagement.flowboard.task.domain.TaskCreation;
import com.fabricmanagement.flowboard.task.domain.TaskTransitionAttempt;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskTransitionAttemptClaimRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskTransitionAttemptRepository;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class TaskProvisioningIT extends AbstractIntegrationTest {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TaskProvisioningService provisioningService;
  @Autowired private TaskService taskService;
  @Autowired private TaskTransitionAttemptClaimRepository attemptClaimRepository;
  @Autowired private TaskTransitionAttemptRepository attemptRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void nativeTaskUsesTheSameUidAndAuditorSemanticsAsJpa() {
    UUID tenantId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    UUID boardId = UUID.randomUUID();
    seedBoard(tenantId, boardId);

    try {
      TenantContext.setCurrentTenantId(tenantId);
      TenantContext.setCurrentTenantUid("AUDIT-001");
      TenantContext.setCurrentUserId(actorId);
      TransactionTemplate transaction = new TransactionTemplate(transactionManager);
      Task jpaTask = transaction.execute(ignored -> taskRepository.saveAndFlush(jpaTask(boardId)));
      Task nativeTask =
          transaction.execute(
              ignored -> provisioningService.createOrSynchronizeActive(nativeCommand(boardId)));

      assertThat(jpaTask).isNotNull();
      assertThat(nativeTask).isNotNull();
      assertThat(nativeTask.getTenantId()).isEqualTo(tenantId);
      assertThat(nativeTask.getUid()).startsWith("AUDIT-001-TSK-");
      assertThat(jpaTask.getUid()).startsWith("AUDIT-001-TSK-");
      assertThat(nativeTask.getCreatedBy()).isEqualTo(jpaTask.getCreatedBy()).isEqualTo(actorId);
      assertThat(nativeTask.getUpdatedBy()).isEqualTo(jpaTask.getUpdatedBy()).isEqualTo(actorId);
      assertThat(nativeTask.getCreatedAt()).isNotNull();
      assertThat(nativeTask.getUpdatedAt()).isNotNull();
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void nativeSystemTaskUsesTheJpaAuditorFallback() {
    UUID tenantId = UUID.randomUUID();
    UUID boardId = UUID.randomUUID();
    seedBoard(tenantId, boardId);

    try {
      TenantContext.setCurrentTenantId(tenantId);
      TenantContext.setCurrentTenantUid("SYSTEM-001");
      TenantContext.setCurrentUserId(null);
      TransactionTemplate transaction = new TransactionTemplate(transactionManager);
      Task jpaTask = transaction.execute(ignored -> taskRepository.saveAndFlush(jpaTask(boardId)));
      Task nativeTask =
          transaction.execute(
              ignored -> provisioningService.createOrSynchronizeActive(nativeCommand(boardId)));

      assertThat(jpaTask).isNotNull();
      assertThat(nativeTask).isNotNull();
      assertThat(nativeTask.getCreatedBy())
          .isEqualTo(jpaTask.getCreatedBy())
          .isEqualTo(SystemUser.ID);
      assertThat(nativeTask.getUpdatedBy())
          .isEqualTo(jpaTask.getUpdatedBy())
          .isEqualTo(SystemUser.ID);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void nativeTransitionClaimUsesTheSameUidAndAuditorSemanticsAsJpa() {
    UUID tenantId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    UUID boardId = UUID.randomUUID();
    seedBoard(tenantId, boardId);

    try {
      TenantContext.setCurrentTenantId(tenantId);
      TenantContext.setCurrentTenantUid("ATTEMPT-001");
      TenantContext.setCurrentUserId(actorId);
      Task task = provisioningService.createOrSynchronizeActive(nativeCommand(boardId));
      TaskActionCommand jpaCommand = transitionCommand(task, actorId, "jpa-attempt");
      TaskActionCommand nativeCommand = transitionCommand(task, actorId, "native-attempt");
      TransactionTemplate transaction = new TransactionTemplate(transactionManager);

      TaskTransitionAttempt jpaAttempt =
          transaction.execute(
              ignored -> {
                TaskTransitionAttempt attempt = TaskTransitionAttempt.claim(jpaCommand);
                attempt.completeRejected("TEST_REJECTION", "JPA reference", Instant.now());
                return attemptRepository.saveAndFlush(attempt);
              });
      TaskTransitionAttempt nativeAttempt =
          transaction.execute(
              ignored -> {
                assertThat(attemptClaimRepository.tryClaim(tenantId, nativeCommand)).isTrue();
                TaskTransitionAttempt attempt =
                    attemptRepository
                        .findByTenantIdAndActorIdAndIdempotencyKey(
                            tenantId, actorId, nativeCommand.idempotencyKey())
                        .orElseThrow();
                attempt.completeRejected("TEST_REJECTION", "Native reference", Instant.now());
                return attemptRepository.saveAndFlush(attempt);
              });

      assertThat(jpaAttempt).isNotNull();
      assertThat(nativeAttempt).isNotNull();
      assertThat(nativeAttempt.getUid()).startsWith("ATTEMPT-001-TATT-");
      assertThat(jpaAttempt.getUid()).startsWith("ATTEMPT-001-TATT-");
      assertThat(nativeAttempt.getCreatedBy())
          .isEqualTo(jpaAttempt.getCreatedBy())
          .isEqualTo(actorId);
      assertThat(nativeAttempt.getUpdatedBy())
          .isEqualTo(jpaAttempt.getUpdatedBy())
          .isEqualTo(actorId);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void sameGenerationKeyCreatesIndependentExecutionsForTwoTenants() {
    UUID tenantA = UUID.randomUUID();
    UUID tenantB = UUID.randomUUID();
    UUID boardA = UUID.randomUUID();
    UUID boardB = UUID.randomUUID();
    UUID requestId = UUID.randomUUID();
    String key = TaskGenerationKey.manualRequest(requestId);
    seedBoard(tenantA, boardA);
    seedBoard(tenantB, boardB);

    Task taskA = provisionForTenant(tenantA, boardA, key);
    Task taskB = provisionForTenant(tenantB, boardB, key);

    assertThat(taskA.getId()).isNotEqualTo(taskB.getId());
    assertThat(taskA.getTenantId()).isEqualTo(tenantA);
    assertThat(taskB.getTenantId()).isEqualTo(tenantB);
  }

  @Test
  void legacyCreationIdentitiesRemainIdempotentAndAutomationIsGroupedPerRule() {
    UUID tenantId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    UUID boardId = UUID.randomUUID();
    UUID subjectId = UUID.randomUUID();
    UUID firstRuleId = UUID.randomUUID();
    UUID secondRuleId = UUID.randomUUID();
    String manualKey = TaskGenerationKey.manualRequest(UUID.randomUUID());
    String recurringKey =
        TaskGenerationKey.recurring(
            UUID.randomUUID(), OffsetDateTime.parse("2026-09-15T10:00:00Z"));
    String firstAutomationKey =
        TaskGenerationKey.automation(firstRuleId, "SALES_ORDER", subjectId, TaskType.PLANNING);
    String secondAutomationKey =
        TaskGenerationKey.automation(secondRuleId, "SALES_ORDER", subjectId, TaskType.PLANNING);
    seedBoard(tenantId, boardId);

    try {
      TenantContext.setCurrentTenantId(tenantId);
      TenantContext.setCurrentTenantUid("LEGACY-001");
      TenantContext.setCurrentUserId(actorId);

      assertSameExecution(boardId, manualKey, "MANUAL", null);
      assertSameExecution(boardId, recurringKey, "TEMPLATE", UUID.randomUUID());
      Task firstAutomation =
          assertSameExecution(boardId, firstAutomationKey, "AUTOMATION_RULE", firstRuleId);
      Task secondAutomation =
          assertSameExecution(boardId, secondAutomationKey, "AUTOMATION_RULE", secondRuleId);

      assertThat(firstAutomation.getId()).isNotEqualTo(secondAutomation.getId());
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM flowboard.task WHERE tenant_id = ? AND board_id = ?",
                  Integer.class,
                  tenantId,
                  boardId))
          .isEqualTo(4);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void assignmentJoinsTheTaskVersionBoundaryAndSystemReplayDoesNotBumpItAgain() {
    UUID tenantId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    UUID assigneeId = UUID.randomUUID();
    UUID boardId = UUID.randomUUID();
    seedBoard(tenantId, boardId);

    try {
      TenantContext.setCurrentTenantId(tenantId);
      TenantContext.setCurrentTenantUid("ASSIGN-001");
      TenantContext.setCurrentUserId(actorId);
      Task task = provisioningService.createOrSynchronizeActive(nativeCommand(boardId));
      long beforeAssignment = task.getVersion();

      taskService.assignToUser(task.getId(), assigneeId, AssignedBy.SYSTEM, SystemUser.ID);
      long afterAssignment = taskVersion(task.getId());
      taskService.assignToUser(task.getId(), assigneeId, AssignedBy.SYSTEM, SystemUser.ID);

      assertThat(afterAssignment).isGreaterThan(beforeAssignment);
      assertThat(taskVersion(task.getId())).isEqualTo(afterAssignment);
      assertThat(
              jdbc.queryForObject(
                  """
                  SELECT count(*) FROM flowboard.task_assignee
                  WHERE task_id = ? AND user_id = ? AND is_active = TRUE
                  """,
                  Integer.class,
                  task.getId(),
                  assigneeId))
          .isEqualTo(1);
    } finally {
      TenantContext.clear();
    }
  }

  private long taskVersion(UUID taskId) {
    return jdbc.queryForObject(
        "SELECT version FROM flowboard.task WHERE id = ?", Long.class, taskId);
  }

  private static TaskActionCommand transitionCommand(
      Task task, UUID actorId, String idempotencyKey) {
    return new TaskActionCommand(
        task.getId(), actorId, idempotencyKey, "TEST_ACTION", "d".repeat(64), task.getVersion());
  }

  private Task assertSameExecution(
      UUID boardId, String generationKey, String sourceType, UUID sourceId) {
    TaskCreation command = nativeCommand(boardId, generationKey, sourceType, sourceId);
    Task first = provisioningService.createOrSynchronizeActive(command);
    Task replay = provisioningService.createOrSynchronizeActive(command);
    assertThat(replay.getId()).isEqualTo(first.getId());
    return first;
  }

  private Task provisionForTenant(UUID tenantId, UUID boardId, String key) {
    try {
      TenantContext.setCurrentTenantId(tenantId);
      TenantContext.setCurrentTenantUid("TENANT-" + tenantId.toString().substring(0, 4));
      TenantContext.setCurrentUserId(UUID.randomUUID());
      return provisioningService.createOrSynchronizeActive(nativeCommand(boardId, key));
    } finally {
      TenantContext.clear();
    }
  }

  private static Task jpaTask(UUID boardId) {
    Task task =
        Task.create(
            "TSK-JPA-" + UUID.randomUUID().toString().substring(0, 6),
            boardId,
            "JPA audit reference",
            TaskType.GENERAL,
            ModuleType.GENERAL,
            Priority.MEDIUM,
            null,
            null,
            null,
            null);
    task.govern(
        TaskGenerationKey.manualRequest(UUID.randomUUID()),
        TaskWorkflowRegistry.LEGACY_DEFINITION_ID,
        1);
    return task;
  }

  private static TaskCreation nativeCommand(UUID boardId) {
    return nativeCommand(boardId, TaskGenerationKey.manualRequest(UUID.randomUUID()));
  }

  private static TaskCreation nativeCommand(UUID boardId, String generationKey) {
    return nativeCommand(boardId, generationKey, "MANUAL", null);
  }

  private static TaskCreation nativeCommand(
      UUID boardId, String generationKey, String sourceType, UUID sourceId) {
    return new TaskCreation(
        boardId,
        "Native audit reference",
        null,
        TaskType.GENERAL,
        ModuleType.GENERAL,
        Priority.MEDIUM,
        null,
        null,
        null,
        null,
        sourceType,
        sourceId,
        generationKey,
        Set.of());
  }

  private void seedBoard(UUID tenantId, UUID boardId) {
    jdbc.update(
        """
        INSERT INTO flowboard.board (
            id, tenant_id, uid, name, board_type, wip_limit_default, default_view_type,
            is_active, created_at, updated_at, version
        ) VALUES (?, ?, ?, 'Audit board', 'GLOBAL', 5, 'KANBAN', TRUE, NOW(), NOW(), 0)
        """,
        boardId,
        tenantId,
        "audit-board-" + boardId);
  }
}
