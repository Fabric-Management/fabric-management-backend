package com.fabricmanagement.production.core.batch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SOI A04: asking is never the answer; only a covering confirmation closes the question. */
class LotCompatibilityRequestTest {

  private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");
  private final UUID lotA = UUID.randomUUID();
  private final UUID lotB = UUID.randomUUID();
  private final UUID customer = UUID.randomUUID();

  @Test
  @DisplayName("A confirmation of the same lots for this customer answers the request")
  void answeredByCoveringConfirmation() {
    LotCompatibilityRequest request = open();
    LotCompatibilityConfirmation other =
        LotCompatibilityConfirmation.confirm(
            List.of(lotA, UUID.randomUUID()), null, "other lots", UUID.randomUUID(), NOW);
    LotCompatibilityConfirmation otherCustomer =
        LotCompatibilityConfirmation.confirm(
            List.of(lotA, lotB), UUID.randomUUID(), "for someone else", UUID.randomUUID(), NOW);
    LotCompatibilityConfirmation covering =
        LotCompatibilityConfirmation.confirm(
            List.of(lotA, lotB, UUID.randomUUID()), null, "same recipe", UUID.randomUUID(), NOW);

    assertThat(request.isAnsweredBy(other)).isFalse();
    assertThat(request.isAnsweredBy(otherCustomer)).isFalse();
    assertThat(request.isAnsweredBy(covering)).isTrue();

    request.confirmedBy(covering);
    assertThat(request.getStatus()).isEqualTo(LotCompatibilityRequestStatus.CONFIRMED);
    assertThat(request.isAnsweredBy(covering)).isFalse();
  }

  @Test
  @DisplayName("A decline needs its reason; a closed request stays closed")
  void declineNeedsReason() {
    LotCompatibilityRequest request = open();
    assertThatThrownBy(() -> request.decline(UUID.randomUUID(), NOW, " "))
        .isInstanceOf(IllegalArgumentException.class);
    request.decline(UUID.randomUUID(), NOW, "Visible shade step between lots");
    assertThat(request.getStatus()).isEqualTo(LotCompatibilityRequestStatus.DECLINED);
    assertThatThrownBy(() -> request.withdraw(UUID.randomUUID(), NOW))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void keyIgnoresOrder() {
    assertThat(LotCompatibilityRequest.key(List.of(lotA, lotB)))
        .isEqualTo(LotCompatibilityRequest.key(List.of(lotB, lotA)));
  }

  private LotCompatibilityRequest open() {
    return LotCompatibilityRequest.open(
        List.of(lotA, lotB),
        UUID.randomUUID(),
        customer,
        "SALES_ORDER_LINE",
        UUID.randomUUID(),
        null,
        UUID.randomUUID(),
        NOW);
  }
}
