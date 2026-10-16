package com.fabricmanagement.sales.salesorder.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.domain.vo.DeliveryVerificationStatus;
import com.fabricmanagement.common.domain.vo.Verification;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * CONSIGNEE-1 C5/C7/C9: a delivery is verified only with a decided consignee and a complete
 * address; a note is not an address; EXW (the customer collects) needs no address.
 */
class OrderDeliveryVerificationTest {

  private static final UUID CUSTOMER = UUID.randomUUID();
  private static final UUID VERIFIER = UUID.randomUUID();
  private static final Instant AT = Instant.parse("2026-10-16T09:00:00Z");

  private static SalesOrder order(DeliveryTerm term) {
    SalesOrder value =
        SalesOrder.builder()
            .tradingPartnerId(CUSTOMER)
            .orderNumber("SO-1")
            .orderType(OrderType.SALES)
            .build();
    ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
    if (term != null) {
      value.applyDeliveryTerms(DeliveryTerms.of(term, "Mill gate", null));
      value.applyDeliveryTermStatus(null, null);
    }
    return value;
  }

  private static PartyReference customer() {
    return PartyReference.of(PartyMode.CUSTOMER, null, null, CUSTOMER, "consignee");
  }

  private static AddressSnapshot completeAddress() {
    return AddressSnapshot.of("1 Mill Lane", null, "Leeds", null, "LS1 1AA", "GB", null);
  }

  private static OrderDelivery delivery(
      SalesOrder order, PartyReference consignee, AddressSnapshot shipTo, String note) {
    return OrderDelivery.create(
        order.getId(),
        1,
        new OrderDelivery.Content(consignee, shipTo, null, null, null, null, null, false, note));
  }

  @Test
  void aNewDeliveryIsUnverified() {
    SalesOrder order = order(null);

    OrderDelivery delivery = delivery(order, customer(), completeAddress(), null);

    assertThat(delivery.getVerification().getStatus())
        .isEqualTo(DeliveryVerificationStatus.UNVERIFIED);
    assertThat(delivery.isShipToVerifiedAtCopy()).isFalse();
  }

  @Test
  void verifyingRecordsWhoAndWhen() {
    SalesOrder order = order(null);
    OrderDelivery delivery = delivery(order, customer(), completeAddress(), null);

    delivery.verify(order, VERIFIER, AT);

    assertThat(delivery.getVerification().isVerified()).isTrue();
    assertThat(delivery.getVerification().getVerifiedBy()).isEqualTo(VERIFIER);
    assertThat(delivery.getVerification().getVerifiedAt()).isEqualTo(AT);
  }

  @Test
  void changingWhatTheDeliveryHoldsEndsItsVerification() {
    SalesOrder order = order(null);
    OrderDelivery delivery = delivery(order, customer(), completeAddress(), null);
    delivery.verify(order, VERIFIER, AT);
    delivery.recordVerification(delivery.getVerification(), true);

    delivery.revise(
        new OrderDelivery.Content(
            customer(),
            AddressSnapshot.of("2 Dock Road", null, "Hull", null, null, "GB", null),
            null,
            null,
            null,
            null,
            null,
            false,
            null));

    assertThat(delivery.getVerification().isVerified()).isFalse();
    assertThat(delivery.getVerification().getVerifiedBy()).isNull();
    assertThat(delivery.isShipToVerifiedAtCopy()).isFalse();
  }

