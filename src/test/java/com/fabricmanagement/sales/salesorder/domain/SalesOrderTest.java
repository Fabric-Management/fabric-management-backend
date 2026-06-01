package com.fabricmanagement.sales.salesorder.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.common.util.OrderTotals;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SalesOrderTest {

  @Test
  void updateDraft_whenDraft_appliesChanges() {
    SalesOrder order =
        SalesOrder.builder()
            .tradingPartnerId(UUID.randomUUID())
            .orderNumber("SO-123")
            .orderType(OrderType.SALES)
            .build();

    OrderTotals totals =
        OrderTotals.of(
            Money.of(new BigDecimal("100"), "USD"), Money.zero("USD"), Money.zero("USD"));

    SalesOrderUpdateCommand cmd =
        new SalesOrderUpdateCommand(
            "REF-123",
            LocalDate.of(2023, 10, 1),
            LocalDate.of(2023, 10, 15),
            LocalDate.of(2023, 10, 20),
            totals,
            "Ship To",
            "Bill To",
            "TRUCK",
            "Some notes",
            null,
            null,
            LocalDate.of(2023, 11, 1));

    order.updateDraft(cmd);

    assertThat(order.getCustomerReference()).isEqualTo("REF-123");
    assertThat(order.getOrderDate()).isEqualTo(LocalDate.of(2023, 10, 1));
    assertThat(order.getRequestedDeliveryDate()).isEqualTo(LocalDate.of(2023, 10, 15));
    assertThat(order.getPromisedDeliveryDate()).isEqualTo(LocalDate.of(2023, 10, 20));
    assertThat(order.getTotals()).isEqualTo(totals);
    assertThat(order.getShippingAddress()).isEqualTo("Ship To");
    assertThat(order.getBillingAddress()).isEqualTo("Bill To");
    assertThat(order.getShippingMethod()).isEqualTo("TRUCK");
    assertThat(order.getNotes()).isEqualTo("Some notes");
    assertThat(order.getDeadline()).isEqualTo(LocalDate.of(2023, 11, 1));
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"DRAFT"},
      mode = EnumSource.Mode.EXCLUDE)
  void updateDraft_whenNotDraft_throwsExceptionWith409(OrderStatus status) {
    SalesOrder order =
        SalesOrder.builder()
            .tradingPartnerId(UUID.randomUUID())
            .orderNumber("SO-123")
            .orderType(OrderType.SALES)
            .build();

    // Use reflection or the available state transition methods to put the order in non-draft state
    // Let's use the provided transitions if possible or simulate it.
    // For this test, it's easiest to change the field via reflection since the methods enforce
    // rules.
    try {
      java.lang.reflect.Field statusField = SalesOrder.class.getDeclaredField("status");
      statusField.setAccessible(true);
      statusField.set(order, status);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    SalesOrderUpdateCommand cmd =
        new SalesOrderUpdateCommand(
            "REF", LocalDate.now(), null, null, null, null, null, null, null, null, null, null);

    assertThatThrownBy(() -> order.updateDraft(cmd))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("does not allow editing")
        // Note: the test below requires OrderDomainException to have getHttpStatus() method
        // which it inherits from DomainException.
        .extracting("httpStatus")
        .isEqualTo(409);
  }

  @Test
  void confirm_whenDraft_updatesStatusToConfirmed() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.DRAFT).build();
    order.confirm();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
  }

  @Test
  void confirm_whenPendingApproval_throwsException() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.PENDING_APPROVAL).build();
    assertThatThrownBy(() -> order.confirm())
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("Order is awaiting approval; cannot be confirmed manually")
        .extracting("httpStatus")
        .isEqualTo(409);
  }

  @Test
  void confirmFromApproval_whenPendingApproval_updatesStatusToConfirmed() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.PENDING_APPROVAL).build();
    order.confirmFromApproval();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"PENDING_APPROVAL"},
      mode = EnumSource.Mode.EXCLUDE)
  void confirmFromApproval_whenNotPendingApproval_throwsException(OrderStatus status) {
    SalesOrder order = SalesOrder.builder().status(status).build();
    assertThatThrownBy(() -> order.confirmFromApproval())
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("Order can only be confirmed from PENDING_APPROVAL status");
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"DRAFT", "PENDING_APPROVAL"},
      mode = EnumSource.Mode.EXCLUDE)
  void confirm_whenNotDraftOrPendingApproval_throwsException(OrderStatus status) {
    SalesOrder order = SalesOrder.builder().status(status).build();
    assertThatThrownBy(() -> order.confirm())
        .isInstanceOf(OrderDomainException.class)
        .extracting("httpStatus")
        .isEqualTo(409);
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
}
