package com.fabricmanagement.offline.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests for {@link SyncStatus} state machine.
 *
 * <p>CR-11-09: Verifies all allowed and forbidden state transitions.
 */
class SyncStatusTest {

  @Test
  @DisplayName("PENDING can transition to SYNCED or CONFLICT")
  void pendingTransitions() {
    assertThat(SyncStatus.PENDING.allowedTransitions())
        .containsExactlyInAnyOrder(SyncStatus.SYNCED, SyncStatus.CONFLICT);
  }

  @Test
  @DisplayName("SYNCED is terminal — no transitions allowed")
  void syncedIsTerminal() {
    assertThat(SyncStatus.SYNCED.allowedTransitions()).isEmpty();
  }

  @Test
  @DisplayName("CONFLICT can only transition to RESOLVED")
  void conflictTransitions() {
    assertThat(SyncStatus.CONFLICT.allowedTransitions()).containsExactly(SyncStatus.RESOLVED);
  }

  @Test
  @DisplayName("RESOLVED can only transition to SYNCED")
  void resolvedTransitions() {
    assertThat(SyncStatus.RESOLVED.allowedTransitions()).containsExactly(SyncStatus.SYNCED);
  }

  @Test
  @DisplayName("canTransitionTo returns true for valid transitions")
  void canTransitionToValidTarget() {
    assertThat(SyncStatus.PENDING.canTransitionTo(SyncStatus.SYNCED)).isTrue();
    assertThat(SyncStatus.PENDING.canTransitionTo(SyncStatus.CONFLICT)).isTrue();
    assertThat(SyncStatus.CONFLICT.canTransitionTo(SyncStatus.RESOLVED)).isTrue();
    assertThat(SyncStatus.RESOLVED.canTransitionTo(SyncStatus.SYNCED)).isTrue();
  }

  @Test
  @DisplayName("canTransitionTo returns false for invalid transitions")
  void canTransitionToInvalidTarget() {
    assertThat(SyncStatus.SYNCED.canTransitionTo(SyncStatus.PENDING)).isFalse();
    assertThat(SyncStatus.SYNCED.canTransitionTo(SyncStatus.CONFLICT)).isFalse();
    assertThat(SyncStatus.PENDING.canTransitionTo(SyncStatus.RESOLVED)).isFalse();
    assertThat(SyncStatus.CONFLICT.canTransitionTo(SyncStatus.SYNCED)).isFalse();
    assertThat(SyncStatus.CONFLICT.canTransitionTo(SyncStatus.PENDING)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(SyncStatus.class)
  @DisplayName("no status can transition to itself")
  void noSelfTransitions(SyncStatus status) {
    assertThat(status.canTransitionTo(status)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(SyncStatus.class)
  @DisplayName("allowedTransitions returns non-null Set for all statuses")
  void allowedTransitionsIsNeverNull(SyncStatus status) {
    Set<SyncStatus> transitions = status.allowedTransitions();
    assertThat(transitions).isNotNull();
  }
}
