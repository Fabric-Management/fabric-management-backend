package com.fabricmanagement.sales.orderintake.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOptionFixtures;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuantityAcceptanceTest {

  private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

  @Test
  @DisplayName("S22: a customer acceptance carries contact, channel and time; no document needed")
  void customerAcceptanceNeedsContactChannelTime() {
    QuantityAcceptance.CustomerEvidence phone =
        new QuantityAcceptance.CustomerEvidence(
            "Jane (buyer)", AcceptanceChannel.PHONE, NOW, null, null, true);
    QuantityAcceptance accepted =
        record(option(QuantityOption.OptionKind.ABOVE, false), phone, null);
    assertThat(accepted.getChannel()).isEqualTo(AcceptanceChannel.PHONE);
    assertThat(accepted.getCustomerContact()).isEqualTo("Jane (buyer)");
    assertThat(accepted.getEvidenceAttachmentId()).isNull();

    QuantityAcceptance.CustomerEvidence noContact =
        new QuantityAcceptance.CustomerEvidence(
            " ", AcceptanceChannel.PHONE, NOW, null, null, true);
    assertThatThrownBy(
            () -> record(option(QuantityOption.OptionKind.ABOVE, false), noContact, null))
        .isInstanceOf(IllegalArgumentException.class);
    QuantityAcceptance.CustomerEvidence noStatement =
        new QuantityAcceptance.CustomerEvidence(
            "Jane", AcceptanceChannel.PHONE, NOW, null, null, false);
    assertThatThrownBy(
            () -> record(option(QuantityOption.OptionKind.ABOVE, false), noStatement, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("An exact option needs no customer contact")
  void exactNeedsNoCustomer() {
    QuantityAcceptance exact =
        QuantityAcceptance.record(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            option(QuantityOption.OptionKind.EXACT, false),
            QuantityOption.Compatibility.SINGLE_LOT,
            new BigDecimal("500"),
            "M",
            QuantityAcceptanceBasis.EXACT_MATCH,
            false,
            null,
            null,
            null,
            UUID.randomUUID(),
            NOW,
            null);
    assertThat(exact.isConditional()).isFalse();
    assertThat(exact.getCustomerContact()).isNull();
  }

  @Test
  @DisplayName(
      "A01: the single-piece remnant must be acknowledged; BELOW needs a remaining decision")
  void remnantAndRemainingNeed() {
    QuantityAcceptance.CustomerEvidence evidence =
        new QuantityAcceptance.CustomerEvidence(
            "Ali", AcceptanceChannel.EMAIL, NOW, null, null, true);
    assertThatThrownBy(
            () ->
                record(
                    option(QuantityOption.OptionKind.REQUESTED_WITH_REMNANT, true), evidence, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> record(option(QuantityOption.OptionKind.BELOW, false), evidence, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            record(
                    option(QuantityOption.OptionKind.BELOW, false),
                    evidence,
                    RemainingNeed.REMAINS_OPEN)
                .getRemainingNeed())
        .isEqualTo(RemainingNeed.REMAINS_OPEN);
  }

  @Test
  @DisplayName("IK-13: a pending-compatibility option is recorded as conditional")
  void pendingCompatibilityIsConditional() {
    QuantityAcceptance.CustomerEvidence evidence =
        new QuantityAcceptance.CustomerEvidence(
            "Ali", AcceptanceChannel.EMAIL, NOW, null, null, true);
    QuantityAcceptance acceptance =
        QuantityAcceptance.record(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            option(QuantityOption.OptionKind.ABOVE, false),
            QuantityOption.Compatibility.PENDING_CONFIRMATION,
            new BigDecimal("514"),
            "M",
            QuantityAcceptanceBasis.CUSTOMER_ACCEPTED,
            false,
            null,
            null,
            evidence,
            UUID.randomUUID(),
            NOW,
            null);
    assertThat(acceptance.isConditional()).isTrue();
  }

  @Test
  @DisplayName("S21: the acceptance covers only the terms it fixed; closing is one-way")
  void coversOnlyItsTerms() {
    QuantityAcceptance.CustomerEvidence evidence =
        new QuantityAcceptance.CustomerEvidence(
            "Ali", AcceptanceChannel.EMAIL, NOW, null, null, true);
    QuantityAcceptance acceptance =
        record(option(QuantityOption.OptionKind.ABOVE, false), evidence, null);
    acceptance.coverTerms("a".repeat(64));
    assertThat(acceptance.covers("a".repeat(64))).isTrue();
    assertThat(acceptance.covers("b".repeat(64))).isFalse();
    assertThatThrownBy(() -> acceptance.coverTerms("c".repeat(64)))
        .isInstanceOf(IllegalStateException.class);
    acceptance.supersede(NOW);
    assertThat(acceptance.covers("a".repeat(64))).isFalse();
    assertThatThrownBy(() -> acceptance.withdraw(NOW)).isInstanceOf(IllegalStateException.class);
  }

  private static QuantityAcceptance record(
      QuantityOption option,
      QuantityAcceptance.CustomerEvidence evidence,
      RemainingNeed remaining) {
    return QuantityAcceptance.record(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        option,
        QuantityOption.Compatibility.SINGLE_LOT,
        option.quantity(),
        "M",
        QuantityAcceptanceBasis.CUSTOMER_ACCEPTED,
        false,
        remaining,
        remaining == null ? null : new BigDecimal("288"),
        evidence,
        UUID.randomUUID(),
        NOW,
        null);
  }

  @Test
  @DisplayName(
      "Pieces of unknown state are not counted as pieces left (Fatih, 2026-10-13); a remnant is"
          + " one or two pool pieces and must be acknowledged")
  void unknownPiecesAreNotCounted_aRemnantIsOneOrTwoPieces() {
    QuantityOption.LotPart emptiedButUnknown =
        QuantityOptionFixtures.lot(
            UUID.randomUUID(),
            "P2",
            List.of(UUID.randomUUID()),
            new BigDecimal("212"),
            0,
            2,
            false,
            null,
            null);
    QuantityOption.LotPart twoLeft =
        QuantityOptionFixtures.lot(
            UUID.randomUUID(),
            "P3",
            List.of(UUID.randomUUID()),
            new BigDecimal("212"),
            2,
            0,
            true,
            UUID.randomUUID(),
            new BigDecimal("60"));

    assertThat(emptiedButUnknown.exhausted()).isFalse();
    QuantityOption unknownOnly =
        QuantityOptionFixtures.option(
            "EXACT:0123456789abcdef",
            QuantityOption.OptionKind.EXACT,
            QuantityOption.Compatibility.SINGLE_LOT,
            new BigDecimal("212"),
            new BigDecimal("212"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            false,
            List.of(emptiedButUnknown));
    assertThat(unknownOnly.leavesRemnant()).isFalse();
    assertThat(unknownOnly.needsRemnantAcknowledgement()).isFalse();

    QuantityOption option =
        QuantityOptionFixtures.option(
            "REQUESTED_WITH_REMNANT:0123456789abcdef",
            QuantityOption.OptionKind.REQUESTED_WITH_REMNANT,
            QuantityOption.Compatibility.SINGLE_LOT,
            new BigDecimal("212"),
            new BigDecimal("212"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            false,
            List.of(twoLeft));
    assertThat(option.leavesRemnant()).isTrue();
    assertThat(option.needsRemnantAcknowledgement()).isTrue();
    QuantityAcceptance.CustomerEvidence evidence =
        new QuantityAcceptance.CustomerEvidence(
            "Ali", AcceptanceChannel.EMAIL, NOW, null, null, true);
    assertThatThrownBy(() -> record(option, evidence, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("remnant");
  }

  static QuantityOption option(QuantityOption.OptionKind kind, boolean remnant) {
    UUID piece = UUID.randomUUID();
    return QuantityOptionFixtures.option(
        kind + ":0123456789abcdef",
        kind,
        QuantityOption.Compatibility.SINGLE_LOT,
        new BigDecimal("212"),
        new BigDecimal("212"),
        new BigDecimal("-288"),
        new BigDecimal("-57.6"),
        false,
        List.of(
            QuantityOptionFixtures.lot(
                UUID.randomUUID(),
                "P1",
                List.of(piece),
                new BigDecimal("212"),
                remnant ? 1 : 0,
                0,
                remnant,
                remnant ? UUID.randomUUID() : null,
                remnant ? new BigDecimal("30") : null)));
  }
}
