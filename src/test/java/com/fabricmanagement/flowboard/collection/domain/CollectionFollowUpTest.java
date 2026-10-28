package com.fabricmanagement.flowboard.collection.domain;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.flowboard.collection.app.port.CollectionFollowUpPort.Facts;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CollectionFollowUpTest {
  @Test
  void aPromiseAndLaterNoAnswerPreserveTheAgreedDateAndLastPromise() {
    var c = CollectionFollowUp.open(UUID.randomUUID(), 1);
    var agreed = LocalDate.of(2026, 10, 30);
    c.observe(
        1,
        "COLLECT_PAYMENT",
        agreed,
        "GBP",
        new BigDecimal("4284.00"),
        false,
        false,
        Instant.parse("2026-10-22T10:00:00Z"));
    c.contact(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2), new BigDecimal("1000.00"));
    assertThat(c.getDueDate()).isEqualTo(agreed);
    assertThat(c.getOutstanding()).isEqualByComparingTo("4284.00");
    assertThat(c.actionableOn()).isEqualTo(LocalDate.of(2026, 11, 2));
    c.contact(LocalDate.of(2026, 11, 3), null, null);
    assertThat(c.getPromisedOn()).isEqualTo(LocalDate.of(2026, 11, 2));
    assertThat(c.getPromisedAmount()).isEqualByComparingTo("1000.00");
  }

  @Test
  void receivedChequeChangesTheWorkStageWithoutSettlingCash() {
    var f =
        new Facts(
            UUID.randomUUID(),
            1,
            UUID.randomUUID(),
            "GBP",
            new BigDecimal("4284"),
            new BigDecimal("4284"),
            BigDecimal.ZERO,
            LocalDate.of(2026, 10, 30),
            LocalDate.of(2027, 7, 20),
            true,
            false,
            false,
            false,
            null);
    assertThat(f.phase()).isEqualTo("CLEAR_CHEQUE");
    assertThat(f.closed()).isFalse();
    assertThat(f.dueDate()).isEqualTo(LocalDate.of(2027, 7, 20));
    var missing =
        new Facts(
            f.obligationId(),
            2,
            f.partnerId(),
            "GBP",
            f.expected(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            f.agreedDate(),
            f.chequeDueDate(),
            true,
            false,
            true,
            false,
            null);
    assertThat(missing.closed()).as("Missing evidence is never a settled source").isFalse();
  }

  @Test
  void closureKeepsPromiseAndItsOldEpisodeIdentity() {
    var c = CollectionFollowUp.open(UUID.randomUUID(), 1);
    c.contact(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2), new BigDecimal("1000.00"));
    c.close(Instant.parse("2026-11-02T10:00:00Z"), "RECORDED_SETTLED");
    assertThat(c.getPromisedAmount()).isEqualByComparingTo("1000.00");
    assertThat(c.getClosureReason()).isEqualTo("RECORDED_SETTLED");
    assertThat(CollectionFollowUp.open(c.getObligationId(), 2).getClosedAt()).isNull();
  }
}
