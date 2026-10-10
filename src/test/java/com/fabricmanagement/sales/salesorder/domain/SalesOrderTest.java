package com.fabricmanagement.sales.salesorder.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.sales.common.exception.OrderDomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SalesOrderTest {

  @Test
  void seededDemoOrderIsConfirmedFromDraft() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.DRAFT).build();
    order.confirmSeededDemoOrder();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"DRAFT"},
      mode = EnumSource.Mode.EXCLUDE)
  void seededDemoOrderIsNotConfirmedTwice(OrderStatus status) {
    SalesOrder order = SalesOrder.builder().status(status).build();
    assertThatThrownBy(order::confirmSeededDemoOrder)
        .isInstanceOf(OrderDomainException.class)
        .extracting("httpStatus")
        .isEqualTo(409);
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderFlowStage.class,
      names = {"CUSTOMER_APPROVED"},
      mode = EnumSource.Mode.EXCLUDE)
  void onlyTheCustomersApprovalConfirmsAnOrder(OrderFlowStage stage) {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.DRAFT).flowStage(stage).build();
    assertThatThrownBy(order::confirmByCustomer)
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo("NOT_APPROVED_BY_CUSTOMER"));
    assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT);
  }

  @Test
  void approvedOrderIsConfirmedWithItsTermAgreedByTheCustomer() {
    SalesOrder order =
        SalesOrder.builder()
            .status(OrderStatus.DRAFT)
            .flowStage(OrderFlowStage.CUSTOMER_APPROVED)
            .build();
    order.applyDeliveryTerms(
        DeliveryTerms.of(DeliveryTerm.FCA, "Felixstowe", IncotermsVersion.INCOTERMS_2020));
    order.applyDeliveryTermStatus(null, null);

    order.confirmByCustomer();

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order.getDeliveryTermStatus()).isEqualTo(DeliveryTermStatus.AGREED_BY_CUSTOMER);
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderFlowStage.class,
      names = {
        "AWAITING_PLANNING",
        "IN_PLANNING",
        "PLANNED",
        "AWAITING_INTERNAL_APPROVAL",
        "AWAITING_CUSTOMER_APPROVAL"
      })
  void contentIsLockedWhileWithPlanningOrOutForAnApproval(OrderFlowStage stage) {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.DRAFT).flowStage(stage).build();
    assertThat(order.commercialContentLock()).isEqualTo(SalesOrder.WITH_PLANNING);
    assertThatThrownBy(order::assertCommercialContentEditable)
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("ORDER_WITH_PLANNING"));
  }

  @Test
  void contentIsLockedAfterTheCustomersApproval() {
    SalesOrder approved =
        SalesOrder.builder()
            .status(OrderStatus.CONFIRMED)
            .flowStage(OrderFlowStage.CUSTOMER_APPROVED)
            .build();
    assertThatThrownBy(approved::assertCommercialContentEditable)
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo("ORDER_APPROVED_BY_CUSTOMER"));
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"CONFIRMED", "IN_PROGRESS", "PARTIALLY_SHIPPED", "SHIPPED", "DELIVERED", "ON_HOLD"})
  void aConfirmedOrderIsLockedWhateverItsFlowStage(OrderStatus status) {
    SalesOrder order = SalesOrder.builder().status(status).flowStage(OrderFlowStage.DRAFT).build();
    assertThat(order.commercialContentLock()).isEqualTo(SalesOrder.APPROVED_BY_CUSTOMER);
  }

  @Test
  void aDraftIsEditable() {
    SalesOrder order =
        SalesOrder.builder().status(OrderStatus.DRAFT).flowStage(OrderFlowStage.DRAFT).build();
    assertThat(order.commercialContentLock()).isNull();
    order.assertCommercialContentEditable();
  }

  @Test
  void recordShipmentProgress_partial_setsPartiallyShipped() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.CONFIRMED).build();
    order.recordShipmentProgress(false, true);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_SHIPPED);
  }

  @Test
  void recordShipmentProgress_allShipped_setsShipped() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.CONFIRMED).build();
    order.recordShipmentProgress(true, true);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
  }

  @Test
  void recordShipmentProgress_partiallyShipped_toShipped() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.PARTIALLY_SHIPPED).build();
    order.recordShipmentProgress(true, true);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
  }

  @Test
  void recordShipmentProgress_idempotent_staysPartiallyShipped() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.PARTIALLY_SHIPPED).build();
    order.recordShipmentProgress(false, true);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_SHIPPED);
  }

  @Test
  void recordShipmentProgress_inProgress_setsPartiallyShipped() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.IN_PROGRESS).build();
    order.recordShipmentProgress(false, true);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_SHIPPED);
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"DELIVERED", "CANCELLED", "REJECTED"})
  void recordShipmentProgress_terminal_noop(OrderStatus status) {
    SalesOrder order = SalesOrder.builder().status(status).build();
    order.recordShipmentProgress(true, true);
    assertThat(order.getStatus()).isEqualTo(status);
  }

  @Test
  void recordShipmentProgress_onHold_noop() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.ON_HOLD).build();
    order.recordShipmentProgress(false, true);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.ON_HOLD);
  }

  @Test
  void recordShipmentProgress_noneShipped_noop() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.CONFIRMED).build();
    order.recordShipmentProgress(false, false);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
  }

  @Test
  void markInProgressIfConfirmed_whenConfirmed_setsInProgress() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.CONFIRMED).build();

    boolean changed = order.markInProgressIfConfirmed();

    assertThat(changed).isTrue();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"CONFIRMED"},
      mode = EnumSource.Mode.EXCLUDE)
  void markInProgressIfConfirmed_whenNotConfirmed_isNoop(OrderStatus status) {
    SalesOrder order = SalesOrder.builder().status(status).build();

    boolean changed = order.markInProgressIfConfirmed();

    assertThat(changed).isFalse();
    assertThat(order.getStatus()).isEqualTo(status);
  }

  @Test
  void cancel_whenInProgress_succeeds() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.IN_PROGRESS).build();
    order.cancel();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"PARTIALLY_SHIPPED", "SHIPPED"})
  void cancel_whenPartiallyShippedOrShipped_throws409(OrderStatus status) {
    SalesOrder order = SalesOrder.builder().status(status).build();
    assertThatThrownBy(() -> order.cancel())
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("Cannot cancel order")
        .extracting("httpStatus")
        .isEqualTo(409);
  }

  @Test
  void hold_thenResume_restoresInProgress() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.IN_PROGRESS).build();
    order.hold();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.ON_HOLD);
    order.resume();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
  }

  @Test
  void hold_thenResume_restoresConfirmed() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.CONFIRMED).build();
    order.hold();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.ON_HOLD);
    order.resume();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
  }

  @Test
  void resume_whenNotOnHold_throws409() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.DRAFT).build();
    assertThatThrownBy(() -> order.resume())
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("must be ON_HOLD")
        .extracting("httpStatus")
        .isEqualTo(409);
  }

  @Test
  void reviseRejected_whenRejected_movesToDraft() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.REJECTED).build();
    order.reviseRejected();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT);
  }

  @Test
  void reviseRejected_whenNotRejected_throws409() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.IN_PROGRESS).build();
    assertThatThrownBy(() -> order.reviseRejected())
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("only REJECTED orders can be revised")
        .extracting("httpStatus")
        .isEqualTo(409);
  }

  @Test
  void hold_whenAlreadyOnHold_throws409() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.ON_HOLD).build();
    assertThatThrownBy(() -> order.hold())
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("terminal or already ON_HOLD")
        .extracting("httpStatus")
        .isEqualTo(409);
  }

  @Test
  void reviseRejected_clearsRejectionReason() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.REJECTED).build();
    org.springframework.test.util.ReflectionTestUtils.setField(
        order, "rejectionReason", "Insufficient funds");
    order.reviseRejected();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT);
    assertThat(order.getRejectionReason()).isNull();
  }

  @Test
  void aDeliveryTermIsAProposalUntilTheCustomerApprovesOrAContractFixedIt() {
    SalesOrder order = SalesOrder.builder().orderNumber("SO-1").build();
    order.applyDeliveryTerms(DeliveryTerms.of(DeliveryTerm.FCA, "Bradford mill", null));

    order.applyDeliveryTermStatus(null, null);
    assertThat(order.getDeliveryTermStatus()).isEqualTo(DeliveryTermStatus.PROPOSED);

    // Only the customer's approval agrees a term; it cannot be entered.
    assertThatThrownBy(
            () -> order.applyDeliveryTermStatus(DeliveryTermStatus.AGREED_BY_CUSTOMER, null))
        .isInstanceOf(OrderDomainException.class);
    assertThatThrownBy(
            () -> order.applyDeliveryTermStatus(DeliveryTermStatus.AGREED_BY_CONTRACT, " "))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("contract");

    order.markDeliveryTermAgreedByCustomer();
    assertThat(order.getDeliveryTermStatus()).isEqualTo(DeliveryTermStatus.AGREED_BY_CUSTOMER);

    order.applyDeliveryTermStatus(DeliveryTermStatus.AGREED_BY_CONTRACT, " Frame 2026/14 ");
    order.markDeliveryTermAgreedByCustomer();
    assertThat(order.getDeliveryTermStatus()).isEqualTo(DeliveryTermStatus.AGREED_BY_CONTRACT);
    assertThat(order.getDeliveryContractReference()).isEqualTo("Frame 2026/14");
  }

  @Test
  void withoutATermThereIsNoStatus() {
    SalesOrder order = SalesOrder.builder().orderNumber("SO-1").build();

    assertThatThrownBy(() -> order.applyDeliveryTermStatus(DeliveryTermStatus.PROPOSED, null))
        .isInstanceOf(OrderDomainException.class);
    assertThatThrownBy(order::markDeliveryTermAgreedByCustomer)
        .isInstanceOf(OrderDomainException.class);
  }

  @Test
  void theAgreementContextIsAChoiceAndOnlyOtherCarriesADescription() {
    SalesOrder order = SalesOrder.builder().orderNumber("SO-1").build();

    order.applyAgreementContext(AgreementContext.TRADE_FAIR_OR_EVENT, null);
    assertThat(order.getAgreementContext()).isEqualTo(AgreementContext.TRADE_FAIR_OR_EVENT);
    assertThatThrownBy(() -> order.applyAgreementContext(AgreementContext.OTHER, "  "))
        .isInstanceOf(OrderDomainException.class);
    assertThatThrownBy(() -> order.applyAgreementContext(AgreementContext.REMOTE_MEETING, "Zoom"))
        .isInstanceOf(OrderDomainException.class);
    order.applyAgreementContext(AgreementContext.OTHER, " Agent's office ");
    assertThat(order.getAgreementContextNote()).isEqualTo("Agent's office");
  }
}
