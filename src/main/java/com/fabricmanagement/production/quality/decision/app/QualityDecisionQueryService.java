package com.fabricmanagement.production.quality.decision.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.domain.port.WarehouseLocationPort;
import com.fabricmanagement.production.execution.batch.domain.port.WarehouseLocationRef;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.quality.decision.domain.QualityDecision;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionEligibility;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionOutcome;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionReasonPolicy;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionScope;
import com.fabricmanagement.production.quality.decision.dto.QualityBatchSummaryDto;
import com.fabricmanagement.production.quality.decision.dto.QualityDecisionOptionsDto;
import com.fabricmanagement.production.quality.decision.dto.QualityDecisionOutcomeOptionDto;
import com.fabricmanagement.production.quality.decision.dto.QualityDecisionReasonOptionDto;
import com.fabricmanagement.production.quality.decision.dto.QualityDecisionUnitDto;
import com.fabricmanagement.production.quality.decision.dto.QualityQueueItemDto;
import com.fabricmanagement.production.quality.decision.dto.QualityRelocationTargetDto;
import com.fabricmanagement.production.quality.decision.infra.repository.QualityDecisionRepository;
import com.fabricmanagement.production.quality.decision.mapper.QualityDecisionMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class QualityDecisionQueryService {

  private final QualityDecisionRepository decisionRepository;
  private final StockUnitRepository stockUnitRepository;
  private final BatchRepository batchRepository;
  private final WarehouseLocationPort warehouseLocationPort;
  private final QualityDecisionMapper mapper;

  public QualityDecisionQueryService(
      QualityDecisionRepository decisionRepository,
      StockUnitRepository stockUnitRepository,
      BatchRepository batchRepository,
      WarehouseLocationPort warehouseLocationPort,
      QualityDecisionMapper mapper) {
    this.decisionRepository = decisionRepository;
    this.stockUnitRepository = stockUnitRepository;
    this.batchRepository = batchRepository;
    this.warehouseLocationPort = warehouseLocationPort;
    this.mapper = mapper;
  }

  public Page<QualityQueueItemDto> getQueue(Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();
    return stockUnitRepository
        .findQualityQueue(tenantId, pageable)
        .map(
            row ->
                new QualityQueueItemDto(
                    row.getBatchId(),
                    row.getBatchCode(),
                    row.getProductId(),
                    row.getProductUid(),
                    ProductType.valueOf(row.getProductType()),
                    row.getProductDisplayName(),
                    row.getColorId(),
                    row.getColorName(),
                    row.getSupplierBatchCode(),
                    row.getPendingUnitCount(),
                    row.getBatchCreatedAt()));
  }

  public Page<QualityDecision> getHistory(UUID batchId, Pageable pageable) {
    return decisionRepository.findByTenantIdAndBatchIdOrderByDecidedAtDescSeqDesc(
        TenantContext.requireTenantId(), batchId, pageable);
  }

  public QualityBatchSummaryDto getSummary(UUID batchId) {
    UUID tenantId = TenantContext.requireTenantId();
    var row =
        batchRepository
            .findQualityBatchSummary(tenantId, batchId)
            .orElseThrow(() -> new NotFoundException("Batch not found: " + batchId));
    Map<QualityDisposition, Long> counts =
        stockUnitRepository.countQualityDispositions(tenantId, batchId).stream()
            .collect(
                Collectors.toMap(
                    StockUnitRepository.QualityDispositionCount::getDisposition,
                    StockUnitRepository.QualityDispositionCount::getUnitCount));
    long totalCount = counts.values().stream().mapToLong(Long::longValue).sum();
    long activeUnitCount =
        stockUnitRepository.countByTenantIdAndBatchIdAndIsActiveTrue(tenantId, batchId);
    long statusEligibleUnitCount =
        stockUnitRepository.countByTenantIdAndBatchIdAndIsActiveTrueAndStatusIn(
            tenantId, batchId, QualityDecisionEligibility.unitStatusEligibleStatuses());
    var fullLotCapability =
        QualityDecisionEligibility.combine(
            QualityDecisionEligibility.evaluateBatch(
                QualityDecisionScope.FULL_LOT,
                BatchStatus.valueOf(row.getStatus()),
                row.getReservedQuantity(),
                row.getConsumedQuantity()),
            QualityDecisionEligibility.evaluatePopulation(
                QualityDecisionScope.FULL_LOT, activeUnitCount, statusEligibleUnitCount));
    var selectedUnitsCapability =
        QualityDecisionEligibility.combine(
            QualityDecisionEligibility.evaluateBatch(
                QualityDecisionScope.SELECTED_UNITS,
                BatchStatus.valueOf(row.getStatus()),
                row.getReservedQuantity(),
                row.getConsumedQuantity()),
            QualityDecisionEligibility.evaluatePopulation(
                QualityDecisionScope.SELECTED_UNITS, activeUnitCount, statusEligibleUnitCount));

    return new QualityBatchSummaryDto(
        row.getBatchId(),
        row.getBatchCode(),
        row.getProductId(),
        row.getProductUid(),
        row.getProductDisplayName(),
        ProductType.valueOf(row.getProductType()),
        row.getColorId(),
        row.getColorName(),
        row.getBatchCreatedAt(),
        counts.getOrDefault(QualityDisposition.PENDING_INSPECTION, 0L),
        counts.getOrDefault(QualityDisposition.RELEASED, 0L),
        counts.getOrDefault(QualityDisposition.QUARANTINED, 0L),
        counts.getOrDefault(QualityDisposition.NONCONFORMING, 0L),
        totalCount,
        fullLotCapability.allowed(),
        fullLotCapability.blockedReason(),
        selectedUnitsCapability.allowed(),
        selectedUnitsCapability.blockedReason());
  }

  public QualityDecisionOptionsDto getDecisionOptions() {
    return new QualityDecisionOptionsDto(
        java.util.Arrays.stream(QualityDecisionOutcome.values())
            .map(
                outcome ->
                    new QualityDecisionOutcomeOptionDto(
                        outcome,
                        QualityDecisionReasonPolicy.manualReasonRequired(outcome),
                        QualityDecisionReasonPolicy.manualReasons(outcome).stream()
                            .map(
                                reason ->
                                    new QualityDecisionReasonOptionDto(
                                        reason,
                                        QualityDecisionReasonPolicy.remarksRequired(reason)))
                            .toList()))
            .toList());
  }

  public Page<QualityDecisionUnitDto> getActiveUnits(UUID batchId, Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();
    Page<StockUnit> units =
        stockUnitRepository.findByTenantIdAndBatchIdAndIsActiveTrue(tenantId, batchId, pageable);
    var locationIds =
        units.getContent().stream()
            .map(StockUnit::getLocationId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<UUID, WarehouseLocationRef> locations =
        warehouseLocationPort.findLocationRefs(tenantId, locationIds).stream()
            .collect(Collectors.toMap(WarehouseLocationRef::id, location -> location));
    return units.map(unit -> mapper.toUnitDto(unit, locations.get(unit.getLocationId())));
  }

  public List<QualityRelocationTargetDto> getRelocationTargets() {
    return warehouseLocationPort
        .findQualityRelocationTargets(TenantContext.requireTenantId())
        .stream()
        .map(
            target ->
                new QualityRelocationTargetDto(
                    target.id(), target.code(), target.name(), target.path()))
        .toList();
  }
}
