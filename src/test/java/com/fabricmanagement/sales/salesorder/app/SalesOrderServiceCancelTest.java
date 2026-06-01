package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderCancelledEvent;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderDto;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.util.List;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SalesOrderServiceCancelTest {

  @Mock private SalesOrderRepository orderRepository;
  @Mock private SalesOrderLineRepository lineRepository;
  @Mock private DomainEventPublisher domainEventPublisher;

  @InjectMocks private SalesOrderService salesOrderService;

  private UUID tenantId;
  private UUID orderId;
  private SalesOrder order;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    orderId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);

    order =
        SalesOrder.builder()
            .tradingPartnerId(UUID.randomUUID())
            .orderNumber("SO-123")
            .status(OrderStatus.IN_PROGRESS)
            .build();
    ReflectionTestUtils.setField(order, "id", orderId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void cancelOrder_publishesSalesOrderCancelledEvent() {
    when(orderRepository.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(SalesOrder.class))).thenReturn(order);

    SalesOrderLine line = SalesOrderLine.builder().build();
    ReflectionTestUtils.setField(line, "id", UUID.randomUUID());
    when(lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of(line));

    SalesOrderDto result = salesOrderService.cancelOrder(orderId);

    assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);

    ArgumentCaptor<SalesOrderCancelledEvent> eventCaptor =
        ArgumentCaptor.forClass(SalesOrderCancelledEvent.class);
    verify(domainEventPublisher).publish(eventCaptor.capture());

    SalesOrderCancelledEvent event = eventCaptor.getValue();
    assertThat(event.getSalesOrderId()).isEqualTo(orderId);
    assertThat(event.getOrderNumber()).isEqualTo("SO-123");
    assertThat(event.getCancelledLineIds()).containsExactly(line.getId());
    assertThat(event.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  void resumeOrder_restoresStatus() {
    ReflectionTestUtils.setField(order, "status", OrderStatus.ON_HOLD);
    ReflectionTestUtils.setField(order, "statusBeforeHold", OrderStatus.CONFIRMED);

    when(orderRepository.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(SalesOrder.class))).thenReturn(order);

    SalesOrderDto result = salesOrderService.resumeOrder(orderId);

    assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
  }

  @Test
  void reviseOrder_setsDraft() {
    ReflectionTestUtils.setField(order, "status", OrderStatus.REJECTED);

    when(orderRepository.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(SalesOrder.class))).thenReturn(order);

    SalesOrderDto result = salesOrderService.reviseOrder(orderId);

    assertThat(result.getStatus()).isEqualTo(OrderStatus.DRAFT);
  }

  @Test
  void cancelOrder_whenShipped_throwsException() {
    ReflectionTestUtils.setField(order, "status", OrderStatus.SHIPPED);

    when(orderRepository.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
    // Lines are collected before cancel() — stub explicitly to document intent
    when(lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of());

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> salesOrderService.cancelOrder(orderId))
        .isInstanceOf(com.fabricmanagement.sales.common.exception.OrderDomainException.class)
        .hasMessageContaining("Cannot cancel order");
  }

  @Test
  void resumeOrder_whenNotOnHold_throwsException() {
    ReflectionTestUtils.setField(order, "status", OrderStatus.IN_PROGRESS);

    when(orderRepository.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> salesOrderService.resumeOrder(orderId))
        .isInstanceOf(com.fabricmanagement.sales.common.exception.OrderDomainException.class)
        .hasMessageContaining("must be ON_HOLD");
  }

  @Test
  void reviseOrder_whenNotRejected_throwsException() {
    ReflectionTestUtils.setField(order, "status", OrderStatus.IN_PROGRESS);

    when(orderRepository.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> salesOrderService.reviseOrder(orderId))
        .isInstanceOf(com.fabricmanagement.sales.common.exception.OrderDomainException.class)
        .hasMessageContaining("only REJECTED orders can be revised");
  }

  @Test
  void resumeOrder_whenStatusBeforeHoldNull_throwsIllegalState() {
    ReflectionTestUtils.setField(order, "status", OrderStatus.ON_HOLD);
    ReflectionTestUtils.setField(order, "statusBeforeHold", null);

    when(orderRepository.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> salesOrderService.resumeOrder(orderId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("statusBeforeHold is null");
  }
}
