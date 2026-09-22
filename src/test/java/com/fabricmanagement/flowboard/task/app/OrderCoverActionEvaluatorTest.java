package com.fabricmanagement.flowboard.task.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.flowboard.task.domain.OrderCoverActionEvaluator;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Golden table captured from the pre-extraction adapter + query-service decision chain. */
class OrderCoverActionEvaluatorTest {
  private static final UUID ACTOR = UUID.fromString("10000000-0000-0000-0000-000000000002");

  @Test
  void goldenTableRecordsEveryCombinationOfTheEightLegacyConditions() {
    IntStream.range(0, 1 << 8)
        .mapToObj(Conditions::fromMask)
        .forEach(
            conditions -> {
              var evaluated =
                  OrderCoverActionEvaluator.evaluate(
                      new OrderCoverActionEvaluator.Inputs(
                          !conditions.closed(),
                          !conditions.taskMissing(),
                          !conditions.candidacyFailed(),
                          !conditions.notPoolMember(),
                          !conditions.outsideWriteScope(),
                          !conditions.unassigned(),
                          conditions.unassigned()
                              ? List.of()
                              : List.of(conditions.assignedElsewhere() ? UUID.randomUUID() : ACTOR),
                          ACTOR,
                          !conditions.evidenceUnknown()));
              assertThat(evaluated.blockedReason())
                  .as("extracted mask %s", conditions.mask())
                  .isEqualTo(recordedLegacyReason(conditions));
            });
  }

  @Test
  void departmentOnlyAssignmentIsAssignedElsewhereRatherThanUnassigned() {
    var result =
        OrderCoverActionEvaluator.evaluate(
            new OrderCoverActionEvaluator.Inputs(
                true, true, true, true, true, true, List.of(), ACTOR, true));
    assertThat(result.blockedReason()).isEqualTo("ASSIGNED_ELSEWHERE");
  }

  /** Frozen precedence copied from the pre-extraction adapter and query-service merge. */
  private static String recordedLegacyReason(Conditions conditions) {
    if (conditions.closed()) return "CASE_CLOSED";
    if (conditions.taskMissing()) return "UNASSIGNED";
    if (conditions.candidacyFailed()) return "PERMISSION_DENIED";
    if (conditions.notPoolMember()) return "OUTSIDE_ROUTING_POOL";
    if (conditions.outsideWriteScope()) return "PERMISSION_DENIED";
    if (conditions.unassigned()) return "UNASSIGNED";
    if (conditions.assignedElsewhere()) return "ASSIGNED_ELSEWHERE";
    return conditions.evidenceUnknown() ? "EVIDENCE_UNKNOWN" : null;
  }

  private record Conditions(int mask) {
    static Conditions fromMask(int mask) {
      return new Conditions(mask);
    }

    boolean closed() {
      return flag(0);
    }

    boolean taskMissing() {
      return flag(1);
    }

    boolean candidacyFailed() {
      return flag(2);
    }

    boolean notPoolMember() {
      return flag(3);
    }

    boolean outsideWriteScope() {
      return flag(4);
    }

    boolean unassigned() {
      return flag(5);
    }

    boolean assignedElsewhere() {
      return flag(6);
    }

    boolean evidenceUnknown() {
      return flag(7);
    }

    private boolean flag(int bit) {
      return (mask & (1 << bit)) != 0;
    }
  }
}
