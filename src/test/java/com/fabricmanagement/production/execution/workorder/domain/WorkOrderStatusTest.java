package com.fabricmanagement.production.execution.workorder.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("WorkOrderStatus — state machine")
class WorkOrderStatusTest {

  @Nested
  @DisplayName("Valid transitions")
  class ValidTransitions {

    static Stream<Arguments> validTransitionPairs() {
      return Stream.of(
          // DRAFT transitions
          Arguments.of(WorkOrderStatus.DRAFT, WorkOrderStatus.PENDING_APPROVAL),
          Arguments.of(WorkOrderStatus.DRAFT, WorkOrderStatus.SENT),
          Arguments.of(WorkOrderStatus.DRAFT, WorkOrderStatus.CANCELLED),
          // PENDING_APPROVAL transitions
          Arguments.of(WorkOrderStatus.PENDING_APPROVAL, WorkOrderStatus.APPROVED),
          Arguments.of(WorkOrderStatus.PENDING_APPROVAL, WorkOrderStatus.REJECTED),
          Arguments.of(WorkOrderStatus.PENDING_APPROVAL, WorkOrderStatus.CANCELLED),
          // APPROVED transitions
          Arguments.of(WorkOrderStatus.APPROVED, WorkOrderStatus.SENT),
          Arguments.of(WorkOrderStatus.APPROVED, WorkOrderStatus.CANCELLED),
          // REJECTED transitions
          Arguments.of(WorkOrderStatus.REJECTED, WorkOrderStatus.DRAFT),
          Arguments.of(WorkOrderStatus.REJECTED, WorkOrderStatus.CANCELLED),
          // SENT transitions
          Arguments.of(WorkOrderStatus.SENT, WorkOrderStatus.IN_PROGRESS),
          Arguments.of(WorkOrderStatus.SENT, WorkOrderStatus.CANCELLED),
          // IN_PROGRESS transitions
          Arguments.of(WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.COMPLETED),
          Arguments.of(WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.CANCELLED));
    }

    @ParameterizedTest(name = "{0} → {1} should be allowed")
    @MethodSource("validTransitionPairs")
    void allowsValidTransition(WorkOrderStatus from, WorkOrderStatus to) {
      assertThat(from.canTransitionTo(to)).as("%s → %s should be valid", from, to).isTrue();
    }
  }

  @Nested
  @DisplayName("Invalid transitions")
  class InvalidTransitions {

    static Stream<Arguments> invalidTransitionPairs() {
      return Stream.of(
          // DRAFT cannot go directly to APPROVED, IN_PROGRESS, COMPLETED
          Arguments.of(WorkOrderStatus.DRAFT, WorkOrderStatus.APPROVED),
          Arguments.of(WorkOrderStatus.DRAFT, WorkOrderStatus.IN_PROGRESS),
          Arguments.of(WorkOrderStatus.DRAFT, WorkOrderStatus.COMPLETED),
          // APPROVED cannot go back to DRAFT or skip to IN_PROGRESS
          Arguments.of(WorkOrderStatus.APPROVED, WorkOrderStatus.DRAFT),
          Arguments.of(WorkOrderStatus.APPROVED, WorkOrderStatus.IN_PROGRESS),
          Arguments.of(WorkOrderStatus.APPROVED, WorkOrderStatus.COMPLETED),
          // SENT cannot go back
          Arguments.of(WorkOrderStatus.SENT, WorkOrderStatus.DRAFT),
          Arguments.of(WorkOrderStatus.SENT, WorkOrderStatus.APPROVED),
          // IN_PROGRESS cannot go back
          Arguments.of(WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.DRAFT),
          Arguments.of(WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.SENT));
    }

    @ParameterizedTest(name = "{0} → {1} should be blocked")
    @MethodSource("invalidTransitionPairs")
    void blocksInvalidTransition(WorkOrderStatus from, WorkOrderStatus to) {
      assertThat(from.canTransitionTo(to)).as("%s → %s should be invalid", from, to).isFalse();
    }
  }

  @Nested
  @DisplayName("Terminal states")
  class TerminalStates {

    @ParameterizedTest(name = "COMPLETED → {0} should be blocked")
    @EnumSource(WorkOrderStatus.class)
    void completedIsTerminal(WorkOrderStatus target) {
      assertThat(WorkOrderStatus.COMPLETED.canTransitionTo(target)).isFalse();
    }

    @ParameterizedTest(name = "CANCELLED → {0} should be blocked")
    @EnumSource(WorkOrderStatus.class)
    void cancelledIsTerminal(WorkOrderStatus target) {
      assertThat(WorkOrderStatus.CANCELLED.canTransitionTo(target)).isFalse();
    }
  }

  @Nested
  @DisplayName("Self-transitions")
  class SelfTransitions {

    @ParameterizedTest(name = "{0} → {0} should be blocked")
    @EnumSource(WorkOrderStatus.class)
    void selfTransitionNotAllowed(WorkOrderStatus status) {
      assertThat(status.canTransitionTo(status))
          .as("Self-transition %s → %s should not be allowed", status, status)
          .isFalse();
    }
  }

  @Test
  @DisplayName("Every status has a defined transition set (no missing entries)")
  void allStatusesHaveTransitionsDefined() {
    for (WorkOrderStatus status : WorkOrderStatus.values()) {
      // canTransitionTo should not throw NPE for any status pair
      for (WorkOrderStatus target : WorkOrderStatus.values()) {
        // This call should never throw — just verifying no missing map entries
        status.canTransitionTo(target);
      }
    }
  }
}
