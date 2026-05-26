package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.approval.ApprovalPort;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.DocumentNumberGenerator;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.sales.salesorder.app.ruleengine.SalesOrderRuleEngine;
import com.fabricmanagement.sales.salesorder.domain.OrderType;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderConfirmedEvent;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderDto;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SalesOrderServiceConfirmTest {

  @Mock private SalesOrderRepository orderRepository;
  @Mock private TradingPartnerResolver partnerResolver;
  @Mock private TradingPartnerService partnerService;
  @Mock private SalesOrderLineRepository lineRepository;
  @Mock private SalesOrderRuleEngine ruleEngine;
  @Mock private ModuleSpecsValidator moduleSpecsValidator;
  @Mock private DomainEventPublisher domainEventPublisher;
  @Mock private DocumentNumberGenerator documentNumberGenerator;
  @Mock private ApprovalPort approvalPort;

  @InjectMocks private SalesOrderService salesOrderService;

  @Captor private ArgumentCaptor<SalesOrderConfirmedEvent> eventCaptor;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID orderId = UUID.randomUUID();
  private final UUID partnerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(userId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private SalesOrder createDraftOrder() {
    SalesOrder order =
        SalesOrder.builder()
            .tradingPartnerId(partnerId)
            .orderNumber("SO-001")
            .orderType(OrderType.SALES)
            .build();
    ReflectionTestUtils.setField(order, "id", orderId);
    return order;
  }

  private SalesOrderLine createLine(String unit, BigDecimal qty) {
    SalesOrderLine line =
        SalesOrderLine.builder().salesOrderId(orderId).productId(UUID.randomUUID()).build();
    ReflectionTestUtils.setField(line, "unit", unit);
    ReflectionTestUtils.setField(line, "requestedQty", qty);
    ReflectionTestUtils.setField(line, "isActive", true);
    return line;
  }

  @Test
  void confirmOrder_populatesEventWithCustomerIdAndName() {
    // Arrange
    SalesOrder order = createDraftOrder();
    when(orderRepository.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
    when(approvalPort.requiresApproval(any(), any(), any(), any(), anyInt(), any(), any()))
        .thenReturn(false);
    when(orderRepository.save(any(SalesOrder.class))).thenReturn(order);

    TradingPartnerDto partner = TradingPartnerDto.builder().build();
    ReflectionTestUtils.setField(partner, "id", partnerId);
    ReflectionTestUtils.setField(partner, "displayName", "Test Customer");
    when(partnerService.findById(tenantId, partnerId)).thenReturn(Optional.of(partner));

    List<SalesOrderLine> lines = List.of(createLine("KG", BigDecimal.valueOf(100)));
    when(lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(lines);

    // Act
    SalesOrderDto result = salesOrderService.confirmOrder(orderId);

    // Assert
    verify(domainEventPublisher).publish(eventCaptor.capture());
    SalesOrderConfirmedEvent event = eventCaptor.getValue();

    assertThat(event.getCustomerId()).isEqualTo(partnerId);
    assertThat(event.getCustomerName()).isEqualTo("Test Customer");
  }

  @Test
  void confirmOrder_sameUnitLines_populatesUnit() {
    // Arrange
    SalesOrder order = createDraftOrder();
    when(orderRepository.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
    when(approvalPort.requiresApproval(any(), any(), any(), any(), anyInt(), any(), any()))
        .thenReturn(false);
    when(orderRepository.save(any(SalesOrder.class))).thenReturn(order);

    when(partnerService.findById(tenantId, partnerId)).thenReturn(Optional.empty());

    List<SalesOrderLine> lines =
        List.of(
            createLine("MT", BigDecimal.valueOf(100)), createLine("MT", BigDecimal.valueOf(50)));
    when(lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(lines);

    // Act
    salesOrderService.confirmOrder(orderId);

    // Assert
    verify(domainEventPublisher).publish(eventCaptor.capture());
    SalesOrderConfirmedEvent event = eventCaptor.getValue();

    assertThat(event.getUnit()).isEqualTo("MT");
    assertThat(event.getTotalQuantity()).isEqualTo(BigDecimal.valueOf(150));
  }

  @Test
  void confirmOrder_mixedUnitLines_unitIsNull() {
    // Arrange
    SalesOrder order = createDraftOrder();
    when(orderRepository.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
    when(approvalPort.requiresApproval(any(), any(), any(), any(), anyInt(), any(), any()))
        .thenReturn(false);
    when(orderRepository.save(any(SalesOrder.class))).thenReturn(order);

    when(partnerService.findById(tenantId, partnerId)).thenReturn(Optional.empty());

    List<SalesOrderLine> lines =
        List.of(
            createLine("MT", BigDecimal.valueOf(100)), createLine("KG", BigDecimal.valueOf(50)));
    when(lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(lines);

    // Act
    salesOrderService.confirmOrder(orderId);

    // Assert
    verify(domainEventPublisher).publish(eventCaptor.capture());
    SalesOrderConfirmedEvent event = eventCaptor.getValue();

    assertThat(event.getUnit()).isNull();
    assertThat(event.getTotalQuantity()).isEqualTo(BigDecimal.valueOf(150));
  }

  @Test
  void confirmOrder_noLines_unitIsNull() {
    // Arrange
    SalesOrder order = createDraftOrder();
    when(orderRepository.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
    when(approvalPort.requiresApproval(any(), any(), any(), any(), anyInt(), any(), any()))
        .thenReturn(false);
    when(orderRepository.save(any(SalesOrder.class))).thenReturn(order);

    when(partnerService.findById(tenantId, partnerId)).thenReturn(Optional.empty());

    when(lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of());

    // Act
    salesOrderService.confirmOrder(orderId);

    // Assert
    verify(domainEventPublisher).publish(eventCaptor.capture());
    SalesOrderConfirmedEvent event = eventCaptor.getValue();

    assertThat(event.getUnit()).isNull();
    assertThat(event.getTotalQuantity()).isEqualTo(BigDecimal.ZERO);
  }

  @Test
  void confirmOrder_partnerNotFound_customerNameIsNull() {
    // Arrange
    SalesOrder order = createDraftOrder();
    when(orderRepository.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
    when(approvalPort.requiresApproval(any(), any(), any(), any(), anyInt(), any(), any()))
        .thenReturn(false);
    when(orderRepository.save(any(SalesOrder.class))).thenReturn(order);

    when(partnerService.findById(tenantId, partnerId)).thenReturn(Optional.empty());

    when(lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of());

    // Act
    salesOrderService.confirmOrder(orderId);

    // Assert
    verify(domainEventPublisher).publish(eventCaptor.capture());
    SalesOrderConfirmedEvent event = eventCaptor.getValue();

    assertThat(event.getCustomerId()).isEqualTo(partnerId);
    assertThat(event.getCustomerName()).isNull();
  }
}
