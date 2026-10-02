package com.fabricmanagement.sales.orderintake.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerToneAcceptanceTest {
  @Test
  void toneAcceptanceRequiresAndRetainsTheRecordersStatement() {
    assertThatThrownBy(() -> record(false)).isInstanceOf(IllegalArgumentException.class);
    assertThat(record(true).isCustomerStatementConfirmed()).isTrue();
  }

  private CustomerToneAcceptance record(boolean statement) {
    return CustomerToneAcceptance.record(
        UUID.randomUUID(),
        UUID.randomUUID(),
        List.of(UUID.randomUUID(), UUID.randomUUID()),
        "Customer saw both swatches",
        null,
        "Customer contact",
        AcceptanceChannel.PHONE,
        Instant.parse("2026-09-27T10:00:00Z"),
        statement,
        UUID.randomUUID());
  }
}
