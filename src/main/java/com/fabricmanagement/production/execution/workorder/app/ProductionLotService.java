package com.fabricmanagement.production.execution.workorder.app;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchSourceType;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.domain.CreateBatchCommand;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.domain.exception.WorkOrderDomainException;
import com.fabricmanagement.production.execution.workorder.dto.OpenProductionLotRequest;
import com.fabricmanagement.production.execution.workorder.dto.ProductionLotResponse;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing 1:N Production Lots (Batches) for a WorkOrder. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductionLotService {

  private final BatchRepository batchRepository;
  private final WorkOrderRepository workOrderRepository;

  @Transactional
  public ProductionLotResponse openLot(UUID workOrderId, OpenProductionLotRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    WorkOrder wo = loadWorkOrder(workOrderId, tenantId);

    if (wo.getStatus() != WorkOrderStatus.IN_PROGRESS) {
      throw new WorkOrderDomainException(
          String.format(
              "Cannot open lot. WorkOrder %s is not IN_PROGRESS (status=%s)",
              wo.getWorkOrderNumber(), wo.getStatus()));
    }

    if (wo.getOutputProductId() == null) {
      throw new WorkOrderDomainException("WorkOrder has no output product ID set.");
    }

    // Lot sequence based on existing lots for this WO
    long existingLotCount =
        batchRepository.countByTenantIdAndSourceIdAndSourceTypeAndIsActiveTrue(
            tenantId, workOrderId, BatchSourceType.INTERNAL_PRODUCTION);

    String lotCode = String.format("%s-LOT-%03d", wo.getWorkOrderNumber(), existingLotCount + 1);

    // Create a new empty Batch (Lot)
    Batch lot =
        Batch.create(
            new CreateBatchCommand(
                tenantId,
                wo.getOutputProductId(),
                request.productType(),
                lotCode,
                null, // supplierBatchCode
                BigDecimal.ZERO, // Starts with 0 quantity, increases as StockUnits are recorded
                wo.getUnit(),
                Instant.now(), // productionDate
                null, // expiryDate
                request.locationId(),
                null, // qualityStandardId
                request.remarks(),
                new HashMap<>(), // attributes
                BatchSourceType.INTERNAL_PRODUCTION,
                workOrderId, // sourceId defines the 1:N relationship
                null)); // New production lots have no inferred color identity

    lot.setStatus(BatchStatus.AVAILABLE);
    Batch saved;
    try {
      saved = batchRepository.saveAndFlush(lot);
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      throw new WorkOrderDomainException("Concurrent lot creation detected. Please try again.");
    }

    log.info("Opened new production lot {} for WorkOrder {}", lotCode, wo.getWorkOrderNumber());

    return ProductionLotResponse.from(saved);
  }

  @Transactional
  public ProductionLotResponse closeLot(UUID workOrderId, UUID lotId) {
    UUID tenantId = TenantContext.requireTenantId();
    loadWorkOrder(workOrderId, tenantId);

    Batch lot = loadLot(lotId, tenantId, workOrderId);

    if (lot.getStatus() == BatchStatus.DEPLETED) {
      throw new WorkOrderDomainException("Lot is already closed (DEPLETED).");
    }

    lot.setStatus(BatchStatus.DEPLETED);
    Batch saved = batchRepository.save(lot);

    log.info("Closed production lot {} for WorkOrder {}", lot.getBatchCode(), workOrderId);

    return ProductionLotResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public List<ProductionLotResponse> getActiveLots(UUID workOrderId) {
    UUID tenantId = TenantContext.requireTenantId();
    loadWorkOrder(workOrderId, tenantId);

    List<Batch> activeLots =
        batchRepository.findByTenantIdAndSourceIdAndSourceTypeAndStatusAndIsActiveTrue(
            tenantId, workOrderId, BatchSourceType.INTERNAL_PRODUCTION, BatchStatus.AVAILABLE);

    return activeLots.stream().map(ProductionLotResponse::from).toList();
  }

  private WorkOrder loadWorkOrder(UUID id, UUID tenantId) {
    return workOrderRepository
        .findByIdAndTenantIdAndIsActiveTrue(id, tenantId)
        .orElseThrow(() -> new NotFoundException("WorkOrder not found: " + id));
  }

  private Batch loadLot(UUID id, UUID tenantId, UUID workOrderId) {
    return batchRepository
        .findByIdAndTenantId(id, tenantId)
        .filter(b -> b.getSourceType() == BatchSourceType.INTERNAL_PRODUCTION)
        .filter(b -> workOrderId.equals(b.getSourceId()))
        .filter(BaseEntity::getIsActive)
        .orElseThrow(() -> new NotFoundException("Production lot not found: " + id));
  }
}
