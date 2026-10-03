package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.OrderDelivery;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.OrderLineAllocation;
import com.fabricmanagement.sales.salesorder.domain.OrderType;
import com.fabricmanagement.sales.salesorder.domain.PartyReference;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.AllocationInput;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.SetAllocationsRequest;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderDeliveryRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderLineAllocationRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** ADR-0014 D8: allocations never exceed a line and are replaced per delivery. */
@ExtendWith(MockitoExtension.class)
class OrderDeliveryAllocationTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ACTOR = UUID.randomUUID();

  @Mock private OrderDraftAccess access;
  @Mock private OrderDeliveryRepository deliveries;
  @Mock private OrderLineAllocationRepository allocations;
  @Mock private SalesOrderLineRepository lines;
  @Mock private SalesOrderRevision revision;
  @InjectMocks private OrderDeliveryService service;

  private SalesOrder order;
  private OrderDelivery delivery;
  private OrderDelivery other;
  private SalesOrderLine line;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    TenantContext.setCurrentUserId(ACTOR);
    order =
        SalesOrder.builder()
            .tradingPartnerId(UUID.randomUUID())
            .orderNumber("SO-1")
            .orderType(OrderType.SALES)
            .build();
    ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(order, "version", 3L);
    delivery = delivery(1);
    other = delivery(2);
    line =
        SalesOrderLine.builder()
            .salesOrderId(order.getId())
            .requestedQty(new BigDecimal("100"))
            .unit("m")
            .build();
    ReflectionTestUtils.setField(line, "id", UUID.randomUUID());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private OrderDelivery delivery(int sequence) {
    OrderDelivery value =
        OrderDelivery.create(
            order.getId(),
            sequence,
            new OrderDelivery.Content(
                PartyReference.NONE, null, null, null, null, null, null, false));
    ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
    return value;
  }

  private void givenAnEditableOrderWithOtherDeliveryCarrying(String quantity) {
    when(access.writable(order.getId(), 3L, ACTOR)).thenReturn(order);
    when(deliveries.findByTenantIdAndSalesOrderIdAndId(TENANT, order.getId(), delivery.getId()))
        .thenReturn(Optional.of(delivery));
    when(lines.findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByCreatedAtAscIdAsc(
            TENANT, order.getId()))
        .thenReturn(List.of(line));
    when(allocations.findByTenantIdAndSalesOrderId(TENANT, order.getId()))
        .thenReturn(
            List.of(
                OrderLineAllocation.of(
                    order.getId(), line.getId(), other.getId(), new BigDecimal(quantity))));
  }

  private SetAllocationsRequest request(UUID lineId, String quantity) {
    return new SetAllocationsRequest(
        3L, List.of(new AllocationInput(lineId, new BigDecimal(quantity))));
  }

  @Test
  void theDeliveriesNeverCarryMoreOfALineThanTheOrderHas() {
    givenAnEditableOrderWithOtherDeliveryCarrying("60");

    assertThatThrownBy(
            () ->
                service.setAllocations(
                    order.getId(), delivery.getId(), request(line.getId(), "40.001"), ACTOR))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("more of this line");
    verify(allocations, never()).deleteByDelivery(TENANT, delivery.getId());
  }

  @Test
  void aDeliverysAllocationsAreReplacedAfterTheOldOnesAreRemoved() {
    givenAnEditableOrderWithOtherDeliveryCarrying("60");

    service.setAllocations(order.getId(), delivery.getId(), request(line.getId(), "40"), ACTOR);

    InOrder sequence = inOrder(allocations, revision);
    sequence.verify(allocations).deleteByDelivery(TENANT, delivery.getId());
    sequence.verify(allocations).saveAll(anyList());
    sequence.verify(revision).linesChanged(order);
  }

  @Test
  void onlyLinesOfThisOrderAreAllocated() {
    givenAnEditableOrderWithOtherDeliveryCarrying("10");

    assertThatThrownBy(
            () ->
                service.setAllocations(
                    order.getId(), delivery.getId(), request(UUID.randomUUID(), "5"), ACTOR))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("not on this order");
  }

  @Test
  void aLineIsGivenOncePerDelivery() {
    givenAnEditableOrderWithOtherDeliveryCarrying("10");
    SetAllocationsRequest twice =
        new SetAllocationsRequest(
            3L,
            List.of(
                new AllocationInput(line.getId(), BigDecimal.ONE),
                new AllocationInput(line.getId(), BigDecimal.ONE)));

    assertThatThrownBy(() -> service.setAllocations(order.getId(), delivery.getId(), twice, ACTOR))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("once per delivery");
  }

  @Test
  void deliveriesChangeOnlyInSalesDraft() {
    ReflectionTestUtils.setField(order, "flowStage", OrderFlowStage.IN_PLANNING);
    when(access.writable(eq(order.getId()), eq(3L), eq(ACTOR))).thenReturn(order);

    assertThatThrownBy(
            () ->
                service.setAllocations(
                    order.getId(), delivery.getId(), request(line.getId(), "1"), ACTOR))
        .isInstanceOf(OrderDomainException.class);
    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.IN_PLANNING);
  }
}
