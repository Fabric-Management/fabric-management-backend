package com.fabricmanagement.production.quality.decision.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.quality.decision.domain.QualityDecision;
import com.fabricmanagement.production.quality.decision.dto.QualityBatchSummaryDto;
import com.fabricmanagement.production.quality.decision.dto.QualityQueueItemDto;
import com.fabricmanagement.production.quality.decision.infra.repository.QualityDecisionRepository;
import java.util.Map;
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

  public QualityDecisionQueryService(
      QualityDecisionRepository decisionRepository,
      StockUnitRepository stockUnitRepository,
      BatchRepository batchRepository) {
    this.decisionRepository = decisionRepository;
    this.stockUnitRepository = stockUnitRepository;
    this.batchRepository = batchRepository;
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
        totalCount);
  }

  public Page<StockUnit> getActiveUnits(UUID batchId, Pageable pageable) {
    return stockUnitRepository.findByTenantIdAndBatchIdAndIsActiveTrue(
        TenantContext.requireTenantId(), batchId, pageable);
  }
}
