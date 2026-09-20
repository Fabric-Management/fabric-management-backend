package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException;
import com.fabricmanagement.flowboard.task.domain.DomainTaskActionResult;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskActionCommand;
import com.fabricmanagement.flowboard.task.domain.TaskTransitionAttempt;
import com.fabricmanagement.flowboard.task.dto.TaskTransitionResult;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskTransitionAttemptClaimRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskTransitionAttemptRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskTransitionTransactionGuard;
import com.fabricmanagement.flowboard.task.infra.repository.TaskVersionLockRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replay-first transaction boundary for typed domain Task actions.
 *
 * <p>The native idempotency claim and the domain action share this transaction. PostgreSQL waits on
 * an uncommitted conflicting claim, so concurrent calls with the same actor and idempotency key are
 * serialized until the winner commits or rolls back. This is the intentional SYSTEM_SAME_TX
 * profile. The transaction guard sets a bounded lock timeout before the claim, and the outer
 * executor translates timeout and retryable database failures after rollback.
 *
 * <p>A committed PENDING claim is not expected in this profile: technical failures roll the claim
 * back. A future split-transaction system actor must define stale-PENDING recovery before it uses
 * this boundary.
 */
@Service
public class TaskTransitionOrchestrator {

  private final TaskRepository taskRepository;
  private final TaskVersionLockRepository taskVersionLockRepository;
  private final TaskTransitionAttemptRepository attemptRepository;
  private final TaskTransitionAttemptClaimRepository attemptClaimRepository;
  private final TaskWorkflowRegistry workflowRegistry;
  private final TaskAffectedSubjectService affectedSubjectService;
  private final Map<String, DomainTaskAction> actions;
  private final Clock clock;
  private final TaskTransitionTransactionGuard transactionGuard;

  public TaskTransitionOrchestrator(
      TaskRepository taskRepository,
      TaskVersionLockRepository taskVersionLockRepository,
      TaskTransitionAttemptRepository attemptRepository,
      TaskTransitionAttemptClaimRepository attemptClaimRepository,
      TaskWorkflowRegistry workflowRegistry,
      TaskAffectedSubjectService affectedSubjectService,
      ObjectProvider<DomainTaskAction> actionProvider,
      Clock clock,
      TaskTransitionTransactionGuard transactionGuard) {
    this.taskRepository = taskRepository;
    this.taskVersionLockRepository = taskVersionLockRepository;
    this.attemptRepository = attemptRepository;
    this.attemptClaimRepository = attemptClaimRepository;
    this.workflowRegistry = workflowRegistry;
    this.affectedSubjectService = affectedSubjectService;
    this.actions =
        actionProvider
            .orderedStream()
            .collect(
                Collectors.toUnmodifiableMap(
                    DomainTaskAction::actionKey,
                    Function.identity(),
                    (left, right) -> {
                      throw new IllegalStateException("Duplicate Task action: " + left.actionKey());
                    }));
    this.clock = clock;
    this.transactionGuard =
        java.util.Objects.requireNonNull(
            transactionGuard, "The bounded lock timeout guard is not optional");
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public TaskTransitionResult execute(TaskActionCommand command) {
    // This must stay before every persistence access, including replay and idempotency claim.
    transactionGuard.setBoundedLockTimeout();
    assertTrustedActor(command);
    UUID tenantId = TenantContext.requireTenantId();
    var replay =
        attemptRepository.findByTenantIdAndActorIdAndIdempotencyKey(
            tenantId, command.actorId(), command.idempotencyKey());
    if (replay.isPresent()) {
      assertSameCommand(replay.get(), command);
      return response(replay.get(), true);
    }

    if (!attemptClaimRepository.tryClaim(tenantId, command)) {
      TaskTransitionAttempt winner =
          attemptRepository
              .findByTenantIdAndActorIdAndIdempotencyKey(
                  tenantId, command.actorId(), command.idempotencyKey())
              .orElseThrow(
                  () -> new IllegalStateException("Claimed Task transition result is unavailable"));
      assertSameCommand(winner, command);
      return response(winner, true);
    }
    TaskTransitionAttempt attempt =
        attemptRepository
            .findByTenantIdAndActorIdAndIdempotencyKey(
                tenantId, command.actorId(), command.idempotencyKey())
            .orElseThrow(
                () -> new IllegalStateException("Task transition claim was not persisted"));

    Task task =
        taskRepository
            .findById(command.taskId())
            .orElseThrow(() -> new EntityNotFoundException("Task not found: " + command.taskId()));
    if (!Long.valueOf(command.expectedVersion()).equals(task.getVersion())) {
      throw new FlowBoardDomainException(
          "Task version changed: expected=%d actual=%d"
              .formatted(command.expectedVersion(), task.getVersion()),
          "TASK_VERSION_CONFLICT",
          409);
    }
    workflowRegistry.resolve(task.getWorkflowDefinitionId(), task.getWorkflowVersion());
    DomainTaskAction action = actions.get(command.actionKey());
    if (action == null || !action.supports(task)) {
      throw new FlowBoardDomainException(
          "Task action is not supported by the pinned workflow",
          "FLOWBOARD_TASK_ACTION_UNSUPPORTED",
          409,
          new Object[] {command.actionKey()});
    }

    DomainTaskActionResult domainResult = action.execute(task, command);
    Instant completedAt = Instant.now(clock);
    switch (domainResult) {
      case DomainTaskActionResult.Accepted accepted -> {
        // A partial action can change only affected-subject rows. It still consumes the
        // task version, so a competing recipient cannot commit against the same version.
        affectedSubjectService.synchronize(task.getId(), accepted.remainingSubjects());
        // A conflict here is a lost race, not a stale client expectation: the version was correct
        // when checked above. The optimistic-lock failure propagates unwrapped so the retry
        // boundary in TaskTransitionExecutor can re-run the whole transaction; only once its
        // attempts are exhausted does it become the typed TASK_VERSION_CONFLICT the early check
        // returns immediately. Either way the claim rolls back and no attempt is recorded.
        if (accepted.remainingSubjects().isEmpty()) {
          task.closeGovernedExecution(completedAt);
          taskRepository.saveAndFlush(task);
        } else {
          taskVersionLockRepository.forceIncrementNow(task);
        }
        attempt.completeAccepted(accepted.resultType(), accepted.resultId(), completedAt);
      }
      case DomainTaskActionResult.Rejected rejected ->
          attempt.completeRejected(rejected.code(), rejected.message(), completedAt);
    }
    return response(attemptRepository.save(attempt), false);
  }

  private static void assertTrustedActor(TaskActionCommand command) {
    UUID boundActor = TenantContext.getCurrentUserId();
    if (boundActor == null || !boundActor.equals(command.actorId())) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Task action actor must come from the authenticated execution context");
    }
  }

