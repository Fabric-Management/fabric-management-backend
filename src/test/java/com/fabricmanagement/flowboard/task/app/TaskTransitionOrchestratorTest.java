package com.fabricmanagement.flowboard.task.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException;
import com.fabricmanagement.flowboard.task.domain.DomainTaskActionResult;
import com.fabricmanagement.flowboard.task.domain.ModuleType;
import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskActionCommand;
import com.fabricmanagement.flowboard.task.domain.TaskSubject;
import com.fabricmanagement.flowboard.task.domain.TaskTransitionAttempt;
import com.fabricmanagement.flowboard.task.domain.TaskTransitionOutcome;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskTransitionAttemptClaimRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskTransitionAttemptRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskTransitionTransactionGuard;
import com.fabricmanagement.flowboard.task.infra.repository.TaskVersionLockRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
  @Mock private TaskVersionLockRepository taskVersionLockRepository;
  @Mock private TaskTransitionAttemptRepository attemptRepository;
  @Mock private TaskTransitionAttemptClaimRepository attemptClaimRepository;
  @Mock private TaskAffectedSubjectService affectedSubjectService;
  @Mock private TaskTransitionTransactionGuard transactionGuard;

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
    when(taskRepository.findById(command.taskId())).thenReturn(Optional.of(governedTask(4L)));
    when(affectedSubjectService.current(command.taskId())).thenReturn(Set.of());

    var result = orchestrator(List.of()).execute(command);

    assertThat(result.outcome()).isEqualTo(TaskTransitionOutcome.ACCEPTED);
    assertThat(result.replayed()).isTrue();
    assertThat(result.taskVersion()).isEqualTo(4L);
    verify(taskRepository).findById(command.taskId());
    verify(taskVersionLockRepository, never()).forceIncrementNow(any());
    var ordered = org.mockito.Mockito.inOrder(transactionGuard, attemptRepository);
    ordered.verify(transactionGuard).setBoundedLockTimeout();
    ordered
        .verify(attemptRepository)
        .findByTenantIdAndActorIdAndIdempotencyKey(TENANT_ID, ACTOR_ID, command.idempotencyKey());
  }

  @Test
  void sameIdempotencyKeyWithDifferentPayloadFingerprintIsAConflict() {
    TaskActionCommand original = command(2);
    TaskTransitionAttempt attempt = TaskTransitionAttempt.claim(original);
    attempt.completeAccepted("ORDER_COVER_RESULT", UUID.randomUUID(), Instant.now());
    TaskActionCommand changed =
        new TaskActionCommand(
            original.taskId(),
            original.actorId(),
            original.idempotencyKey(),
            original.actionKey(),
            "b".repeat(64),
            original.expectedVersion());
    when(attemptRepository.findByTenantIdAndActorIdAndIdempotencyKey(
            TENANT_ID, ACTOR_ID, original.idempotencyKey()))
        .thenReturn(Optional.of(attempt));

    assertThatThrownBy(() -> orchestrator(List.of()).execute(changed))
        .isInstanceOfSatisfying(
            FlowBoardDomainException.class,
            failure -> assertThat(failure.getErrorCode()).isEqualTo("IDEMPOTENCY_CONFLICT"));
    verify(taskRepository, never()).findById(any());
  }

  @Test
  void rejectsGenuinelyStaleCommandAfterReplayMiss() {
    Task task = governedTask(3L);
    TaskActionCommand command = command(2);
    stubWinningClaim(command);
    when(taskRepository.findById(command.taskId())).thenReturn(Optional.of(task));

    assertThatThrownBy(() -> orchestrator(List.of()).execute(command))
        .isInstanceOfSatisfying(
            FlowBoardDomainException.class,
            failure -> assertThat(failure.getErrorCode()).isEqualTo("TASK_VERSION_CONFLICT"))
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
    verify(taskVersionLockRepository, never()).forceIncrementNow(any());
  }

  @Test
  void concurrentVersionChangeDuringTheWriteStaysRetryableAndRecordsNoAttempt() {
    Task task = governedTask(2L);
    TaskActionCommand command = command(2);
    Set<TaskSubject> remainingSubjects = Set.of(new TaskSubject("TEST_SCOPE", UUID.randomUUID()));
    DomainTaskAction acceptingAction =
        new DomainTaskAction() {
          public String actionKey() {
            return "SETTLE_ORDER_COVER";
          }

          public boolean supports(Task candidate) {
            return true;
          }

          public DomainTaskActionResult execute(Task candidate, TaskActionCommand ignored) {
            return new DomainTaskActionResult.Accepted(
                "ORDER_COVER_RESULT", UUID.randomUUID(), remainingSubjects);
          }
        };
    stubWinningClaim(command);
    when(taskRepository.findById(command.taskId())).thenReturn(Optional.of(task));
    when(taskVersionLockRepository.forceIncrementNow(task))
        .thenThrow(
            new org.springframework.orm.ObjectOptimisticLockingFailureException(
                Task.class, command.taskId()));

    // A lost race must reach TaskTransitionExecutor as an optimistic-lock failure so the whole
    // transaction can be retried; typing it here would make a transient conflict look terminal.
    assertThatThrownBy(() -> orchestrator(List.of(acceptingAction)).execute(command))
        .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
    verify(attemptRepository, never()).save(any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void acceptedResultConsumesVersionAndClosesOnlyWhenNoSubjectsRemain(boolean partial) {
    Task task = governedTask(2L);
    TaskActionCommand command = command(2);
    UUID resultId = UUID.randomUUID();
    Set<TaskSubject> remainingSubjects =
        partial ? Set.of(new TaskSubject("TEST_SCOPE", UUID.randomUUID())) : Set.of();
    DomainTaskAction acceptingAction =
        new DomainTaskAction() {
          public String actionKey() {
            return "SETTLE_ORDER_COVER";
          }

          public boolean supports(Task candidate) {
            return true;
          }

          public DomainTaskActionResult execute(Task candidate, TaskActionCommand ignored) {
            return new DomainTaskActionResult.Accepted(
                "ORDER_COVER_RESULT", resultId, remainingSubjects);
          }
        };
    stubWinningClaim(command);
    when(taskRepository.findById(command.taskId())).thenReturn(Optional.of(task));
    when(attemptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    if (partial) {
      when(taskVersionLockRepository.forceIncrementNow(task))
          .thenAnswer(
              invocation -> {
                task.setVersion(3L);
                return 3L;
              });
    } else {
      when(taskRepository.saveAndFlush(task))
          .thenAnswer(
              invocation -> {
                task.setVersion(3L);
                return task;
              });
    }

    var result = orchestrator(List.of(acceptingAction)).execute(command);

    assertThat(result.outcome()).isEqualTo(TaskTransitionOutcome.ACCEPTED);
    assertThat(result.taskVersion()).isEqualTo(3L);
    if (partial) {
      assertThat(task.getClosedAt()).isNull();
      assertThat(task.getStatus())
          .isEqualTo(com.fabricmanagement.flowboard.task.domain.TaskStatus.BACKLOG);
    } else {
      assertThat(task.getClosedAt()).isEqualTo(Instant.parse("2026-09-15T12:00:00Z"));
      assertThat(task.getStatus())
          .isEqualTo(com.fabricmanagement.flowboard.task.domain.TaskStatus.DONE);
    }
    if (partial) {
      verify(taskVersionLockRepository).forceIncrementNow(task);
      verify(taskRepository, never()).saveAndFlush(any());
    } else {
      verify(taskVersionLockRepository, never()).forceIncrementNow(any());
      verify(taskRepository).saveAndFlush(task);
    }
    verify(affectedSubjectService).synchronize(task.getId(), remainingSubjects);
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
        taskVersionLockRepository,
        attemptRepository,
        attemptClaimRepository,
        new TaskWorkflowRegistry(),
        affectedSubjectService,
        provider,
        Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC),
        transactionGuard);
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
