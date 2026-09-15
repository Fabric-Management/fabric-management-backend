package com.fabricmanagement.flowboard.task.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.task.app.DomainTaskAction;
import com.fabricmanagement.flowboard.task.app.TaskGenerationKey;
import com.fabricmanagement.flowboard.task.app.TaskProvisioningService;
import com.fabricmanagement.flowboard.task.app.TaskTransitionOrchestrator;
import com.fabricmanagement.flowboard.task.domain.DomainTaskActionResult;
import com.fabricmanagement.flowboard.task.domain.ModuleType;
import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskActionCommand;
import com.fabricmanagement.flowboard.task.domain.TaskCreation;
import com.fabricmanagement.flowboard.task.domain.TaskTransitionOutcome;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.fabricmanagement.flowboard.task.infra.repository.TaskTransitionAttemptRepository;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TaskTransitionReplayIT.Actions.class)
class TaskTransitionReplayIT extends AbstractIntegrationTest {

  private static final String ACCEPT = "TEST_ACCEPT";
  private static final String REJECT = "TEST_REJECT";
  private static final String FAIL = "TEST_TECHNICAL_FAILURE";
  private static final String FINGERPRINT = "b".repeat(64);

  @Autowired private JdbcTemplate jdbc;
  @Autowired private TaskProvisioningService provisioningService;
  @Autowired private TaskTransitionOrchestrator orchestrator;
  @Autowired private TaskTransitionAttemptRepository attemptRepository;

  @Test
  void lostResponseReplayReturnsOriginalResultBeforeStaleVersionCheck() {
    Fixture fixture = fixture();
    TaskActionCommand command = fixture.command(ACCEPT, "idem-lost-response", FINGERPRINT);

    var first = inContext(fixture, () -> orchestrator.execute(command));
    var replay = inContext(fixture, () -> orchestrator.execute(command));

    assertThat(first.outcome()).isEqualTo(TaskTransitionOutcome.ACCEPTED);
    assertThat(replay.resultId()).isEqualTo(first.resultId());
    assertThat(replay.replayed()).isTrue();
    assertThatThrownBy(
            () ->
                inContext(
                    fixture,
                    () ->
                        orchestrator.execute(
                            new TaskActionCommand(
                                command.taskId(),
                                command.actorId(),
                                command.idempotencyKey(),
                                command.actionKey(),
                                "c".repeat(64),
                                command.expectedVersion()))))
        .isInstanceOf(
            com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException.class);
  }

  @Test
  void concurrentSameKeyCallsBothReceiveTheWinnerResult() throws Exception {
    Fixture fixture = fixture();
    TaskActionCommand command = fixture.command(ACCEPT, "idem-concurrent", FINGERPRINT);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> concurrentExecute(fixture, command, ready, start));
      var second = executor.submit(() -> concurrentExecute(fixture, command, ready, start));
      ready.await();
      start.countDown();