  private static void assertSameCommand(TaskTransitionAttempt attempt, TaskActionCommand command) {
    if (!attempt.getTaskId().equals(command.taskId())
        || !attempt.getActionKey().equals(command.actionKey())
        || !attempt.getPayloadFingerprint().equals(command.payloadFingerprint())) {
      throw new FlowBoardDomainException(
          "Idempotency key was already used for a different Task command",
          "IDEMPOTENCY_CONFLICT",
          409,
          new Object[] {command.idempotencyKey()});
    }
  }

  private TaskTransitionResult response(TaskTransitionAttempt attempt, boolean replayed) {
    if (attempt.getOutcome()
        == com.fabricmanagement.flowboard.task.domain.TaskTransitionOutcome.PENDING) {
      throw new IllegalStateException("A pending Task transition cannot be returned as a replay");
    }
    // Task fields describe the task as it is now (§4.2). The stored receipt does not depend on
    // them: if the task row no longer exists, a replay still returns the receipt (§3.3).
    Task currentTask = taskRepository.findById(attempt.getTaskId()).orElse(null);
    if (currentTask == null) {
      return new TaskTransitionResult(
          attempt.getOutcome(),
          attempt.getResultType(),
          attempt.getResultId(),
          attempt.getRejectionCode(),
          attempt.getRejectionMessage(),
          replayed,
          attempt.getTaskId(),
          0L,
          null,
          java.util.Set.of());
    }
    java.util.Set<com.fabricmanagement.flowboard.task.domain.TaskSubject> remaining =
        affectedSubjectService.current(currentTask.getId());
    return new TaskTransitionResult(
        attempt.getOutcome(),
        attempt.getResultType(),
        attempt.getResultId(),
        attempt.getRejectionCode(),
        attempt.getRejectionMessage(),
        replayed,
        currentTask.getId(),
        currentTask.getVersion(),
        currentTask.getStatus(),
        remaining);
  }
}
