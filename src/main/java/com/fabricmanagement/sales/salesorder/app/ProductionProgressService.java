package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductionProgressService {

  private final SalesOrderLineRepository salesOrderLineRepository;
  private final SalesOrderRepository salesOrderRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public UUID markLineInProduction(UUID salesOrderLineId) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrderLine line =
        salesOrderLineRepository.findByTenantIdAndId(tenantId, salesOrderLineId).orElse(null);
    if (line == null) {
      log.error("SalesOrderLine not found for production start: {}", salesOrderLineId);
      return null;
    }

    SalesOrderLineStatus previousStatus = line.getLineStatus();
    boolean changed = line.markInProduction();
    if (changed) {
      salesOrderLineRepository.save(line);
      log.info(
          "SalesOrderLine {} status changed: {} -> {}",
          line.getId(),
          previousStatus,
          line.getLineStatus());
    } else {
      log.debug(
          "SalesOrderLine {} production start ignored. Current status={}",
          line.getId(),
          previousStatus);
    }
    return line.getSalesOrderId();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markOrderInProgressIfConfirmed(UUID salesOrderId) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrder order =
        salesOrderRepository.findByTenantIdAndId(tenantId, salesOrderId).orElse(null);
    if (order == null) {
      log.error("SalesOrder not found for production progress: {}", salesOrderId);
      return;
    }

    OrderStatus previousStatus = order.getStatus();
    boolean changed = order.markInProgressIfConfirmed();
    if (changed) {
      salesOrderRepository.save(order);
      log.info(
          "SalesOrder {} status changed: {} -> {}",
          order.getOrderNumber(),
          previousStatus,
          order.getStatus());
    } else {
      log.debug(
          "SalesOrder {} production progress ignored. Current status={}",
          order.getOrderNumber(),
          previousStatus);
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markLineProductionCompleted(UUID salesOrderLineId) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrderLine line =
        salesOrderLineRepository.findByTenantIdAndId(tenantId, salesOrderLineId).orElse(null);
    if (line == null) {
      log.error("SalesOrderLine not found for production completion: {}", salesOrderLineId);
      return;
    }

    SalesOrderLineStatus previousStatus = line.getLineStatus();
    boolean changed = line.markCompleted();
    if (changed) {
      salesOrderLineRepository.save(line);
      log.info(
          "SalesOrderLine {} status changed: {} -> {}",
          line.getId(),
          previousStatus,
          line.getLineStatus());
    } else {
      log.warn(
          "SalesOrderLine {} production completion ignored. Current status={}",
          line.getId(),
          previousStatus);
    }
  }
}
