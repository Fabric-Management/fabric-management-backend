package com.fabricmanagement.flowboard.routing.app;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.task.app.*;
import com.fabricmanagement.flowboard.task.domain.*;
import com.fabricmanagement.flowboard.task.infra.repository.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

@Import(RoutingTransitionRaceIT.Actions.class)
class RoutingTransitionRaceIT extends RoutingIntegrationSupport {
  @Autowired private TaskTransitionOrchestrator orchestrator;
  @Autowired private TaskAffectedSubjectService subjects;
  @Autowired private TaskAffectedSubjectRepository subjectRepository;
  @Autowired private TaskTransitionAttemptRepository attempts;
  @Autowired private TestAction action;

  @TestConfiguration
  static class Actions {
    @Bean
    TestAction routingTestAction(TaskAffectedSubjectRepository subjects) {
      return new TestAction(subjects);
    }
  }

  static class TestAction implements DomainTaskAction {
    private final TaskAffectedSubjectRepository subjects;
    final AtomicReference<CountDownLatch> boundary = new AtomicReference<>();

    TestAction(TaskAffectedSubjectRepository subjects) {
      this.subjects = subjects;
    }

    public String actionKey() {
      return "ROUTING_TEST_PARTIAL";
    }

    public boolean supports(Task task) {
      return task.getTaskType() == TaskType.ORDER_COVER;
    }

    public DomainTaskActionResult execute(Task task, TaskActionCommand command) {
      var latch = boundary.get();
      if (latch != null) {
        latch.countDown();
        await(latch);
      }
      var remaining =
          subjects.findAllByTaskId(task.getId()).stream()
              .filter(row -> Boolean.TRUE.equals(row.getIsActive()))
              .filter(row -> !row.getSubjectId().equals(command.actorId()))
              .map(row -> new TaskSubject(row.getSubjectType(), row.getSubjectId()))
              .collect(Collectors.toSet());
      return new DomainTaskActionResult.Accepted("TEST_PARTIAL", UUID.randomUUID(), remaining);
    }
  }

  @Test
  void partialAcceptanceConsumesVersionWithoutClosingTaskAndReplayDoesNotConsumeItAgain() {
    var a = user("A", "flowboard:write", "sales:write");
    var b = user("B", "flowboard:write", "sales:write");
    var task = task(a.getId());
    configure(a.getId(), b.getId());
    evaluate(task);
    tx(
        () -> {
          subjects.synchronize(
              task.getId(),
              Set.of(
                  new TaskSubject("TEST_SCOPE", a.getId()),
                  new TaskSubject("TEST_SCOPE", b.getId())));
          return null;
        });
    long version = tasks.findById(task.getId()).orElseThrow().getVersion();
    var command = command(task, a.getId(), "routing-partial", version);

    assertThat(execute(command)).isTrue();
    var remaining = tasks.findById(task.getId()).orElseThrow();
    assertThat(remaining.getVersion()).isEqualTo(version + 1);
    assertThat(remaining.getClosedAt()).isNull();
    assertThat(activeSubjects(task)).containsExactly(new TaskSubject("TEST_SCOPE", b.getId()));

    assertThat(execute(command)).isTrue();
    assertThat(tasks.findById(task.getId()).orElseThrow().getVersion()).isEqualTo(version + 1);
    assertThat(activeSubjects(task)).containsExactly(new TaskSubject("TEST_SCOPE", b.getId()));
  }

  @Test
  void onlyOneRecipientConsumesTheVersionAndLoserCanRetryItsUnreservedKey() throws Exception {
    var a = user("A", "flowboard:write", "sales:write");
    var b = user("B", "flowboard:write", "sales:write");
    var task = task(a.getId());
    configure(a.getId(), b.getId());
    evaluate(task);
    tx(
        () -> {
          subjects.synchronize(
              task.getId(),
              Set.of(
                  new TaskSubject("TEST_SCOPE", a.getId()),
                  new TaskSubject("TEST_SCOPE", b.getId())));
          return null;
        });
    long version = tasks.findById(task.getId()).orElseThrow().getVersion();
    var first = command(task, a.getId(), "routing-race-a", version);
    var second = command(task, b.getId(), "routing-race-b", version);
    action.boundary.set(new CountDownLatch(2));
    List<Boolean> result;
    try {
      result = race(() -> execute(first), () -> execute(second));
    } finally {
      action.boundary.set(null);
    }
    assertThat(result).containsExactlyInAnyOrder(true, false);
    var winner = result.get(0) ? first : second;
    var loser = result.get(0) ? second : first;
    assertThat(
            attempts.findByTenantIdAndActorIdAndIdempotencyKey(
                tenant, winner.actorId(), winner.idempotencyKey()))
        .get()
        .extracting(TaskTransitionAttempt::getOutcome)
        .isEqualTo(TaskTransitionOutcome.ACCEPTED);
    assertThat(
            attempts.findByTenantIdAndActorIdAndIdempotencyKey(
                tenant, loser.actorId(), loser.idempotencyKey()))
        .isEmpty();
    var remaining = tasks.findById(task.getId()).orElseThrow();
    assertThat(remaining.getClosedAt()).isNull();
    assertThat(remaining.getVersion()).isEqualTo(version + 1);
    assertThat(activeSubjects(task))
        .containsExactly(new TaskSubject("TEST_SCOPE", loser.actorId()));
    assertThat(
            execute(command(task, loser.actorId(), loser.idempotencyKey(), remaining.getVersion())))
        .isTrue();
    assertThat(tasks.findById(task.getId()).orElseThrow().getClosedAt()).isNotNull();
    assertThat(activeSubjects(task)).isEmpty();
  }

  private Set<TaskSubject> activeSubjects(Task task) {
    return subjectRepository.findAllByTaskId(task.getId()).stream()
        .filter(row -> Boolean.TRUE.equals(row.getIsActive()))
        .map(row -> new TaskSubject(row.getSubjectType(), row.getSubjectId()))
        .collect(Collectors.toSet());
  }

  private TaskActionCommand command(Task task, UUID actor, String key, long version) {
    return new TaskActionCommand(
        task.getId(), actor, key, "ROUTING_TEST_PARTIAL", "c".repeat(64), version);
  }

  private boolean execute(TaskActionCommand command) {
    TenantContext.setCurrentUserId(command.actorId());
    try {
      assertThat(orchestrator.execute(command).outcome()).isEqualTo(TaskTransitionOutcome.ACCEPTED);
      return true;
    } catch (org.springframework.orm.ObjectOptimisticLockingFailureException
        | jakarta.persistence.OptimisticLockException expected) {
      return false;
    }
  }
}
