package com.fabricmanagement.production.execution.workorder.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.app.StockUnitService;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderConsumption;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderStockConsumedEvent;
import com.fabricmanagement.production.execution.workorder.domain.exception.WorkOrderDomainException;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderConsumptionResponse;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderConsumptionSummaryResponse;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderConsumptionRepository;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing shop-floor consumption of physical StockUnits for WorkOrders. */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkOrderConsumptionService {

  private final WorkOrderConsumptionRepository workOrderConsumptionRepository;
  private final WorkOrderRepository workOrderRepository;
  private final StockUnitService stockUnitService;
  private final StockUnitRepository stockUnitRepository;
  private final BatchRepository batchRepository;
  private final DomainEventPublisher domainEventPublisher;

  /**
   * Records a new StockUnit consumption against a WorkOrder.
   *
   * <p>Delegates the physical inventory update (and its cascading batch update) entirely to {@link
   * StockUnitService}, ensuring atomicity and preventing double-counting.
   */
  @Transactional
  public WorkOrderConsumptionResponse consumeFromStockUnit(
      UUID workOrderId, UUID stockUnitId, BigDecimal amount) {

    UUID tenantId = TenantContext.requireTenantId();
    UUID actorId = TenantContext.getCurrentUserId();

    // 1. Verify WorkOrder
    WorkOrder workOrder = loadWorkOrder(workOrderId, tenantId);
    if (workOrder.getStatus() != WorkOrderStatus.IN_PROGRESS) {
      throw new WorkOrderDomainException(
          String.format(
              "WorkOrder %s is not IN_PROGRESS (status=%s). Cannot consume.",
              workOrder.getWorkOrderNumber(), workOrder.getStatus()));
    }

    // 2. Fetch StockUnit and Batch (read-only) for denormalization
    StockUnit stockUnit = loadStockUnit(stockUnitId, tenantId);
    Batch batch = loadBatch(stockUnit.getBatchId(), tenantId);

    // 3. Perform consumption via StockUnitService
    // Handles validation, status transitons, event publishing, and Batch sync reliably
    if (stockUnit.getStatus() == StockUnitStatus.RESERVED) {
      stockUnitService.consumeReserved(stockUnitId, amount, workOrderId);
    } else {
      stockUnitService.consume(stockUnitId, amount);
    }

    // 4. Record consumption trace
    WorkOrderConsumption consumption =
        WorkOrderConsumption.record(
            tenantId,
            workOrderId,
            stockUnitId,
            stockUnit.getBatchId(),
            stockUnit.getBarcode(),
            batch.getBatchCode(),
            stockUnit.getMaterialType(),
            amount,
            stockUnit.getUnit(), // Usually matches workOrder unit, but SU owns physical unit
            stockUnit.getQualityGradeId(),
            actorId);

    WorkOrderConsumption saved = workOrderConsumptionRepository.save(consumption);

    // 5. Publish domain event
    domainEventPublisher.publish(
        new WorkOrderStockConsumedEvent(
            tenantId,
            workOrderId,
            stockUnitId,
            stockUnit.getBatchId(),
            amount,
            stockUnit.getUnit()));

    log.info(
        "Recorded WO {} consumption of {} {} from SU {}",
        workOrder.getWorkOrderNumber(),
        amount,
        stockUnit.getUnit(),
        stockUnit.getBarcode());

    return WorkOrderConsumptionResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public List<WorkOrderConsumptionResponse> getConsumptions(UUID workOrderId) {
    UUID tenantId = TenantContext.requireTenantId();
    // Validate existence and tenant ownership
    loadWorkOrder(workOrderId, tenantId);

    List<WorkOrderConsumption> consumptions =
        workOrderConsumptionRepository
            .findByTenantIdAndWorkOrderIdAndIsActiveTrueOrderByCreatedAtAsc(tenantId, workOrderId);

    return WorkOrderConsumptionResponse.from(consumptions);
  }

  @Transactional(readOnly = true)
  public WorkOrderConsumptionSummaryResponse getConsumptionSummary(UUID workOrderId) {
    UUID tenantId = TenantContext.requireTenantId();
    WorkOrder workOrder = loadWorkOrder(workOrderId, tenantId);

    List<WorkOrderConsumption> consumptions =
        workOrderConsumptionRepository
            .findByTenantIdAndWorkOrderIdAndIsActiveTrueOrderByCreatedAtAsc(tenantId, workOrderId);

    BigDecimal totalConsumed =
        consumptions.stream()
            .map(WorkOrderConsumption::getConsumedWeight)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    Map<
            com.fabricmanagement.production.masterdata.material.domain.MaterialType,
            List<WorkOrderConsumption>>
        grouped =
            consumptions.stream()
                .collect(Collectors.groupingBy(WorkOrderConsumption::getMaterialType));

    List<WorkOrderConsumptionSummaryResponse.MaterialBreakdown> breakdowns =
        grouped.entrySet().stream()
            .map(
                entry -> {
                  BigDecimal typeSum =
                      entry.getValue().stream()
                          .map(WorkOrderConsumption::getConsumedWeight)
                          .reduce(BigDecimal.ZERO, BigDecimal::add);
                  return new WorkOrderConsumptionSummaryResponse.MaterialBreakdown(
                      entry.getKey(), typeSum, entry.getValue().size());
                })
            .toList();

    return new WorkOrderConsumptionSummaryResponse(
        workOrder.getId(),
        workOrder.getPlannedQty(),
        totalConsumed,
        workOrder.getUnit(),
        breakdowns);
  }

  // --- Helpers --- //

  private WorkOrder loadWorkOrder(UUID id, UUID tenantId) {
    WorkOrder workOrder =
        workOrderRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("WorkOrder not found: " + id));

    if (!workOrder.getTenantId().equals(tenantId)) {
      throw new NotFoundException("WorkOrder not found: " + id);
    }
    return workOrder;
  }

  private StockUnit loadStockUnit(UUID id, UUID tenantId) {
    return stockUnitRepository
        .findById(id)
        .filter(u -> u.getTenantId().equals(tenantId))
        .orElseThrow(() -> new NotFoundException("StockUnit not found: " + id));
  }

  private Batch loadBatch(UUID id, UUID tenantId) {
    return batchRepository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new NotFoundException("Batch not found: " + id));
  }
}
