package com.fabricmanagement.sales.salesorder.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.sales.common.exception.OrderDomainException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** ADR-0014 D8: deliveries inherit or override the order's term and requested date. */
class OrderDeliveryTest {

  private static final UUID CUSTOMER = UUID.randomUUID();
  private static final LocalDate DAY = LocalDate.of(2026, 11, 20);

  private static SalesOrder order() {
    SalesOrder value =
        SalesOrder.builder()
            .tradingPartnerId(CUSTOMER)
            .orderNumber("SO-1")
            .orderType(OrderType.SALES)
            .build();
    ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
    value.applyDeliveryTerms(DeliveryTerms.of(DeliveryTerm.FCA, "Mill gate", null));
    value.applyDeliveryTermStatus(null, null);
    value.applyRequestedDate(
        RequestedDate.of(
            RequestedDateStatus.REQUESTED, DAY, RequestedDeliveryEvent.UNSPECIFIED, null));
    return value;
  }

  private static OrderDelivery.Content following() {
    return new OrderDelivery.Content(
        PartyReference.NONE, null, null, null, null, null, null, false);
  }

  private static DeliveryTermSetting dap() {
    return DeliveryTermSetting.of(
        DeliveryTerms.of(DeliveryTerm.DAP, "Producer's site, Leeds", null), null, null);
  }

  @Test
  void aNewDeliveryFollowsTheOrdersTermAndDate() {
    SalesOrder order = order();
    OrderDelivery delivery = OrderDelivery.create(order.getId(), 1, following());

    assertThat(delivery.getTermSource()).isEqualTo(DefaultSource.ORDER_DEFAULT);
    assertThat(delivery.effectiveTerm(order).terms().term()).isEqualTo(DeliveryTerm.FCA);
    assertThat(delivery.effectiveRequestedDate(order).date()).isEqualTo(DAY);
    assertThat(delivery.getOwnTerm()).isEqualTo(DeliveryTermSetting.NONE);
  }

  @Test
  void aDeliveryWithItsOwnTermKeepsItWhenTheOrderDefaultChanges() {
    SalesOrder order = order();
    OrderDelivery delivery =
        OrderDelivery.create(
            order.getId(),
            1,
            new OrderDelivery.Content(
                PartyReference.NONE, null, DefaultSource.OVERRIDE, dap(), null, null, null, true));
    order.applyDeliveryTerms(DeliveryTerms.of(DeliveryTerm.EXW, "Mill", null));

    assertThat(delivery.effectiveTerm(order).terms().term()).isEqualTo(DeliveryTerm.DAP);
    assertThat(delivery.isShipComplete()).isTrue();
  }

  @Test
  void aSourceAndItsValueMustAgree() {
    UUID orderId = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                OrderDelivery.create(
                    orderId,
                    1,
                    new OrderDelivery.Content(
                        PartyReference.NONE, null, null, dap(), null, null, null, false)))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("follows the order's delivery term");
    assertThatThrownBy(
            () ->
                OrderDelivery.create(
                    orderId,
                    1,
                    new OrderDelivery.Content(
                        PartyReference.NONE,
                        null,
                        DefaultSource.OVERRIDE,
                        null,
                        null,
                        null,
                        null,
                        false)))
        .isInstanceOf(OrderDomainException.class);
    assertThatThrownBy(
            () ->
                OrderDelivery.create(
                    orderId,
                    1,
                    new OrderDelivery.Content(
                        PartyReference.NONE,
                        null,
                        null,
                        null,
                        DefaultSource.OVERRIDE,
                        RequestedDate.UNKNOWN,
                        null,
                        false)))
        .isInstanceOf(OrderDomainException.class);
  }

  @Test
  void aThirdPartyConsigneeIsKeptOnTheDelivery() {
    PartyReference producer =
        PartyReference.of(
            PartyMode.SNAPSHOT,
            null,
            PartySnapshot.of("North Mill Garments", null, null, "+44 113 000 0000"),
            CUSTOMER,
            "consignee");
    OrderDelivery delivery =
        OrderDelivery.create(
            UUID.randomUUID(),
            2,
            new OrderDelivery.Content(
                producer,
                AddressSnapshot.of("1 Mill Lane", null, "Leeds", null, null, "GB", null),
                null,
                null,
                null,
                null,
                " Road freight ",
                false));
    assertThat(delivery.getConsignee()).isEqualTo(producer);
    assertThat(delivery.getTransportPreference()).isEqualTo("Road freight");
    assertThat(delivery.getSequenceNo()).isEqualTo(2);
  }

  @Test
  void aTermAgreedByTheCustomerIsNeverEntered() {
    assertThatThrownBy(
            () ->
                DeliveryTermSetting.of(
                    DeliveryTerms.of(DeliveryTerm.FCA, "Mill", null),
                    DeliveryTermStatus.AGREED_BY_CUSTOMER,
                    null))
        .isInstanceOf(OrderDomainException.class);
    assertThatThrownBy(
            () ->
                DeliveryTermSetting.of(
                    DeliveryTerms.of(DeliveryTerm.FCA, "Mill", null),
                    DeliveryTermStatus.AGREED_BY_CONTRACT,
                    null))
        .isInstanceOf(OrderDomainException.class);
  }

  @Test
  void aDeliveryResolvesDefaultsOnlyFromItsOwnOrder() {
    OrderDelivery delivery = OrderDelivery.create(UUID.randomUUID(), 1, following());
    assertThatThrownBy(() -> delivery.effectiveTerm(order()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aLineChangeMustStillFitItsDeliveries() {
    BigDecimal allocated = new BigDecimal("80");
    // Allowed: nothing allocated, or the new quantity still covers the deliveries.
    OrderLineAllocation.assertLineChangeFits(BigDecimal.ZERO, "M", new BigDecimal("1"), "KG");
    OrderLineAllocation.assertLineChangeFits(allocated, "M", new BigDecimal("80"), "m");

    assertThatThrownBy(
            () ->
                OrderLineAllocation.assertLineChangeFits(allocated, "M", new BigDecimal("60"), "M"))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("Deliveries carry 80 M");
    assertThatThrownBy(
            () ->
                OrderLineAllocation.assertLineChangeFits(
                    allocated, "M", new BigDecimal("100"), "KG"))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("changing the unit");
  }

  @Test
  void anAllocationIsPositiveWithAtMostThreeDecimals() {
    UUID id = UUID.randomUUID();
    assertThatThrownBy(() -> OrderLineAllocation.of(id, id, id, BigDecimal.ZERO))
        .isInstanceOf(OrderDomainException.class);
    assertThatThrownBy(() -> OrderLineAllocation.of(id, id, id, new BigDecimal("1.2345")))
        .isInstanceOf(OrderDomainException.class);
    assertThat(OrderLineAllocation.of(id, id, id, new BigDecimal("120.500")).getQuantity())
        .isEqualByComparingTo("120.5");
  }
}
