package com.fabricmanagement.production.execution.workorder.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchSourceType;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderOutput;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderOutputRecordedEvent;
import com.fabricmanagement.production.execution.workorder.domain.exception.WorkOrderDomainException;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderOutputResponse;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderOutputSummaryResponse;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderConsumptionRepository;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderOutputRepository;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkOrderOutputService {

  private final WorkOrderOutputRepository workOrderOutputRepository;
  private final WorkOrderRepository workOrderRepository;
  private final StockUnitRepository stockUnitRepository;
  private final BatchRepository batchRepository;
  private final WorkOrderConsumptionRepository workOrderConsumptionRepository;
  private final DomainEventPublisher domainEventPublisher;

  @Transactional
  public WorkOrderOutputResponse recordOutput(UUID workOrderId, UUID stockUnitId, String notes) {
    UUID tenantId = TenantContext.requireTenantId();
    UUID actorId = TenantContext.getCurrentUserId();

    // 1. WorkOrder validation
    WorkOrder workOrder = loadWorkOrder(workOrderId, tenantId);
    if (workOrder.getStatus() != WorkOrderStatus.IN_PROGRESS) {
      throw new WorkOrderDomainException(
          String.format(
              "WorkOrder %s is not IN_PROGRESS (status=%s). Cannot record output.",
              workOrder.getWorkOrderNumber(), workOrder.getStatus()));
    }

    // 2. Load StockUnit + Batch
    StockUnit stockUnit = loadStockUnit(stockUnitId, tenantId);
    Batch batch = loadBatch(stockUnit.getBatchId(), tenantId);

    // 3. Validate: batch is output of THIS WorkOrder
    if (batch.getSourceType() != BatchSourceType.INTERNAL_PRODUCTION
        || !workOrderId.equals(batch.getSourceId())) {
      throw new WorkOrderDomainException(
          "StockUnit's batch is not an output batch of this WorkOrder");
    }

    // 4. Duplicate output prevention
    if (workOrderOutputRepository.existsByTenantIdAndStockUnitIdAndIsActiveTrue(
        tenantId, stockUnitId)) {
      throw new WorkOrderDomainException("StockUnit already recorded as output for a WorkOrder");
    }

    // 5. Record output link
    WorkOrderOutput output =
        WorkOrderOutput.record(
            tenantId,
            workOrderId,
            stockUnitId,
            batch.getId(),
            stockUnit.getBarcode(),
            batch.getBatchCode(),
            stockUnit.getMaterialType(),
            stockUnit.getInitialWeight(), // Use full produced initial weight
            stockUnit.getUnit(),
            stockUnit.getQualityGradeId(),
            actorId,
            notes);

    WorkOrderOutput saved = workOrderOutputRepository.save(output);

    // 6. Event Publishing
    domainEventPublisher.publish(
        new WorkOrderOutputRecordedEvent(
            tenantId,
            workOrderId,
            stockUnitId,
            batch.getId(),
            stockUnit.getInitialWeight(),
            stockUnit.getUnit()));

    log.info(
        "Recorded WO {} output of {} {} (SU {})",
        workOrder.getWorkOrderNumber(),
        stockUnit.getInitialWeight(),
        stockUnit.getUnit(),
        stockUnit.getBarcode());

    return WorkOrderOutputResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public List<WorkOrderOutputResponse> getOutputs(UUID workOrderId) {
    UUID tenantId = TenantContext.requireTenantId();
    loadWorkOrder(workOrderId, tenantId);

    List<WorkOrderOutput> outputs =
        workOrderOutputRepository.findByTenantIdAndWorkOrderIdAndIsActiveTrueOrderByCreatedAtAsc(
            tenantId, workOrderId);

    return WorkOrderOutputResponse.from(outputs);
  }

  @Transactional(readOnly = true)
  public WorkOrderOutputSummaryResponse getOutputSummary(UUID workOrderId) {
    UUID tenantId = TenantContext.requireTenantId();
    WorkOrder workOrder = loadWorkOrder(workOrderId, tenantId);

    List<WorkOrderOutput> outputs =
        workOrderOutputRepository.findByTenantIdAndWorkOrderIdAndIsActiveTrueOrderByCreatedAtAsc(
            tenantId, workOrderId);

    BigDecimal totalOutput =
        workOrderOutputRepository.sumOutputWeightByWorkOrderId(tenantId, workOrderId);

    BigDecimal totalConsumed =
        workOrderConsumptionRepository.sumConsumedWeightByWorkOrderId(tenantId, workOrderId);

    // Protect against zero division
    BigDecimal yieldPercentage =
        totalConsumed.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : totalOutput
                .multiply(new BigDecimal("100"))
                .divide(totalConsumed, 2, RoundingMode.HALF_UP);

    Map<
            com.fabricmanagement.production.masterdata.material.domain.MaterialType,
            List<WorkOrderOutput>>
        grouped = outputs.stream().collect(Collectors.groupingBy(WorkOrderOutput::getMaterialType));

    List<WorkOrderOutputSummaryResponse.MaterialBreakdown> breakdowns =
        grouped.entrySet().stream()
            .map(
                entry -> {
                  BigDecimal typeSum =
                      entry.getValue().stream()
                          .map(WorkOrderOutput::getOutputWeight)
                          .reduce(BigDecimal.ZERO, BigDecimal::add);
                  return new WorkOrderOutputSummaryResponse.MaterialBreakdown(
                      entry.getKey(), typeSum, entry.getValue().size());
                })
            .toList();

    return new WorkOrderOutputSummaryResponse(
        workOrder.getId(),
        workOrder.getPlannedQty(),
        totalOutput,
        totalConsumed,
        yieldPercentage,
        workOrder.getUnit(),
        breakdowns,
        outputs.size());
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
