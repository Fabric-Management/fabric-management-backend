package com.fabricmanagement.flowboard.task.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException;
import com.fabricmanagement.flowboard.task.domain.TaskActionCommand;
import com.fabricmanagement.flowboard.task.domain.TaskTransitionOutcome;
import com.fabricmanagement.flowboard.task.dto.TaskTransitionResult;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class TaskTransitionExecutorTest {
  private final TaskTransitionOrchestrator orchestrator = mock(TaskTransitionOrchestrator.class);
  private final TaskTransitionExecutor executor = new TaskTransitionExecutor(orchestrator);
  private final TaskActionCommand command =
      new TaskActionCommand(
          UUID.randomUUID(), UUID.randomUUID(), "key", "ACTION", "a".repeat(64), 1);

  @Test
  void retriesWholeTransactionalCallAtMostThreeTimes() {
    TaskTransitionResult accepted =
        new TaskTransitionResult(
            TaskTransitionOutcome.ACCEPTED, "RESULT", UUID.randomUUID(), null, null, false);
    when(orchestrator.execute(command))
        .thenThrow(new TransientDataAccessResourceException("serialization"))
        .thenThrow(new TransientDataAccessResourceException("deadlock"))
        .thenReturn(accepted);

    assertThat(executor.execute(command)).isSameAs(accepted);
    verify(orchestrator, times(3)).execute(command);
  }

  @Test
  void retriesALostVersionRaceAndSucceedsWhenTheRetryWins() {
    TaskTransitionResult accepted =
        new TaskTransitionResult(
            TaskTransitionOutcome.ACCEPTED, "RESULT", UUID.randomUUID(), null, null, false);
    when(orchestrator.execute(command))
        .thenThrow(new ObjectOptimisticLockingFailureException("task", UUID.randomUUID()))
        .thenReturn(accepted);

    assertThat(executor.execute(command)).isSameAs(accepted);
    verify(orchestrator, times(2)).execute(command);
  }

  @Test
  void aVersionRaceThatSurvivesEveryRetryBecomesTheSameTypedConflictAsTheEarlyCheck() {
    when(orchestrator.execute(command))
        .thenThrow(new ObjectOptimisticLockingFailureException("task", UUID.randomUUID()));

    assertThatThrownBy(() -> executor.execute(command))
        .isInstanceOfSatisfying(
            FlowBoardDomainException.class,
            failure -> {
              assertThat(failure.getErrorCode()).isEqualTo("TASK_VERSION_CONFLICT");
              assertThat(failure.getHttpStatus()).isEqualTo(409);
            });
    verify(orchestrator, times(3)).execute(command);
  }

  @Test
  void translatesLockTimeoutAfterRollbackWithoutRetryingIt() {
    when(orchestrator.execute(command))
        .thenThrow(new RuntimeException(new SQLException("lock timeout", "55P03")));

    assertThatThrownBy(() -> executor.execute(command))
        .isInstanceOfSatisfying(
            FlowBoardDomainException.class,
            failure -> {
              assertThat(failure.getErrorCode()).isEqualTo("LOCK_TIMEOUT");
              assertThat(failure.getHttpStatus()).isEqualTo(409);
            });
    verify(orchestrator).execute(command);
  }
}
