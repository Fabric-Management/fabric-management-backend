package com.fabricmanagement.sales.salesorder.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderCoverCaseTest {
  @Test
  void partialThenFinalSettlementAdvancesTheCaseAndWritesClosedAtOnce() {
    OrderCoverCase coverCase = OrderCoverCase.open(UUID.randomUUID(), UUID.randomUUID());
    Instant partialAt = Instant.parse("2026-09-19T12:00:00Z");
    Instant finalAt = partialAt.plusSeconds(60);

    coverCase.settle(false, partialAt);
    assertThat(coverCase.getState()).isEqualTo(OrderCoverCaseState.PARTIALLY_SETTLED);
    assertThat(coverCase.getRevision()).isEqualTo(2);
    assertThat(coverCase.getClosedAt()).isNull();

    coverCase.settle(true, finalAt);
    assertThat(coverCase.getState()).isEqualTo(OrderCoverCaseState.SETTLED);
    assertThat(coverCase.getRevision()).isEqualTo(3);
    assertThat(coverCase.getClosedAt()).isEqualTo(finalAt);
  }
}
