package com.fabricmanagement.production.execution.workorder.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.ReservationStatus;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchReservationRepository;
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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
  private final BatchReservationRepository batchReservationRepository;
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
    // Handles validation, status transitions, event publishing, and Batch sync reliably
    if (stockUnit.getStatus() == StockUnitStatus.RESERVED) {
      stockUnitService.consumeReserved(stockUnitId, amount, workOrderId);

      // Keep logical BatchReservation in sync with physical consumption
      batchReservationRepository
          .findFirstByTenantIdAndBatchIdAndReferenceIdAndStatusInAndIsActiveTrue(
              tenantId,
              batch.getId(),
              workOrderId,
              java.util.Set.of(ReservationStatus.ACTIVE, ReservationStatus.PARTIALLY_CONSUMED))
          .ifPresent(
              reservation -> {
                reservation.consume(amount);
                batchReservationRepository.save(reservation);
              });
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
            stockUnit.getProductType(),
            batch.getProductId(),
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

    // DB-level aggregation — no in-memory groupBy
    List<WorkOrderConsumptionSummaryResponse.ProductBreakdown> breakdowns =
        workOrderConsumptionRepository.aggregateByProductType(tenantId, workOrderId).stream()
            .map(
                agg ->
                    new WorkOrderConsumptionSummaryResponse.ProductBreakdown(
                        agg.getProductType(), agg.getTotalWeight(), agg.getRecordCount()))
            .toList();

    // Derive total from aggregation — single query, no extra DB round-trip
    BigDecimal totalConsumed =
        breakdowns.stream()
            .map(WorkOrderConsumptionSummaryResponse.ProductBreakdown::consumedWeight)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return new WorkOrderConsumptionSummaryResponse(
        workOrder.getId(),
        workOrder.getPlannedQty(),
        totalConsumed,
        workOrder.getUnit(),
        breakdowns);
  }

  @Transactional(readOnly = true)
  public PagedResponse<WorkOrderConsumptionResponse> getConsumptionsPaged(
      UUID workOrderId, Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();
    loadWorkOrder(workOrderId, tenantId);

    Page<WorkOrderConsumption> page =
        workOrderConsumptionRepository.findByTenantIdAndWorkOrderIdAndIsActiveTrue(
            tenantId, workOrderId, pageable);

    return PagedResponse.from(page, WorkOrderConsumptionResponse::from);
  }

  // --- Helpers --- //

  private WorkOrder loadWorkOrder(UUID id, UUID tenantId) {
    return workOrderRepository
        .findById(id)
        .filter(wo -> wo.getTenantId().equals(tenantId))
        .filter(BaseEntity::getIsActive)
        .orElseThrow(() -> new NotFoundException("WorkOrder not found: " + id));
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
