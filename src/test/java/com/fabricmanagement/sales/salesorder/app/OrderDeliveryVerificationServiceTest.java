package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.DeliveryVerificationPolicy;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.DefaultSource;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTermSetting;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerms;
import com.fabricmanagement.sales.salesorder.domain.OrderDelivery;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.OrderType;
import com.fabricmanagement.sales.salesorder.domain.PartyMode;
import com.fabricmanagement.sales.salesorder.domain.PartyReference;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.DeliveriesView;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.DeliveryContentInput;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.DeliveryCorrection;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.SaveDeliveryRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.VerifyDeliveryRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.AddressInput;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.PartyInput;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderDeliveryRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderLineAllocationRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * CONSIGNEE-1 S2: what a save gives a delivery, and what verifying does to it, its partner and its
 * address card. The sales scope, the version lock and the database are covered elsewhere.
 */
@ExtendWith(MockitoExtension.class)
class OrderDeliveryVerificationServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ACTOR = UUID.randomUUID();
  private static final UUID CUSTOMER = UUID.randomUUID();
  private static final UUID PARTNER = UUID.randomUUID();
  private static final UUID CARD = UUID.randomUUID();
  private static final long VERSION = 3L;

  @Mock private OrderDraftAccess access;
  @Mock private OrderDeliveryRepository deliveries;
  @Mock private OrderLineAllocationRepository allocations;
  @Mock private SalesOrderLineRepository lines;
  @Mock private SalesOrderRevision revision;
  @Mock private DeliveryVerificationPolicy policy;
  @Mock private OrderWorkService work;
  @InjectMocks private OrderDeliveryService service;

  private SalesOrder order;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    TenantContext.setCurrentUserId(ACTOR);
    order = order(null);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private static SalesOrder order(DeliveryTerm term) {
    SalesOrder value =
        SalesOrder.builder()
            .tradingPartnerId(CUSTOMER)
            .orderNumber("SO-1")
            .orderType(OrderType.SALES)
            .build();
    ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(value, "version", VERSION);
    if (term != null) {
      value.applyDeliveryTerms(DeliveryTerms.of(term, "Mill gate", null));
      value.applyDeliveryTermStatus(null, null);
    }
    return value;
  }

  // ─── what a save gives the delivery ───────────────────────────────────────

  private static PartyInput customer() {
    return new PartyInput(PartyMode.CUSTOMER, null, null);
  }

  private static PartyInput partner() {
    return new PartyInput(PartyMode.PARTNER, PARTNER, null);
  }

  private static AddressInput typedAddress() {
    return new AddressInput("1 Mill Lane", null, "Leeds", null, "LS1 1AA", "GB", null);
  }

  private static AddressInput addressFromCard() {
    return new AddressInput("1 Mill Lane", null, "Leeds", null, "LS1 1AA", "GB", CARD);
  }

  private static DeliveryContentInput content(
      PartyInput consignee, AddressInput shipTo, String note) {
    return new DeliveryContentInput(consignee, shipTo, null, null, null, null, null, false, note);
  }

  private OrderDelivery saved(DeliveryContentInput input) {
    when(access.writable(order.getId(), VERSION, ACTOR)).thenReturn(order);
    service.create(order.getId(), new SaveDeliveryRequest(VERSION, input), ACTOR);
    ArgumentCaptor<OrderDelivery> captor = ArgumentCaptor.forClass(OrderDelivery.class);
    verify(deliveries).save(captor.capture());
    return captor.getValue();
  }

  @Test
  void whatAUserWithoutThePermissionSavesWaitsForAVerifier() {
    OrderDelivery delivery = saved(content(customer(), typedAddress(), null));

    assertThat(delivery.getVerification().isVerified()).isFalse();
    assertThat(delivery.getVerification().getVerifiedBy()).isNull();
  }

  @Test
  void whatAVerifierSavesCompleteIsVerifiedByThem() {
    when(policy.actorMayVerify()).thenReturn(true);

    OrderDelivery delivery = saved(content(customer(), typedAddress(), null));

    assertThat(delivery.getVerification().isVerified()).isTrue();
    assertThat(delivery.getVerification().getVerifiedBy()).isEqualTo(ACTOR);
    assertThat(delivery.getVerification().getVerifiedAt()).isNotNull();
    assertThat(delivery.isShipToVerifiedAtCopy()).isFalse();
  }

  @Test
  void aNoteOnlyDeliveryIsUnverifiedByDefinitionEvenFromAVerifier() {
    when(policy.actorMayVerify()).thenReturn(true);

    OrderDelivery delivery = saved(content(customer(), null, "Merter workshop, Ahmet confirms"));

    assertThat(delivery.getNote()).isEqualTo("Merter workshop, Ahmet confirms");
    assertThat(delivery.getVerification().isVerified()).isFalse();
  }

  @Test
  void aVerifierSavingNoConsigneeOrAPartialAddressLeavesItUnverified() {
    when(policy.actorMayVerify()).thenReturn(true);

    OrderDelivery delivery = saved(content(null, typedAddress(), null));

    assertThat(delivery.getVerification().isVerified()).isFalse();
  }

  @Test
  void aVerifiersDeliveryToAnUnverifiedPartnerWaitsForTheVerifyStep() {
    when(policy.actorMayVerify()).thenReturn(true);
    when(access.partnerVerified(PARTNER)).thenReturn(false);

    OrderDelivery delivery = saved(content(partner(), typedAddress(), null));

    assertThat(delivery.getVerification().isVerified()).isFalse();
  }

  @Test
  void aVerifiersDeliveryToAVerifiedPartnerIsVerified() {
    when(policy.actorMayVerify()).thenReturn(true);
    when(access.partnerVerified(PARTNER)).thenReturn(true);

    OrderDelivery delivery = saved(content(partner(), typedAddress(), null));

    assertThat(delivery.getVerification().isVerified()).isTrue();
  }

  @Test
  void aVerifiersDeliveryCopiedFromAnUnverifiedCardWaitsForTheVerifyStep() {
    when(policy.actorMayVerify()).thenReturn(true);
    when(access.cardVerified(CARD)).thenReturn(false);

    OrderDelivery delivery = saved(content(customer(), addressFromCard(), null));

    assertThat(delivery.getVerification().isVerified()).isFalse();
    assertThat(delivery.isShipToVerifiedAtCopy()).isFalse();
  }

  @Test
  void aDeliveryCopiedFromAVerifiedCardRemembersTheCardWasVerified() {
    when(policy.actorMayVerify()).thenReturn(true);
    when(access.cardVerified(CARD)).thenReturn(true);

    OrderDelivery delivery = saved(content(customer(), addressFromCard(), null));

    assertThat(delivery.getVerification().isVerified()).isTrue();
    assertThat(delivery.isShipToVerifiedAtCopy()).isTrue();
  }

  @Test
  void aNonVerifiersCopyFromAVerifiedCardStillWaitsButRemembersTheCard() {
    when(access.cardVerified(CARD)).thenReturn(true);

    OrderDelivery delivery = saved(content(customer(), addressFromCard(), null));

    assertThat(delivery.getVerification().isVerified()).isFalse();
    assertThat(delivery.isShipToVerifiedAtCopy()).isTrue();
  }

  @Test
  void aNonVerifiersEditEndsTheVerificationOfAVerifiedDelivery() {
    OrderDelivery delivery = existing(content(customer(), typedAddress(), null));
    delivery.verify(order, ACTOR, Instant.now());
    assertThat(delivery.getVerification().isVerified()).isTrue();
    when(access.writable(order.getId(), VERSION, ACTOR)).thenReturn(order);

    service.update(
        order.getId(),
        delivery.getId(),
        new SaveDeliveryRequest(VERSION, content(customer(), typedAddress(), "new note")),
        ACTOR);

    assertThat(delivery.getVerification().isVerified()).isFalse();
    assertThat(delivery.getNote()).isEqualTo("new note");
  }

  // ─── verifying ────────────────────────────────────────────────────────────

  private OrderDelivery existing(DeliveryContentInput input) {
    PartyReference consignee =
        input.consignee() == null
            ? PartyReference.NONE
            : input.consignee().toReference(CUSTOMER, "consignee");
    OrderDelivery delivery =
        OrderDelivery.create(
            order.getId(),
            1,
            new OrderDelivery.Content(
                consignee,
                input.shipTo() == null ? null : input.shipTo().toSnapshot(),
                null,
                null,
                null,
                null,
                null,
                false,
                input.note()));
    ReflectionTestUtils.setField(delivery, "id", UUID.randomUUID());
    lenient()
        .when(
            deliveries.findByTenantIdAndSalesOrderIdAndId(TENANT, order.getId(), delivery.getId()))
        .thenReturn(Optional.of(delivery));
    lenient().when(access.verifiable(order.getId(), VERSION)).thenReturn(order);
    return delivery;
  }

  private DeliveriesView verifyPlain(OrderDelivery delivery) {
    return service.verify(
        order.getId(), delivery.getId(), new VerifyDeliveryRequest(VERSION, null), ACTOR);
  }

  @Test
  void verifyingConfirmsTheDeliveryAndItsPartnerAndAddressCard() {
    OrderDelivery delivery = existing(content(partner(), addressFromCard(), null));

    DeliveriesView view = verifyPlain(delivery);

    assertThat(delivery.getVerification().isVerified()).isTrue();
    assertThat(delivery.getVerification().getVerifiedBy()).isEqualTo(ACTOR);
    verify(access).verifyPartner(PARTNER, ACTOR);
    verify(access).verifyCard(CARD, ACTOR);
    verify(access).flush();
    assertThat(view.orderId()).isEqualTo(order.getId());
  }

  @Test
  void anAddressTypedOnTheOrderForTheCustomerHasNothingToCascadeTo() {
    OrderDelivery delivery = existing(content(customer(), typedAddress(), null));

    verifyPlain(delivery);

    assertThat(delivery.getVerification().isVerified()).isTrue();
    verify(access, never()).verifyPartner(any(), any());
    verify(access, never()).verifyCard(any(), any());
  }

  @Test
  void aPlainVerificationChangesNoOrderContentSoTheVersionStays() {
    OrderDelivery delivery = existing(content(customer(), typedAddress(), null));

    verifyPlain(delivery);

    verify(revision, never()).linesChanged(any());
  }

  @Test
  void aNoteOnlyDeliveryIsRefusedAndNothingIsCascaded() {
    OrderDelivery delivery = existing(content(partner(), null, "Merter workshop"));

    assertThatThrownBy(() -> verifyPlain(delivery))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo("DELIVERY_NOTE_ONLY"));
    assertThat(delivery.getVerification().isVerified()).isFalse();
    verify(access, never()).verifyPartner(any(), any());
    verify(access, never()).verifyCard(any(), any());
  }

  @Test
  void aNoteOnlyDeliveryIsVerifiedWhenTheCustomerCollects() {
    order = order(DeliveryTerm.EXW);
    OrderDelivery delivery = existing(content(customer(), null, "Customer collects"));

    verifyPlain(delivery);

    assertThat(delivery.getVerification().isVerified()).isTrue();
    assertThat(delivery.getShipTo()).isNull();
  }

  @Test
  void aDeliveryWithItsOwnExwTermNeedsNoAddressEvenWhenTheOrderTermDiffers() {
    order = order(DeliveryTerm.DAP);
    OrderDelivery delivery = existing(content(customer(), null, "Customer collects"));
    delivery.revise(
        new OrderDelivery.Content(
            PartyReference.of(PartyMode.CUSTOMER, null, null, CUSTOMER, "consignee"),
            null,
            DefaultSource.OVERRIDE,
            DeliveryTermSetting.of(
                DeliveryTerms.of(DeliveryTerm.EXW, "Mill gate", null), null, null),
            null,
            null,
            null,
            false,
            "Customer collects"));

    verifyPlain(delivery);

    assertThat(delivery.getVerification().isVerified()).isTrue();
  }

  @Test
  void aStaleVersionIsRejectedBeforeAnythingIsVerified() {
    OrderDelivery delivery = existing(content(partner(), addressFromCard(), null));
    when(access.verifiable(order.getId(), 2L))
        .thenThrow(new ObjectOptimisticLockingFailureException(SalesOrder.class, order.getId()));

    assertThatThrownBy(
            () ->
                service.verify(
                    order.getId(), delivery.getId(), new VerifyDeliveryRequest(2L, null), ACTOR))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);

    assertThat(delivery.getVerification().isVerified()).isFalse();
    verify(access, never()).verifyPartner(any(), any());
    verify(access, never()).verifyCard(any(), any());
  }

  @Test
  void aDeliveryOfAnotherOrderIsNotFound() {
    when(access.verifiable(order.getId(), VERSION)).thenReturn(order);
    UUID other = UUID.randomUUID();
    when(deliveries.findByTenantIdAndSalesOrderIdAndId(TENANT, order.getId(), other))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.verify(
                    order.getId(), other, new VerifyDeliveryRequest(VERSION, null), ACTOR))
        .isInstanceOf(NotFoundException.class);
  }

  // ─── verifying with a correction ──────────────────────────────────────────

  @Test
  void aCorrectionReplacesTheAddressMovesTheVersionAndVerifiesTheCorrectedCard() {
    UUID correctedCard = UUID.randomUUID();
    OrderDelivery delivery = existing(content(customer(), typedAddress(), null));
    when(access.cardVerified(correctedCard)).thenReturn(false);

    service.verify(
        order.getId(),
        delivery.getId(),
        new VerifyDeliveryRequest(
            VERSION,
            new DeliveryCorrection(
                customer(),
                new AddressInput("9 New St", null, "York", null, null, "GB", correctedCard),
                "corrected by planning")),
        ACTOR);

    assertThat(delivery.getShipTo().getLine1()).isEqualTo("9 New St");
    assertThat(delivery.getNote()).isEqualTo("corrected by planning");
    assertThat(delivery.getVerification().isVerified()).isTrue();
    assertThat(delivery.isShipToVerifiedAtCopy()).isFalse();
    verify(access).verifyCard(correctedCard, ACTOR);
    verify(revision).linesChanged(order);
  }

  @Test
  void aCorrectionNamingAnUnregisteredPartnerIsRefused() {
    OrderDelivery delivery = existing(content(customer(), typedAddress(), null));
    org.mockito.Mockito.doThrow(
            OrderDomainException.rule("PARTNER_NOT_FOUND", "That partner is not registered"))
        .when(access)
        .requireRegistered(any(PartyReference.class));

    assertThatThrownBy(
            () ->
                service.verify(
                    order.getId(),
                    delivery.getId(),
                    new VerifyDeliveryRequest(
                        VERSION, new DeliveryCorrection(partner(), typedAddress(), null)),
                    ACTOR))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo("PARTNER_NOT_FOUND"));
    verify(revision, never()).linesChanged(any());
  }

  @Test
  void aCorrectionIsRefusedOnceTheCustomerApprovedButAPlainVerificationIsNot() {
    OrderDelivery delivery = existing(content(customer(), typedAddress(), null));
    ReflectionTestUtils.setField(order, "flowStage", OrderFlowStage.CUSTOMER_APPROVED);

    assertThatThrownBy(
            () ->
                service.verify(
                    order.getId(),
                    delivery.getId(),
                    new VerifyDeliveryRequest(
                        VERSION, new DeliveryCorrection(customer(), typedAddress(), "x")),
                    ACTOR))
        .isInstanceOf(OrderDomainException.class);
    assertThat(delivery.getVerification().isVerified()).isFalse();

    verifyPlain(delivery);

    assertThat(delivery.getVerification().isVerified()).isTrue();
  }

  @Test
  void aCorrectionThatStillLacksAnAddressIsRefusedAfterTheCorrectionIsApplied() {
    OrderDelivery delivery = existing(content(customer(), typedAddress(), null));

    assertThatThrownBy(
            () ->
                service.verify(
                    order.getId(),
                    delivery.getId(),
                    new VerifyDeliveryRequest(
                        VERSION, new DeliveryCorrection(customer(), null, "still only a note")),
                    ACTOR))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo("DELIVERY_NOTE_ONLY"));
    verify(revision, never()).linesChanged(any());
  }
}
