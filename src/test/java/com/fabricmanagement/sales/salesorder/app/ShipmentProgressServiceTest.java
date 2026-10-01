package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ShipmentProgressServiceTest {

  @Mock private SalesOrderLineRepository salesOrderLineRepository;
  @Mock private SalesOrderRepository salesOrderRepository;

  @InjectMocks private ShipmentProgressService shipmentProgressService;

  private UUID orderId;
  private UUID lineId;
  private UUID shipmentLineId;

  @BeforeEach
  void setUp() {
    orderId = UUID.randomUUID();
    lineId = UUID.randomUUID();
    shipmentLineId = UUID.randomUUID();
    com.fabricmanagement.common.infrastructure.persistence.TenantContext.setCurrentTenantId(
        com.fabricmanagement.common.infrastructure.persistence.TenantContext.SYSTEM_TENANT_ID);
  }

  @org.junit.jupiter.api.AfterEach
  void tearDown() {
    com.fabricmanagement.common.infrastructure.persistence.TenantContext.clear();
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Faz 1: recordLineShipment Tests
  // ═══════════════════════════════════════════════════════════════════════════

  @Test
  void recordLineShipment_whenLineExists_updatesShippedQtyAndReturnsOrderId() {
    SalesOrderLine line = mock(SalesOrderLine.class);
    when(line.getSalesOrderId()).thenReturn(orderId);
    when(salesOrderLineRepository.findByTenantIdAndId(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext.SYSTEM_TENANT_ID,
            lineId))
        .thenReturn(Optional.of(line));

    BigDecimal confirmedQty = new BigDecimal("100");
    UUID result = shipmentProgressService.recordLineShipment(lineId, shipmentLineId, confirmedQty);

    assertThat(result).isEqualTo(orderId);
    verify(line).addShippedQuantity(shipmentLineId, confirmedQty);
    verify(salesOrderLineRepository).save(line);
  }

  @Test
  void recordLineShipment_whenLineNotFound_returnsNull() {
    when(salesOrderLineRepository.findByTenantIdAndId(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext.SYSTEM_TENANT_ID,
            lineId))
        .thenReturn(Optional.empty());

    UUID result =
        shipmentProgressService.recordLineShipment(lineId, shipmentLineId, new BigDecimal("100"));

    assertThat(result).isNull();
    verify(salesOrderLineRepository, never()).save(any());
  }

  @Test
  void recordLineShipment_whenCalledTwiceWithSameShipmentLineId_isIdempotent() {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .salesOrderId(orderId)
            .requestedQty(new BigDecimal("200"))
            .shippedQty(BigDecimal.ZERO)
            .lineStatus(SalesOrderLineStatus.IN_PRODUCTION)
            .build();

    when(salesOrderLineRepository.findByTenantIdAndId(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext.SYSTEM_TENANT_ID,
            lineId))
        .thenReturn(Optional.of(line));

    BigDecimal confirmedQty = new BigDecimal("100");

    // First call
    UUID result1 = shipmentProgressService.recordLineShipment(lineId, shipmentLineId, confirmedQty);

    // Second call with same shipmentLineId
    UUID result2 = shipmentProgressService.recordLineShipment(lineId, shipmentLineId, confirmedQty);

    assertThat(result1).isEqualTo(orderId);
    assertThat(result2).isEqualTo(orderId);
    assertThat(line.getShippedQty()).isEqualByComparingTo(new BigDecimal("100")); // Not 200
  }

  @Test
  void recordLineShipment_whenOverShippedPersistsPhysicalQuantityAndLogsWarn(
      CapturedOutput output) {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .salesOrderId(orderId)
            .requestedQty(new BigDecimal("100"))
            .shippedQty(new BigDecimal("90"))
            .lineStatus(SalesOrderLineStatus.IN_PRODUCTION)
            .build();

    when(salesOrderLineRepository.findByTenantIdAndId(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext.SYSTEM_TENANT_ID,
            lineId))
        .thenReturn(Optional.of(line));

    UUID result =
        shipmentProgressService.recordLineShipment(lineId, shipmentLineId, new BigDecimal("20"));

    assertThat(result).isEqualTo(orderId);
    assertThat(line.getShippedQty()).isEqualByComparingTo("110");
    assertThat(line.isOverShipped()).isTrue();
    verify(salesOrderLineRepository).save(line);
    assertThat(output).contains("Over-shipment detected for SalesOrderLine");
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Faz 2: updateOrderShipmentStatus Tests
  // ═══════════════════════════════════════════════════════════════════════════

  @Test
  void updateOrderShipmentStatus_whenPartialShipment_setsPartiallyShipped() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.CONFIRMED).build();
    when(salesOrderRepository.findByTenantIdAndId(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext.SYSTEM_TENANT_ID,
            orderId))
        .thenReturn(Optional.of(order));

    // 1 full, 1 zero -> partial
    SalesOrderLine line1 =
        SalesOrderLine.builder()
            .requestedQty(new BigDecimal("100"))
            .shippedQty(new BigDecimal("100"))
            .lineStatus(SalesOrderLineStatus.IN_PRODUCTION)
            .build();
    SalesOrderLine line2 =
        SalesOrderLine.builder()
            .requestedQty(new BigDecimal("50"))
            .shippedQty(BigDecimal.ZERO)
            .lineStatus(SalesOrderLineStatus.IN_PRODUCTION)
            .build();

    when(salesOrderLineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of(line1, line2));

    shipmentProgressService.updateOrderShipmentStatus(orderId);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_SHIPPED);
    verify(salesOrderRepository).save(order);
  }

  @Test
  void updateOrderShipmentStatus_whenAllShipped_setsShipped() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.CONFIRMED).build();
    when(salesOrderRepository.findByTenantIdAndId(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext.SYSTEM_TENANT_ID,
            orderId))
        .thenReturn(Optional.of(order));

    SalesOrderLine line1 =
        SalesOrderLine.builder()
            .requestedQty(new BigDecimal("100"))
            .shippedQty(new BigDecimal("100"))
            .lineStatus(SalesOrderLineStatus.SHIPPED)
            .build();
    SalesOrderLine line2 =
        SalesOrderLine.builder()
            .requestedQty(new BigDecimal("50"))
            .shippedQty(new BigDecimal("50"))
            .lineStatus(SalesOrderLineStatus.SHIPPED)
            .build();

    when(salesOrderLineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of(line1, line2));

    shipmentProgressService.updateOrderShipmentStatus(orderId);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    verify(salesOrderRepository).save(order);
  }

  @Test
  void updateOrderShipmentStatus_whenPartialQty_setsPartiallyShipped() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.CONFIRMED).build();
    when(salesOrderRepository.findByTenantIdAndId(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext.SYSTEM_TENANT_ID,
            orderId))
        .thenReturn(Optional.of(order));

    SalesOrderLine line1 =
        SalesOrderLine.builder()
            .requestedQty(new BigDecimal("100"))
            .shippedQty(new BigDecimal("60")) // 60 < 100
            .lineStatus(SalesOrderLineStatus.IN_WAREHOUSE)
            .build();

    when(salesOrderLineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of(line1));

    shipmentProgressService.updateOrderShipmentStatus(orderId);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_SHIPPED);
    verify(salesOrderRepository).save(order);
  }

  @Test
  void updateOrderShipmentStatus_whenOverShipmentAndOthersFull_setsShipped() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.PARTIALLY_SHIPPED).build();
    when(salesOrderRepository.findByTenantIdAndId(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext.SYSTEM_TENANT_ID,
            orderId))
        .thenReturn(Optional.of(order));

    SalesOrderLine line1 =
        SalesOrderLine.builder()
            .requestedQty(new BigDecimal("100"))
            .shippedQty(new BigDecimal("120")) // Over-shipment
            .lineStatus(SalesOrderLineStatus.SHIPPED)
            .build();
    SalesOrderLine line2 =
        SalesOrderLine.builder()
            .requestedQty(new BigDecimal("50"))
            .shippedQty(new BigDecimal("50")) // Full
            .lineStatus(SalesOrderLineStatus.SHIPPED)
            .build();

    when(salesOrderLineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of(line1, line2));

    shipmentProgressService.updateOrderShipmentStatus(orderId);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    verify(salesOrderRepository).save(order);
  }

  @Test
  void updateOrderShipmentStatus_whenCancelledLinesPresent_ignoresCancelledLines() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.CONFIRMED).build();
    when(salesOrderRepository.findByTenantIdAndId(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext.SYSTEM_TENANT_ID,
            orderId))
        .thenReturn(Optional.of(order));

    SalesOrderLine line1 =
        SalesOrderLine.builder()
            .requestedQty(new BigDecimal("100"))
            .shippedQty(new BigDecimal("100"))
            .lineStatus(SalesOrderLineStatus.SHIPPED)
            .build();
    SalesOrderLine line2 = // Cancelled, should be ignored
        SalesOrderLine.builder()
            .requestedQty(new BigDecimal("50"))
            .shippedQty(BigDecimal.ZERO)
            .lineStatus(SalesOrderLineStatus.CANCELLED)
            .build();

    when(salesOrderLineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of(line1, line2));

    shipmentProgressService.updateOrderShipmentStatus(orderId);

    // Should be SHIPPED because the only active line is fully shipped
    assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    verify(salesOrderRepository).save(order);
  }

  @Test
  void updateOrderShipmentStatus_whenOrderDelivered_noop() {
    SalesOrder order = SalesOrder.builder().status(OrderStatus.DELIVERED).build();
    when(salesOrderRepository.findByTenantIdAndId(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext.SYSTEM_TENANT_ID,
            orderId))
        .thenReturn(Optional.of(order));

    SalesOrderLine line1 =
        SalesOrderLine.builder()
            .requestedQty(new BigDecimal("100"))
            .shippedQty(new BigDecimal("100"))
            .lineStatus(SalesOrderLineStatus.SHIPPED)
            .build();

    when(salesOrderLineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of(line1));

    shipmentProgressService.updateOrderShipmentStatus(orderId);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    verify(salesOrderRepository, never()).save(any());
  }

  @Test
  void updateOrderShipmentStatus_whenOrderNotFound_returns() {
    when(salesOrderRepository.findByTenantIdAndId(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext.SYSTEM_TENANT_ID,
            orderId))
        .thenReturn(Optional.empty());

    when(salesOrderLineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of());

    shipmentProgressService.updateOrderShipmentStatus(orderId);

    verify(salesOrderRepository, never()).save(any());
  }

  @Test
  void updateOrderShipmentStatus_whenStatusUnchanged_doesNotSave() {
    // Already PARTIALLY_SHIPPED
    SalesOrder order = SalesOrder.builder().status(OrderStatus.PARTIALLY_SHIPPED).build();
    when(salesOrderRepository.findByTenantIdAndId(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext.SYSTEM_TENANT_ID,
            orderId))
        .thenReturn(Optional.of(order));

    // Remains partial
    SalesOrderLine line1 =
        SalesOrderLine.builder()
            .requestedQty(new BigDecimal("100"))
            .shippedQty(new BigDecimal("50"))
            .lineStatus(SalesOrderLineStatus.IN_WAREHOUSE)
            .build();

    when(salesOrderLineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of(line1));

    shipmentProgressService.updateOrderShipmentStatus(orderId);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_SHIPPED);
    verify(salesOrderRepository, never()).save(any()); // Shouldn't save if status didn't change
  }
}