      var firstResult = first.get();
      var secondResult = second.get();
      assertThat(firstResult.resultId()).isEqualTo(secondResult.resultId());
      assertThat(java.util.List.of(firstResult.replayed(), secondResult.replayed()))
          .containsExactlyInAnyOrder(false, true);
    }
  }

  @Test
  void genuinelyStaleVersionIsRejectedAndDoesNotReserveTheIdempotencyKey() {
    Fixture fixture = fixture();
    TaskActionCommand command =
        new TaskActionCommand(
            fixture.task().getId(),
            fixture.actorId(),
            "idem-stale",
            ACCEPT,
            FINGERPRINT,
            fixture.task().getVersion() + 1);

    assertThatThrownBy(() -> inContext(fixture, () -> orchestrator.execute(command)))
        .isInstanceOf(jakarta.persistence.OptimisticLockException.class);
    assertThat(
            inContext(
                fixture,
                () ->
                    attemptRepository.findByTenantIdAndActorIdAndIdempotencyKey(
                        fixture.tenantId(), fixture.actorId(), command.idempotencyKey())))
        .isEmpty();
  }

  @Test
  void businessRejectionCommitsTheAttemptWithoutChangingTheTask() {
    Fixture fixture = fixture();
    TaskActionCommand command = fixture.command(REJECT, "idem-business", FINGERPRINT);

    var result = inContext(fixture, () -> orchestrator.execute(command));

    assertThat(result.outcome()).isEqualTo(TaskTransitionOutcome.REJECTED_BUSINESS);
    assertThat(result.rejectionCode()).isEqualTo("TEST_POLICY_REFUSAL");
    assertThat(
            inContext(
                fixture,
                () ->
                    attemptRepository.findByTenantIdAndActorIdAndIdempotencyKey(
                        fixture.tenantId(), fixture.actorId(), command.idempotencyKey())))
        .hasValueSatisfying(
            attempt ->
                assertThat(attempt.getOutcome())
                    .isEqualTo(TaskTransitionOutcome.REJECTED_BUSINESS));
    assertThat(
            jdbc.queryForMap(
                "SELECT status, closed_at FROM flowboard.task WHERE id = ?",
                fixture.task().getId()))
        .containsEntry("status", "BACKLOG")
        .containsEntry("closed_at", null);
  }

  @Test
  void technicalFailureRollsBackItsClaim() {
    Fixture fixture = fixture();
    TaskActionCommand command = fixture.command(FAIL, "idem-technical", FINGERPRINT);

    assertThatThrownBy(() -> inContext(fixture, () -> orchestrator.execute(command)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("simulated technical failure");

    inContext(
        fixture,
        () -> {
          assertThat(
                  attemptRepository.findByTenantIdAndActorIdAndIdempotencyKey(
                      fixture.tenantId(), fixture.actorId(), command.idempotencyKey()))
              .isEmpty();
          return null;
        });
    assertThat(
            jdbc.queryForMap(
                "SELECT status, closed_at FROM flowboard.task WHERE id = ?",
                fixture.task().getId()))
        .containsEntry("status", "BACKLOG")
        .containsEntry("closed_at", null);
  }

  private com.fabricmanagement.flowboard.task.dto.TaskTransitionResult concurrentExecute(
      Fixture fixture, TaskActionCommand command, CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    ready.countDown();
    start.await();
    return inContext(fixture, () -> orchestrator.execute(command));
  }

  private Fixture fixture() {
    UUID tenantId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    UUID boardId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    seedBoard(tenantId, boardId);
    Fixture seed = new Fixture(tenantId, actorId, boardId, orderId, null);
    Task task =
        inContext(
            seed,
            () ->
                provisioningService.createOrSynchronizeActive(
                    new TaskCreation(
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
                        TaskGenerationKey.subject(
                            "SALES_ORDER", orderId, TaskType.ORDER_COVER, null),
                        Set.of())));
    return new Fixture(tenantId, actorId, boardId, orderId, task);
  }

  private <T> T inContext(Fixture fixture, java.util.concurrent.Callable<T> work) {
    try {
      TenantContext.setCurrentTenantId(fixture.tenantId());
      TenantContext.setCurrentTenantUid("REPLAY-001");
      TenantContext.setCurrentUserId(fixture.actorId());
      return work.call();
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    } finally {
      TenantContext.clear();
    }
  }

  private void seedBoard(UUID tenantId, UUID boardId) {
    jdbc.update(
        """
        INSERT INTO flowboard.board (
            id, tenant_id, uid, name, board_type, wip_limit_default, default_view_type,
            is_active, created_at, updated_at, version
        ) VALUES (?, ?, ?, 'Replay board', 'GLOBAL', 5, 'KANBAN', TRUE, NOW(), NOW(), 0)
        """,
        boardId,
        tenantId,
        "replay-board-" + boardId);
  }

  private record Fixture(UUID tenantId, UUID actorId, UUID boardId, UUID orderId, Task task) {
    TaskActionCommand command(String action, String idempotencyKey, String fingerprint) {
      return new TaskActionCommand(
          task.getId(), actorId, idempotencyKey, action, fingerprint, task.getVersion());
    }
  }

  @TestConfiguration
  static class Actions {
    @Bean
    DomainTaskAction acceptingTaskAction() {
      return new DomainTaskAction() {
        public String actionKey() {
          return ACCEPT;
        }

        public boolean supports(Task task) {
          return true;
        }

        public DomainTaskActionResult execute(Task task, TaskActionCommand command) {
          return new DomainTaskActionResult.Accepted("TEST_RESULT", command.taskId());
        }
      };
    }

    @Bean
    DomainTaskAction failingTaskAction() {
      return new DomainTaskAction() {
        public String actionKey() {
          return FAIL;
        }

        public boolean supports(Task task) {
          return true;
        }

        public DomainTaskActionResult execute(Task task, TaskActionCommand command) {
          throw new IllegalStateException("simulated technical failure");
        }
      };
    }

    @Bean
    DomainTaskAction rejectingTaskAction() {
      return new DomainTaskAction() {
        public String actionKey() {
          return REJECT;
        }

        public boolean supports(Task task) {
          return true;
        }

        public DomainTaskActionResult execute(Task task, TaskActionCommand command) {
          return new DomainTaskActionResult.Rejected(
              "TEST_POLICY_REFUSAL", "The test policy rejected this transition");
        }
      };
    }
  }
}
