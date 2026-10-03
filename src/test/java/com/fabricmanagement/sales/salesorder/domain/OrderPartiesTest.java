package com.fabricmanagement.sales.salesorder.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.sales.common.exception.OrderDomainException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** ADR-0014 D4, D7, D9: parties, the requested date with its event and the release policy. */
class OrderPartiesTest {

  private static final UUID CUSTOMER = UUID.randomUUID();
  private static final UUID OTHER = UUID.randomUUID();
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);
  private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

  private static SalesOrder draft() {
    return SalesOrder.builder()
        .tradingPartnerId(CUSTOMER)
        .orderNumber("SO-1")
        .orderType(OrderType.SALES)
        .build();
  }

  private static PartySnapshot producer() {
    return PartySnapshot.of("North Mill Garments", "Ann Lee", "ann@northmill.example", null);
  }

  private static AddressSnapshot address() {
    return AddressSnapshot.of("1 Mill Lane", null, "Leeds", null, "LS1 1AA", "gb", null);
  }

  // ─── Party references ──────────────────────────────────────────────────────

  @Test
  void anUndecidedRoleCarriesNothing() {
    assertThat(PartyReference.of(null, null, null, CUSTOMER, "consignee"))
        .isEqualTo(PartyReference.NONE);
    assertThatThrownBy(() -> PartyReference.of(null, OTHER, null, CUSTOMER, "consignee"))
        .isInstanceOf(OrderDomainException.class);
  }

  @Test
  void theCustomerChosenAsAnotherPartnerIsRejected() {
    assertThatThrownBy(
            () -> PartyReference.of(PartyMode.PARTNER, CUSTOMER, null, CUSTOMER, "consignee"))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("choose the customer");
  }

  @Test
  void anUnregisteredPartyNeedsItsNameAndNoPartnerRecord() {
    assertThatThrownBy(() -> PartyReference.of(PartyMode.SNAPSHOT, null, null, CUSTOMER, "x"))
        .isInstanceOf(OrderDomainException.class);
    assertThatThrownBy(
            () -> PartyReference.of(PartyMode.SNAPSHOT, OTHER, producer(), CUSTOMER, "x"))
        .isInstanceOf(OrderDomainException.class);
    PartyReference value = PartyReference.of(PartyMode.SNAPSHOT, null, producer(), CUSTOMER, "x");
    assertThat(value.snapshot().getName()).isEqualTo("North Mill Garments");
  }

  @Test
  void aDraftKeepsAPartialAddressAndChecksTheFormOfWhatWasEntered() {
    assertThat(address().getCountryCode()).isEqualTo("GB");
    assertThat(address().isComplete()).isTrue();

    AddressSnapshot cityOnly = AddressSnapshot.of(null, null, "Manchester", null, null, null, null);
    assertThat(cityOnly.getCity()).isEqualTo("Manchester");
    assertThat(cityOnly.isComplete()).isFalse();

    assertThat(AddressSnapshot.of(" ", null, null, null, null, null, null)).isNull();
    assertThatThrownBy(() -> AddressSnapshot.of(null, null, "Leeds", null, null, "GBR", null))
        .isInstanceOf(OrderDomainException.class);
  }

  // ─── Bill-to ───────────────────────────────────────────────────────────────

  @Test
  void invoicingTheCustomerNeedsNoRelationshipOrReason() {
    PartyReference customer = PartyReference.of(PartyMode.CUSTOMER, null, null, CUSTOMER, "b");
    BillTo value = BillTo.of(customer, address(), null, null);
    assertThat(value.differsFromCustomer()).isFalse();
    assertThatThrownBy(() -> BillTo.of(customer, null, BillToRelationship.AGENT, "agent"))
        .isInstanceOf(OrderDomainException.class);
  }

  @Test
  void invoicingAnotherEntityRecordsTheRelationshipAndTheReason() {
    PartyReference group = PartyReference.of(PartyMode.PARTNER, OTHER, null, CUSTOMER, "b");
    assertThatThrownBy(() -> BillTo.of(group, null, null, null))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("relates to the customer");
    BillTo value =
        BillTo.of(group, null, BillToRelationship.GROUP_COMPANY, " Group buying office ");
    assertThat(value.differsFromCustomer()).isTrue();
    assertThat(value.reason()).isEqualTo("Group buying office");
  }

  @Test
  void billToIsCommercialContentAndChangesOnlyInTheDraft() {
    SalesOrder order = draft();
    BillTo group =
        BillTo.of(
            PartyReference.of(PartyMode.PARTNER, OTHER, null, CUSTOMER, "b"),
            address(),
            BillToRelationship.GROUP_COMPANY,
            "Group buying office");
    order.applyBillTo(group);
    assertThat(order.getBillTo()).isEqualTo(group);

    ReflectionTestUtils.setField(order, "flowStage", OrderFlowStage.IN_PLANNING);
    assertThatThrownBy(() -> order.applyBillTo(BillTo.NONE))
        .isInstanceOf(OrderDomainException.class);
  }

  // ─── Requested date ────────────────────────────────────────────────────────

  @Test
  void aRequestedDateNamesItsEventAndNotRequestedHasNoDay() {
    assertThatThrownBy(() -> RequestedDate.of(RequestedDateStatus.REQUESTED, TODAY, null, null))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("refers to");
    assertThatThrownBy(() -> RequestedDate.of(RequestedDateStatus.NOT_REQUESTED, TODAY, null, null))
        .isInstanceOf(OrderDomainException.class);
    assertThatThrownBy(
            () -> RequestedDate.of(RequestedDateStatus.NOT_REQUESTED, null, null, "Manchester"))
        .isInstanceOf(OrderDomainException.class);
    assertThatThrownBy(() -> RequestedDate.of(null, TODAY, null, null))
        .isInstanceOf(OrderDomainException.class);
    RequestedDate atProducer =
        RequestedDate.of(
            RequestedDateStatus.REQUESTED,
            TODAY,
            RequestedDeliveryEvent.RECEIVED_BY_CONSIGNEE,
            " Producer's warehouse, Manchester ");
    assertThat(atProducer.place()).isEqualTo("Producer's warehouse, Manchester");
  }

  @Test
  void anUnstatedMeaningIsUnknownNotDifferent() {
    assertThat(RequestedDeliveryEvent.UNSPECIFIED.compareWith(DeliveryEvent.HANDED_TO_CARRIER))
        .isEqualTo(EventComparison.UNKNOWN);
    assertThat(RequestedDeliveryEvent.HANDED_TO_CARRIER.compareWith(null))
        .isEqualTo(EventComparison.UNKNOWN);
    assertThat(
            RequestedDeliveryEvent.RECEIVED_BY_CONSIGNEE.compareWith(
                DeliveryEvent.HANDED_TO_CARRIER))
        .isEqualTo(EventComparison.DIFFERENT);
    assertThat(
            RequestedDeliveryEvent.HANDED_TO_CARRIER.compareWith(DeliveryEvent.HANDED_TO_CARRIER))
        .isEqualTo(EventComparison.SAME);
  }

  @Test
  void aDateFromTheOldFormIsRequestedWithItsMeaningNotStated() {
    SalesOrder order = draft();
    order.initialiseRequestedDate(TODAY);
    assertThat(order.getRequestedDate())
        .isEqualTo(
            new RequestedDate(
                RequestedDateStatus.REQUESTED, TODAY, RequestedDeliveryEvent.UNSPECIFIED, null));
  }

  @Test
  void theOldFormKeepsAnEventChosenEarlierAndAnExplicitNotRequested() {
    SalesOrder order = draft();
    order.applyRequestedDate(
        RequestedDate.of(
            RequestedDateStatus.REQUESTED,
            TODAY,
            RequestedDeliveryEvent.RECEIVED_BY_CONSIGNEE,
            "Producer's warehouse"));
    order.syncLegacyRequestedDate(TODAY.plusDays(3));
    assertThat(order.getRequestedDate().event())
        .isEqualTo(RequestedDeliveryEvent.RECEIVED_BY_CONSIGNEE);
    assertThat(order.getRequestedDate().place()).isEqualTo("Producer's warehouse");
    assertThat(order.getRequestedDate().date()).isEqualTo(TODAY.plusDays(3));

    order.applyRequestedDate(RequestedDate.NOT_REQUESTED);
    order.syncLegacyRequestedDate(null);
    assertThat(order.getRequestedDate()).isEqualTo(RequestedDate.NOT_REQUESTED);
  }

  @Test
  void clearingTheDateInTheOldFormMakesItUnknownAgain() {
    SalesOrder order = draft();
    order.initialiseRequestedDate(TODAY);
    order.syncLegacyRequestedDate(null);
    assertThat(order.getRequestedDate()).isEqualTo(RequestedDate.UNKNOWN);
  }

  // ─── Release policy ────────────────────────────────────────────────────────

  @Test
  void releaseTogetherIsCommercialContent() {
    SalesOrder order = draft();
    order.applyReleaseTogether(true);
    assertThat(order.isReleaseTogether()).isTrue();
    ReflectionTestUtils.setField(order, "flowStage", OrderFlowStage.AWAITING_CUSTOMER_APPROVAL);
    assertThatThrownBy(() -> order.applyReleaseTogether(false))
        .isInstanceOf(OrderDomainException.class);
  }

  // ─── Approver ──────────────────────────────────────────────────────────────

  private static ApprovalAuthority authority(UUID customer, UUID contact, LocalDate until) {
    return ApprovalAuthority.grant(
        customer,
        "Ann Lee",
        contact,
        "ann.lee@example.com",
        ApprovalAuthorityBasis.WRITTEN_MANDATE,
        "Mandate letter 2026-09",
        TODAY.minusDays(10),
        until,
        UUID.randomUUID(),
        NOW);
  }

  @Test
  void onlyACustomerContactWithAValidAuthorityBecomesTheApprover() {
    SalesOrder order = draft();
    UUID contact = UUID.randomUUID();

    order.designateApprover(authority(CUSTOMER, contact, null), TODAY);
    assertThat(order.getApproverContactId()).isEqualTo(contact);

    assertThatThrownBy(() -> order.designateApprover(authority(OTHER, contact, null), TODAY))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("this order's customer");
    assertThatThrownBy(
            () -> order.designateApprover(authority(CUSTOMER, contact, TODAY.minusDays(1)), TODAY))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("valid today");
  }

  @Test
  void aRevokedAuthorityNoLongerQualifies() {
    ApprovalAuthority value = authority(CUSTOMER, UUID.randomUUID(), null);
    value.revoke(UUID.randomUUID(), "Left the company", NOW);
    assertThat(value.isActiveOn(TODAY)).isFalse();
    assertThatThrownBy(() -> draft().designateApprover(value, TODAY))
        .isInstanceOf(OrderDomainException.class);
  }

  @Test
  void anAuthorityKeepsTheAddressItWasGrantedForAndNoOther() {
    ApprovalAuthority value = authority(CUSTOMER, UUID.randomUUID(), null);

    assertThat(value.getAuthorisedEmail()).isEqualTo("ann.lee@example.com");
    // The same mailbox, written differently on the card, is still the authorised one.
    assertThat(value.isAddressedAt(" Ann.Lee@Example.com ")).isTrue();
    // Another address, or the contact point removed from the card, reaches nobody.
    assertThat(value.isAddressedAt("ann.lee@another-domain.example")).isFalse();
    assertThat(value.isAddressedAt(null)).isFalse();
  }

  @Test
  void aChangedContactPointEndsTheAuthorityForGood() {
    ApprovalAuthority value = authority(CUSTOMER, UUID.randomUUID(), null);

    value.endForContactChange(ApprovalAuthorityEnd.CONTACT_ADDRESS_CHANGED, UUID.randomUUID(), NOW);

    assertThat(value.isRevoked()).isTrue();
    assertThat(value.endedByContactChange()).isTrue();
    assertThat(value.getRevocationCause()).isEqualTo(ApprovalAuthorityEnd.CONTACT_ADDRESS_CHANGED);
    assertThat(value.getRevocationReason()).contains("changed on the customer's card");
    assertThat(value.isActiveOn(TODAY)).isFalse();
    // The card reading the authorised address again does not bring it back.
    assertThat(value.isAddressedAt("ann.lee@example.com")).isTrue();
    assertThat(value.isActiveOn(TODAY)).isFalse();
    assertThatThrownBy(() -> draft().designateApprover(value, TODAY))
        .isInstanceOf(OrderDomainException.class);
  }

  @Test
  void anAuthorityEndsOnceAndKeepsItsFirstEnding() {
    ApprovalAuthority revoked = authority(CUSTOMER, UUID.randomUUID(), null);
    revoked.revoke(UUID.randomUUID(), "Left the company", NOW);
    revoked.endForContactChange(
        ApprovalAuthorityEnd.CONTACT_ADDRESS_CHANGED, UUID.randomUUID(), NOW.plusSeconds(60));

    assertThat(revoked.getRevocationCause()).isEqualTo(ApprovalAuthorityEnd.REVOKED);
    assertThat(revoked.getRevokedAt()).isEqualTo(NOW);
    assertThat(revoked.endedByContactChange()).isFalse();

    ApprovalAuthority removed = authority(CUSTOMER, UUID.randomUUID(), null);
    removed.endForContactChange(ApprovalAuthorityEnd.CONTACT_REMOVED, UUID.randomUUID(), NOW);
    assertThatThrownBy(() -> removed.revoke(UUID.randomUUID(), "Again", NOW))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo("APPROVAL_AUTHORITY_REVOKED"));
    assertThat(removed.getRevocationCause()).isEqualTo(ApprovalAuthorityEnd.CONTACT_REMOVED);
  }

  @Test
  void anAuthorityNeedsTheRepresentativesEmailAddress() {
    for (String address : new String[] {null, " ", "ann.lee"}) {
      assertThatThrownBy(
              () ->
                  ApprovalAuthority.grant(
                      CUSTOMER,
                      "Ann Lee",
                      UUID.randomUUID(),
                      address,
                      ApprovalAuthorityBasis.WRITTEN_MANDATE,
                      "Mandate letter 2026-09",
                      TODAY,
                      null,
                      UUID.randomUUID(),
                      NOW))
          .isInstanceOfSatisfying(
              OrderDomainException.class,
              exception ->
                  assertThat(exception.getErrorCode()).isEqualTo("APPROVER_CONTACT_NOT_EMAIL"));
    }
  }

  @Test
  void theApproverDoesNotChangeWhileTheCustomersApprovalIsOut() {
    SalesOrder order = draft();
    ReflectionTestUtils.setField(order, "flowStage", OrderFlowStage.AWAITING_CUSTOMER_APPROVAL);
    assertThatThrownBy(order::clearApprover)
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("withdraw");
  }

  @Test
  void anAuthorityRestsOnABasisAndIsRevokedOnceWithAReason() {
    assertThatThrownBy(
            () ->
                ApprovalAuthority.grant(
                    CUSTOMER,
                    "Ann Lee",
                    UUID.randomUUID(),
                    "ann.lee@example.com",
                    ApprovalAuthorityBasis.OTHER,
                    "  ",
                    TODAY,
                    null,
                    UUID.randomUUID(),
                    NOW))
        .isInstanceOf(OrderDomainException.class);
    assertThatThrownBy(
            () ->
                ApprovalAuthority.grant(
                    CUSTOMER,
                    " ",
                    UUID.randomUUID(),
                    "ann.lee@example.com",
                    ApprovalAuthorityBasis.OTHER,
                    "Register",
                    TODAY,
                    null,
                    UUID.randomUUID(),
                    NOW))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("representative");
    assertThatThrownBy(
            () ->
                ApprovalAuthority.grant(
                    CUSTOMER,
                    "Ann Lee",
                    UUID.randomUUID(),
                    "ann.lee@example.com",
                    ApprovalAuthorityBasis.OTHER,
                    "Register",
                    TODAY,
                    TODAY.minusDays(1),
                    UUID.randomUUID(),
                    NOW))
        .isInstanceOf(OrderDomainException.class);

    ApprovalAuthority value = authority(CUSTOMER, UUID.randomUUID(), null);
    assertThatThrownBy(() -> value.revoke(UUID.randomUUID(), " ", NOW))
        .isInstanceOf(OrderDomainException.class);
    value.revoke(UUID.randomUUID(), "Mandate withdrawn", NOW);
    assertThatThrownBy(() -> value.revoke(UUID.randomUUID(), "Again", NOW))
        .isInstanceOf(OrderDomainException.class);
  }
}
