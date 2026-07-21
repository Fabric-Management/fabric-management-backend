package com.fabricmanagement.production.quality.decision.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.quality.decision.domain.QualityDecision;
import com.fabricmanagement.production.quality.decision.dto.QualityQueueItemDto;
import com.fabricmanagement.production.quality.decision.infra.repository.QualityDecisionRepository;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class QualityDecisionQueryService {

  private static final Set<StockUnitStatus> DECISION_ELIGIBLE_STATUSES =
      EnumSet.of(
          StockUnitStatus.AVAILABLE,
          StockUnitStatus.PARTIAL,
          StockUnitStatus.QUARANTINE,
          StockUnitStatus.ON_HOLD);

  private final QualityDecisionRepository decisionRepository;
  private final StockUnitRepository stockUnitRepository;

  public QualityDecisionQueryService(
      QualityDecisionRepository decisionRepository, StockUnitRepository stockUnitRepository) {
    this.decisionRepository = decisionRepository;
    this.stockUnitRepository = stockUnitRepository;
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

  public Page<StockUnit> getUnits(UUID batchId, Pageable pageable) {
    return stockUnitRepository.findByTenantIdAndBatchIdAndIsActiveTrueAndStatusIn(
        TenantContext.requireTenantId(), batchId, DECISION_ELIGIBLE_STATUSES, pageable);
  }
}
