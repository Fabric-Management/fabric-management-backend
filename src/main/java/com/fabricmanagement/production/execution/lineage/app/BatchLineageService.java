package com.fabricmanagement.production.execution.lineage.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.common.exception.InsufficientStockException;
import com.fabricmanagement.production.execution.fiber.domain.FiberBatch;
import com.fabricmanagement.production.execution.fiber.domain.FiberBatchStatus;
import com.fabricmanagement.production.execution.fiber.domain.exception.FiberBatchDomainException;
import com.fabricmanagement.production.execution.fiber.infra.repository.FiberBatchRepository;
import com.fabricmanagement.production.execution.lineage.domain.BatchLineage;
import com.fabricmanagement.production.execution.lineage.dto.BatchLineageDto;
import com.fabricmanagement.production.execution.lineage.dto.CreateBatchLineageRequest;
import com.fabricmanagement.production.execution.lineage.infra.repository.BatchLineageRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchLineageService {

  private final BatchLineageRepository batchLineageRepository;
  private final FiberBatchRepository fiberBatchRepository;

  @Transactional
  public BatchLineageDto create(CreateBatchLineageRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug(
        "Creating batch lineage: tenantId={}, parent={}, child={}",
        tenantId,
        request.getParentBatchId(),
        request.getChildBatchId());

    if (request.getParentBatchId().equals(request.getChildBatchId())) {
      throw new FiberBatchDomainException("A batch cannot be its own parent");
    }

    if (batchLineageRepository.existsByParentBatchIdAndChildBatchId(
        request.getParentBatchId(), request.getChildBatchId())) {
      throw new FiberBatchDomainException(
          String.format(
              "Lineage already exists: parent=%s → child=%s",
              request.getParentBatchId(), request.getChildBatchId()));
    }

    FiberBatch parentBatch = loadBatch(request.getParentBatchId(), tenantId, "Parent");
    loadBatch(request.getChildBatchId(), tenantId, "Child");

    if (parentBatch.getStatus() == FiberBatchStatus.DEPLETED
        && parentBatch.getAvailableQuantity().compareTo(BigDecimal.ZERO) <= 0) {
      throw new InsufficientStockException(
          parentBatch.getBatchCode(),
          request.getConsumedQuantity(),
          parentBatch.getAvailableQuantity(),
          parentBatch.getUnit());
    }

    if (request.getConsumptionPercentage() != null) {
      BigDecimal currentTotal =
          batchLineageRepository.sumConsumptionPercentageByChildBatchId(request.getChildBatchId());
      BigDecimal newTotal = currentTotal.add(request.getConsumptionPercentage());
      if (newTotal.compareTo(new BigDecimal("100")) > 0) {
        throw new FiberBatchDomainException(
            String.format(
                "Total consumption percentage would exceed 100%%: current=%.2f%%, adding=%.2f%%",
                currentTotal, request.getConsumptionPercentage()));
      }
    }

    BatchLineage lineage =
        BatchLineage.create(
            tenantId,
            request.getParentBatchId(),
            request.getChildBatchId(),
            request.getConsumedQuantity(),
            request.getUnit(),
            request.getConsumptionPercentage(),
            request.getConsumedAt(),
            request.getProcessReference(),
            request.getRemarks());

    lineage = batchLineageRepository.save(lineage);
    log.info(
        "Created batch lineage: id={}, parent={} → child={}, qty={} {}",
        lineage.getId(),
        lineage.getParentBatchId(),
        lineage.getChildBatchId(),
        lineage.getConsumedQuantity(),
        lineage.getUnit());

    return BatchLineageDto.from(lineage);
  }

  /** Forward trace: what input batches were consumed to produce this child batch? */
  @Transactional(readOnly = true)
  public List<BatchLineageDto> getParents(UUID childBatchId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug(
        "Getting parents for child batch: tenantId={}, childBatchId={}", tenantId, childBatchId);

    return batchLineageRepository
        .findByTenantIdAndChildBatchIdAndIsActiveTrue(tenantId, childBatchId)
        .stream()
        .map(BatchLineageDto::from)
        .toList();
  }

  /** Backward trace: where was this parent batch consumed? */
  @Transactional(readOnly = true)
  public List<BatchLineageDto> getChildren(UUID parentBatchId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug(
        "Getting children for parent batch: tenantId={}, parentBatchId={}",
        tenantId,
        parentBatchId);

    return batchLineageRepository
        .findByTenantIdAndParentBatchIdAndIsActiveTrue(tenantId, parentBatchId)
        .stream()
        .map(BatchLineageDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<BatchLineageDto> getAll() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting all batch lineage records: tenantId={}", tenantId);

    return batchLineageRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
        .map(BatchLineageDto::from)
        .toList();
  }

  @Transactional
  public void delete(UUID lineageId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Deleting batch lineage: tenantId={}, id={}", tenantId, lineageId);

    BatchLineage lineage =
        batchLineageRepository
            .findByIdAndTenantId(lineageId, tenantId)
            .orElseThrow(() -> new NotFoundException("Batch lineage not found: " + lineageId));

    lineage.delete();
    batchLineageRepository.save(lineage);
    log.info(
        "Deleted batch lineage: id={}, parent={} → child={}",
        lineage.getId(),
        lineage.getParentBatchId(),
        lineage.getChildBatchId());
  }

  private FiberBatch loadBatch(UUID batchId, UUID tenantId, String label) {
    return fiberBatchRepository
        .findByIdAndTenantId(batchId, tenantId)
        .orElseThrow(
            () -> new NotFoundException(String.format("%s batch not found: %s", label, batchId)));
  }
}
