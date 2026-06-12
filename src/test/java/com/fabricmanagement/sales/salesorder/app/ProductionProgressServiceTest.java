package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.OrderTotals;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductionProgressServiceTest {

  @Mock private SalesOrderLineRepository salesOrderLineRepository;
  @Mock private SalesOrderRepository salesOrderRepository;

  @InjectMocks private ProductionProgressService productionProgressService;

  private UUID tenantId;
  private UUID orderId;
  private UUID lineId;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    orderId = UUID.randomUUID();
    lineId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void markLineInProduction_whenRecipeAssigned_updatesLineAndReturnsOrderId() {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .salesOrderId(orderId)
            .productDesc("Cotton fabric")
            .requestedQty(new BigDecimal("100"))
            .lineStatus(SalesOrderLineStatus.RECIPE_ASSIGNED)
            .build();
    when(salesOrderLineRepository.findByTenantIdAndId(tenantId, lineId))
        .thenReturn(Optional.of(line));

    UUID result = productionProgressService.markLineInProduction(lineId);

    assertThat(result).isEqualTo(orderId);
    assertThat(line.getLineStatus()).isEqualTo(SalesOrderLineStatus.IN_PRODUCTION);
    verify(salesOrderLineRepository).save(line);
  }

  @Test
  void markLineInProduction_whenAlreadyCompleted_isNoopAndReturnsOrderId() {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .salesOrderId(orderId)
            .productDesc("Cotton fabric")
            .requestedQty(new BigDecimal("100"))
            .lineStatus(SalesOrderLineStatus.COMPLETED)
            .build();
    when(salesOrderLineRepository.findByTenantIdAndId(tenantId, lineId))
        .thenReturn(Optional.of(line));

    UUID result = productionProgressService.markLineInProduction(lineId);

    assertThat(result).isEqualTo(orderId);
    assertThat(line.getLineStatus()).isEqualTo(SalesOrderLineStatus.COMPLETED);
    verify(salesOrderLineRepository, never()).save(line);
  }

  @Test
  void markLineInProduction_whenLineMissing_returnsNull() {
    when(salesOrderLineRepository.findByTenantIdAndId(tenantId, lineId))
        .thenReturn(Optional.empty());

    UUID result = productionProgressService.markLineInProduction(lineId);

    assertThat(result).isNull();
  }

  @Test
  void markOrderInProgressIfConfirmed_whenConfirmed_updatesOrder() {
    SalesOrder order =
        SalesOrder.builder()
            .totals(OrderTotals.zero("GBP"))
            .orderNumber("SO-001")
            .status(OrderStatus.CONFIRMED)
            .build();
    when(salesOrderRepository.findByTenantIdAndId(tenantId, orderId))
        .thenReturn(Optional.of(order));

    productionProgressService.markOrderInProgressIfConfirmed(orderId);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
    verify(salesOrderRepository).save(order);
  }

  @Test
  void markOrderInProgressIfConfirmed_whenAlreadyInProgress_isNoop() {
    SalesOrder order =
        SalesOrder.builder()
            .totals(OrderTotals.zero("GBP"))
            .orderNumber("SO-001")
            .status(OrderStatus.IN_PROGRESS)
            .build();
    when(salesOrderRepository.findByTenantIdAndId(tenantId, orderId))
        .thenReturn(Optional.of(order));

    productionProgressService.markOrderInProgressIfConfirmed(orderId);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
    verify(salesOrderRepository, never()).save(order);
  }

  @Test
  void markLineProductionCompleted_whenInProduction_updatesLine() {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .salesOrderId(orderId)
            .productDesc("Cotton fabric")
            .requestedQty(new BigDecimal("100"))
            .lineStatus(SalesOrderLineStatus.IN_PRODUCTION)
            .build();
    when(salesOrderLineRepository.findByTenantIdAndId(tenantId, lineId))
        .thenReturn(Optional.of(line));

    productionProgressService.markLineProductionCompleted(lineId);

    assertThat(line.getLineStatus()).isEqualTo(SalesOrderLineStatus.COMPLETED);
    verify(salesOrderLineRepository).save(line);
  }

  @Test
  void markLineProductionCompleted_whenAlreadyCompleted_isNoop() {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .salesOrderId(orderId)
            .productDesc("Cotton fabric")
            .requestedQty(new BigDecimal("100"))
            .lineStatus(SalesOrderLineStatus.COMPLETED)
            .build();
    when(salesOrderLineRepository.findByTenantIdAndId(tenantId, lineId))
        .thenReturn(Optional.of(line));

    productionProgressService.markLineProductionCompleted(lineId);

    assertThat(line.getLineStatus()).isEqualTo(SalesOrderLineStatus.COMPLETED);
    verify(salesOrderLineRepository, never()).save(line);
  }

  @Test
  void markLineProductionCompleted_whenNotInProduction_isNoopWithoutThrowing() {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .salesOrderId(orderId)
            .productDesc("Cotton fabric")
            .requestedQty(new BigDecimal("100"))
            .lineStatus(SalesOrderLineStatus.RECIPE_ASSIGNED)
            .build();
    when(salesOrderLineRepository.findByTenantIdAndId(tenantId, lineId))
        .thenReturn(Optional.of(line));

    productionProgressService.markLineProductionCompleted(lineId);

    assertThat(line.getLineStatus()).isEqualTo(SalesOrderLineStatus.RECIPE_ASSIGNED);
    verify(salesOrderLineRepository, never()).save(line);
  }
}
