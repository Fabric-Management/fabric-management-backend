package com.fabricmanagement.production.core.workorder.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.production.core.workorder.domain.exception.WorkOrderHoldException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SOI S13 / A12: request, physical stop and resume are separate facts. */
class WorkOrderHoldTest {

  private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

  @Test
  @DisplayName("S13: a request is not a stop; the stop is confirmed; resume needs the checks")
  void requestConfirmResume() {
    WorkOrderHold hold =
        WorkOrderHold.request(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "width change",
            UUID.randomUUID(),
            NOW);
    assertThat(hold.getStatus()).isEqualTo(WorkOrderHold.Status.HOLD_REQUESTED);
    assertThat(hold.getConfirmedAt()).isNull();

    assertThatThrownBy(() -> hold.resume(true, true, "go", UUID.randomUUID(), NOW))
        .isInstanceOf(IllegalStateException.class);

    hold.confirmStop("Stopped after the second dye bath", UUID.randomUUID(), NOW);
    assertThat(hold.getStatus()).isEqualTo(WorkOrderHold.Status.HOLD_CONFIRMED);

    assertThatThrownBy(() -> hold.resume(false, true, "go", UUID.randomUUID(), NOW))
        .isInstanceOf(WorkOrderHoldException.class)
        .hasMessageContaining("customer change");
    assertThatThrownBy(() -> hold.resume(true, false, "go", UUID.randomUUID(), NOW))
        .isInstanceOf(WorkOrderHoldException.class)
        .hasMessageContaining("checks");

    hold.resume(true, true, "Recipe v3 checked, greige re-issued", UUID.randomUUID(), NOW);
    assertThat(hold.getStatus()).isEqualTo(WorkOrderHold.Status.RESUMED);
    assertThat(hold.isOpen()).isFalse();
  }

  @Test
  void theStopNeedsANote() {
    WorkOrderHold hold =
        WorkOrderHold.request(
            UUID.randomUUID(), null, UUID.randomUUID(), "reason", UUID.randomUUID(), NOW);
    assertThatThrownBy(() -> hold.confirmStop(" ", UUID.randomUUID(), NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
