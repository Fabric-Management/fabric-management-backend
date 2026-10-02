package com.fabricmanagement.sales.salesorder.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.sales.common.exception.OrderDomainException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DeliveryCommitmentTest {

  private static final UUID ORDER = UUID.randomUUID();
  private static final UUID ACTOR = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
  private static final DeliveryTerms FCA = DeliveryTerms.of(DeliveryTerm.FCA, "Mill gate", null);

  private static DeliveryCommitment.Agreement agreement(
      LocalDate on, DeliveryTerms terms, CommitmentChangeOrigin origin, String reason) {
    return new DeliveryCommitment.Agreement(
        on, terms, origin, reason, "Jane Smith", CommitmentChannel.EMAIL, NOW.minusSeconds(60));
  }

  private static DeliveryCommitment initial() {
    DeliveryCommitment value =
        DeliveryCommitment.record(
            ORDER, null, agreement(LocalDate.of(2026, 10, 21), FCA, null, null), ACTOR, NOW);
    ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
    return value;
  }

  @Test
  void theFirstPromiseIsInitialAndKeepsTheEventItWasGivenFor() {
    DeliveryCommitment value = initial();

    assertThat(value.getSequence()).isEqualTo(1);
    assertThat(value.getOrigin()).isEqualTo(CommitmentChangeOrigin.INITIAL);
    assertThat(value.getPreviousCommitmentId()).isNull();
    assertThat(value.getDeliveryEvent()).isEqualTo(DeliveryEvent.HANDED_TO_CARRIER);
    assertThat(value.getDeliveryPlace()).isEqualTo("Mill gate");
    assertThat(value.getIncotermsVersion()).isEqualTo(IncotermsVersion.INCOTERMS_2020);
  }

  @Test
  void aCommittedDateNeedsAnAgreedTermSoItRefersToAKnownEvent() {
    assertThatThrownBy(
            () ->
                DeliveryCommitment.record(
                    ORDER,
                    null,
                    agreement(LocalDate.of(2026, 10, 21), DeliveryTerms.NONE, null, null),
                    ACTOR,
                    NOW))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("delivery term");
  }

  @Test
  void aChangeNamesWhoAskedForItAndWhy() {
    DeliveryCommitment first = initial();

    assertThatThrownBy(
            () ->
                DeliveryCommitment.record(
                    ORDER,
                    first,
                    agreement(LocalDate.of(2026, 10, 24), FCA, null, "x"),
                    ACTOR,
                    NOW))
        .isInstanceOf(OrderDomainException.class);
    assertThatThrownBy(
            () ->
                DeliveryCommitment.record(
                    ORDER,
                    first,
                    agreement(
                        LocalDate.of(2026, 10, 24),
                        FCA,
                        CommitmentChangeOrigin.SELLER_REVISION,
                        "  "),
                    ACTOR,
                    NOW))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("reason");

    DeliveryCommitment change =
        DeliveryCommitment.record(
            ORDER,
            first,
            agreement(
                LocalDate.of(2026, 10, 24),
                FCA,
                CommitmentChangeOrigin.BUYER_REQUEST,
                " Buyer added 2,000 m "),
            ACTOR,
            NOW);

    assertThat(change.getSequence()).isEqualTo(2);
    assertThat(change.getPreviousCommitmentId()).isEqualTo(first.getId());
    assertThat(change.getReason()).isEqualTo("Buyer added 2,000 m");
  }

  @Test
  void aChangeMustChangeTheDateOrTheTerm() {
    DeliveryCommitment first = initial();

    assertThatThrownBy(
            () ->
                DeliveryCommitment.record(
                    ORDER,
                    first,
                    agreement(
                        LocalDate.of(2026, 10, 21),
                        FCA,
                        CommitmentChangeOrigin.SELLER_REVISION,
                        "Same again"),
                    ACTOR,
                    NOW))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("Nothing changed");

    DeliveryCommitment renegotiated =
        DeliveryCommitment.record(
            ORDER,
            first,
            agreement(
                LocalDate.of(2026, 10, 21),
                DeliveryTerms.of(DeliveryTerm.DAP, "Buyer DC, Leicester", null),
                CommitmentChangeOrigin.BUYER_REQUEST,
                "Buyer asked us to deliver"),
            ACTOR,
            NOW);
    assertThat(renegotiated.getDeliveryEvent())
        .isEqualTo(DeliveryEvent.READY_FOR_UNLOADING_AT_DESTINATION);
  }

  @Test
  void everyRecordCarriesHowTheBuyerAgreed() {
    assertThatThrownBy(
            () ->
                DeliveryCommitment.record(
                    ORDER,
                    null,
                    new DeliveryCommitment.Agreement(
                        LocalDate.of(2026, 10, 21),
                        FCA,
                        null,
                        null,
                        " ",
                        CommitmentChannel.PHONE,
                        NOW),
                    ACTOR,
                    NOW))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("buyer agreed");
    assertThatThrownBy(
            () ->
                DeliveryCommitment.record(
                    ORDER,
                    null,
                    new DeliveryCommitment.Agreement(
                        LocalDate.of(2026, 10, 21),
                        FCA,
                        null,
                        null,
                        "Jane Smith",
                        CommitmentChannel.PHONE,
                        NOW.plusSeconds(3600)),
                    ACTOR,
                    NOW))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("later than now");
  }
}
