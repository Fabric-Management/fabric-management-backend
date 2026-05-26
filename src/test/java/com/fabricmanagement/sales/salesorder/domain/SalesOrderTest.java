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
}
