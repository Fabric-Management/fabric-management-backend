package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.flowboard.task.domain.TaskActionCommand;
import com.fabricmanagement.flowboard.task.dto.TaskTransitionResult;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/** Bounded retry boundary outside the transaction opened by the orchestrator proxy. */
@Component
@RequiredArgsConstructor
public class TaskTransitionExecutor {
  private final TaskTransitionOrchestrator orchestrator;

  public TaskTransitionResult execute(TaskActionCommand command) {
    for (int attempt = 1; ; attempt++) {
      try {
        return orchestrator.execute(command);
      } catch (RuntimeException failure) {
        if (lockTimeout(failure))
          throw new com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException(
              "The decision is busy; retry with the same idempotency key", "LOCK_TIMEOUT", 409);
        if (attempt >= 3 || !retryable(failure)) throw exhausted(failure);
      }
    }
  }

  private static boolean lockTimeout(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause())
      if (cause instanceof SQLException sql && "55P03".equals(sql.getSQLState())) return true;
    return false;
  }

  /**
   * A version conflict that survives the retries is reported with the same typed 409 the
   * orchestrator's early check raises, so a caller cannot tell the two apart by error code.
   */
  private static RuntimeException exhausted(RuntimeException failure) {
    if (versionConflict(failure))
      return new com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException(
          "Task version changed while the transition was being applied",
          "TASK_VERSION_CONFLICT",
          409);
    return failure;
  }

  private static boolean versionConflict(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause())
      if (cause instanceof ObjectOptimisticLockingFailureException
          || cause instanceof jakarta.persistence.OptimisticLockException
          || cause instanceof org.hibernate.StaleObjectStateException) return true;
    return false;
  }

  private static boolean retryable(Throwable failure) {
    if (failure instanceof ObjectOptimisticLockingFailureException
        || failure instanceof TransientDataAccessException) return true;
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql
          && ("40001".equals(sql.getSQLState()) || "40P01".equals(sql.getSQLState()))) return true;
    }
    return false;
  }
}