  @Test
  void aDeliveryWithoutADecidedConsigneeCannotBeVerified() {
    SalesOrder order = order(null);
    OrderDelivery delivery = delivery(order, PartyReference.NONE, completeAddress(), null);

    assertThat(delivery.verificationBlocker(order))
        .contains(OrderDelivery.VerificationBlocker.CONSIGNEE_MISSING);
    assertThatThrownBy(() -> delivery.verify(order, VERIFIER, AT))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo("DELIVERY_CONSIGNEE_MISSING"));
    assertThat(delivery.getVerification().isVerified()).isFalse();
  }

  @Test
  void aNoteIsNotAnAddress() {
    SalesOrder order = order(DeliveryTerm.FCA);
    OrderDelivery delivery =
        delivery(order, customer(), null, "The Merter workshop - Ahmet to confirm");

    assertThat(delivery.isNoteOnly()).isTrue();
    assertThatThrownBy(() -> delivery.verify(order, VERIFIER, AT))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo("DELIVERY_NOTE_ONLY"));
    assertThat(delivery.getVerification().isVerified()).isFalse();
  }

  @Test
  void aNoteBesideACompleteAddressIsNotNoteOnly() {
    SalesOrder order = order(null);
    OrderDelivery delivery =
        delivery(order, customer(), completeAddress(), "Gate 3, ask for Ahmet");

    assertThat(delivery.isNoteOnly()).isFalse();
    assertThat(delivery.verificationBlocker(order)).isEmpty();
  }

  @Test
  void aPartialAddressWithoutANoteIsIncomplete() {
    SalesOrder order = order(null);
    OrderDelivery delivery =
        delivery(
            order,
            customer(),
            AddressSnapshot.of(null, null, "Manchester", null, null, null, null),
            null);

    assertThatThrownBy(() -> delivery.verify(order, VERIFIER, AT))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo("DELIVERY_ADDRESS_INCOMPLETE"));
  }

  @Test
  void aNoteOnlyDeliveryCanBeVerifiedWhenTheOrderTermIsExw() {
    SalesOrder order = order(DeliveryTerm.EXW);
    OrderDelivery delivery = delivery(order, customer(), null, "Customer collects, Ahmet calls");

    delivery.verify(order, VERIFIER, AT);

    assertThat(delivery.getVerification().isVerified()).isTrue();
    assertThat(delivery.getShipTo()).isNull();
  }

  @Test
  void aDeliverysOwnExwTermAlsoNeedsNoAddress() {
    SalesOrder order = order(DeliveryTerm.FCA);
    OrderDelivery delivery =
        OrderDelivery.create(
            order.getId(),
            1,
            new OrderDelivery.Content(
                customer(),
                null,
                DefaultSource.OVERRIDE,
                DeliveryTermSetting.of(
                    DeliveryTerms.of(DeliveryTerm.EXW, "Mill gate", null), null, null),
                null,
                null,
                null,
                false,
                "Customer collects"));

    delivery.verify(order, VERIFIER, AT);

    assertThat(delivery.getVerification().isVerified()).isTrue();
  }

  @Test
  void aDeliveryThatFollowsAnOrderTermOtherThanExwStillNeedsAnAddress() {
    SalesOrder order = order(DeliveryTerm.DAP);
    OrderDelivery delivery = delivery(order, customer(), null, null);

    assertThat(delivery.verificationBlocker(order))
        .contains(OrderDelivery.VerificationBlocker.ADDRESS_INCOMPLETE);
  }

  @Test
  void theNoteIsTrimmedBlankMeansNoneAndTooLongIsRefused() {
    SalesOrder order = order(null);

    assertThat(delivery(order, customer(), null, "  ").getNote()).isNull();
    assertThat(delivery(order, customer(), null, "  workshop  ").getNote()).isEqualTo("workshop");
    assertThatThrownBy(
            () -> delivery(order, customer(), null, "x".repeat(OrderDelivery.MAX_NOTE + 1)))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("note is too long");
  }

  @Test
  void aCorrectionReplacesTheConsigneeTheAddressAndTheNoteOnly() {
    SalesOrder order = order(null);
    OrderDelivery delivery =
        OrderDelivery.create(
            order.getId(),
            1,
            new OrderDelivery.Content(
                customer(), completeAddress(), null, null, null, null, "Road", true, null));
    delivery.verify(order, VERIFIER, AT);

    delivery.correct(
        customer(),
        AddressSnapshot.of("9 New St", null, "York", null, null, "GB", null),
        "corrected");

    assertThat(delivery.getShipTo().getLine1()).isEqualTo("9 New St");
    assertThat(delivery.getNote()).isEqualTo("corrected");
    assertThat(delivery.getTransportPreference()).isEqualTo("Road");
    assertThat(delivery.isShipComplete()).isTrue();
    assertThat(delivery.getVerification().isVerified()).isFalse();
  }

  @Test
  void aSaveRecordsTheStateItDecidedAndHowTheCardStoodAtCopyTime() {
    SalesOrder order = order(null);
    OrderDelivery delivery = delivery(order, customer(), completeAddress(), null);

    delivery.recordVerification(Verification.verifiedBy(VERIFIER, AT), true);

    assertThat(delivery.getVerification().isVerified()).isTrue();
    assertThat(delivery.isShipToVerifiedAtCopy()).isTrue();
    assertThatThrownBy(() -> delivery.recordVerification(null, false))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
