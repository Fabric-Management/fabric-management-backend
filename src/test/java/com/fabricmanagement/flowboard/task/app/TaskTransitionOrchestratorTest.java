package com.fabricmanagement.flowboard.task.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.task.domain.DomainTaskActionResult;
import com.fabricmanagement.flowboard.task.domain.ModuleType;
import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskActionCommand;
import com.fabricmanagement.flowboard.task.domain.TaskTransitionAttempt;
import com.fabricmanagement.flowboard.task.domain.TaskTransitionOutcome;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskTransitionAttemptClaimRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskTransitionAttemptRepository;
import jakarta.persistence.OptimisticLockException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class TaskTransitionOrchestratorTest {

  private static final UUID TENANT_ID = UUID.fromString("7db56bc5-c7e4-4e64-8533-c8aa1b541830");
  private static final UUID ACTOR_ID = UUID.fromString("c263997a-4982-4bd4-b27d-fb592d43718a");
  private static final String FINGERPRINT = "a".repeat(64);

  @Mock private TaskRepository taskRepository;
  @Mock private TaskTransitionAttemptRepository attemptRepository;
  @Mock private TaskTransitionAttemptClaimRepository attemptClaimRepository;
  @Mock private TaskAffectedSubjectService affectedSubjectService;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentUserId(ACTOR_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void replaysCompletedAttemptBeforeCheckingTheNowStaleVersion() {
    TaskActionCommand command = command(2);
    TaskTransitionAttempt attempt = TaskTransitionAttempt.claim(command);
    attempt.completeAccepted("ORDER_COVER_RESULT", UUID.randomUUID(), Instant.now());
    when(attemptRepository.findByTenantIdAndActorIdAndIdempotencyKey(
            TENANT_ID, ACTOR_ID, command.idempotencyKey()))
        .thenReturn(Optional.of(attempt));

    var result = orchestrator(List.of()).execute(command);

    assertThat(result.outcome()).isEqualTo(TaskTransitionOutcome.ACCEPTED);
    assertThat(result.replayed()).isTrue();
    verify(taskRepository, never()).findById(any());
  }

  @Test
  void rejectsGenuinelyStaleCommandAfterReplayMiss() {
    Task task = governedTask(3L);
    TaskActionCommand command = command(2);
    stubWinningClaim(command);
    when(taskRepository.findById(command.taskId())).thenReturn(Optional.of(task));

    assertThatThrownBy(() -> orchestrator(List.of()).execute(command))
        .isInstanceOf(OptimisticLockException.class)
        .hasMessageContaining("expected=2 actual=3");
  }

  @Test
  void persistsBusinessRejectionWithoutSavingTaskEffects() {
    Task task = governedTask(2L);
    TaskActionCommand command = command(2);
    DomainTaskAction rejectingAction =
        new DomainTaskAction() {
          public String actionKey() {
            return "SETTLE_ORDER_COVER";
          }

          public boolean supports(Task candidate) {
            return true;
          }

          public DomainTaskActionResult execute(Task candidate, TaskActionCommand ignored) {
            return new DomainTaskActionResult.Rejected("STOCK_UNKNOWN", "Stock truth is unknown");
          }
        };
    stubWinningClaim(command);
    when(taskRepository.findById(command.taskId())).thenReturn(Optional.of(task));
    when(attemptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var result = orchestrator(List.of(rejectingAction)).execute(command);

    assertThat(result.outcome()).isEqualTo(TaskTransitionOutcome.REJECTED_BUSINESS);
    assertThat(result.rejectionCode()).isEqualTo("STOCK_UNKNOWN");
    verify(taskRepository, never()).save(any());
  }

  @Test
  void acceptedFinalResultSynchronizesScopeAndClosesTaskAtomically() {
    Task task = governedTask(2L);
    TaskActionCommand command = command(2);
    UUID resultId = UUID.randomUUID();
    DomainTaskAction acceptingAction =
        new DomainTaskAction() {
          public String actionKey() {
            return "SETTLE_ORDER_COVER";
          }

          public boolean supports(Task candidate) {
            return true;
          }

          public DomainTaskActionResult execute(Task candidate, TaskActionCommand ignored) {
            return new DomainTaskActionResult.Accepted("ORDER_COVER_RESULT", resultId);
          }
        };
    stubWinningClaim(command);
    when(taskRepository.findById(command.taskId())).thenReturn(Optional.of(task));
    when(attemptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var result = orchestrator(List.of(acceptingAction)).execute(command);

    assertThat(result.outcome()).isEqualTo(TaskTransitionOutcome.ACCEPTED);
    assertThat(task.getClosedAt()).isEqualTo(Instant.parse("2026-09-15T12:00:00Z"));
    assertThat(task.getStatus())
        .isEqualTo(com.fabricmanagement.flowboard.task.domain.TaskStatus.DONE);
    verify(affectedSubjectService).synchronize(task.getId(), java.util.Set.of());
    verify(taskRepository).save(task);
  }

  @Test
  void refusesActorIdWhenNoAuthenticatedExecutionActorIsBound() {
    TenantContext.setCurrentUserId(null);

    assertThatThrownBy(() -> orchestrator(List.of()).execute(command(0)))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("execution context");
    verify(attemptClaimRepository, never()).tryClaim(any(), any());
  }

  private TaskTransitionOrchestrator orchestrator(List<DomainTaskAction> actions) {
    @SuppressWarnings("unchecked")
    ObjectProvider<DomainTaskAction> provider = org.mockito.Mockito.mock(ObjectProvider.class);
    when(provider.orderedStream()).thenReturn(actions.stream());
    return new TaskTransitionOrchestrator(
        taskRepository,
        attemptRepository,
        attemptClaimRepository,
        new TaskWorkflowRegistry(),
        affectedSubjectService,
        provider,
        Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC));
  }

  private void stubWinningClaim(TaskActionCommand command) {
    TaskTransitionAttempt pending = TaskTransitionAttempt.claim(command);
    when(attemptRepository.findByTenantIdAndActorIdAndIdempotencyKey(any(), any(), any()))
        .thenReturn(Optional.empty(), Optional.of(pending));
    when(attemptClaimRepository.tryClaim(TENANT_ID, command)).thenReturn(true);
  }

  private static TaskActionCommand command(long version) {
    return new TaskActionCommand(
        UUID.fromString("9e43d414-e990-4cab-8779-f272f7e9b532"),
        ACTOR_ID,
        "request-123",
        "SETTLE_ORDER_COVER",
        FINGERPRINT,
        version);
  }

  private static Task governedTask(long version) {
    Task task =
        Task.create(
            "TSK-0001",
            UUID.randomUUID(),
            "Review order cover",
            TaskType.ORDER_COVER,
            ModuleType.GENERAL,
            Priority.HIGH,
            null,
            null,
            "SALES_ORDER",
            UUID.randomUUID());
    task.setId(command(version).taskId());
    task.setVersion(version);
    var workflow = new TaskWorkflowRegistry().latestFor(TaskType.ORDER_COVER);
    task.govern("task:key", workflow.definitionId(), workflow.version());
    return task;
  }
}
