package com.fabricmanagement.production.quality.decision.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.quality.decision.infra.repository.QualityDecisionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class QualityDecisionQueryServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");

  @Mock private QualityDecisionRepository decisionRepository;
  @Mock private StockUnitRepository stockUnitRepository;
  @Mock private BatchRepository batchRepository;

  private QualityDecisionQueryService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    service =
        new QualityDecisionQueryService(decisionRepository, stockUnitRepository, batchRepository);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void mapsTheSelfContainedQueueProjectionWithOneRepositoryCall() {
    var pageable = PageRequest.of(0, 20);
    var batchId = UUID.randomUUID();
    var productId = UUID.randomUUID();
    var colorId = UUID.randomUUID();
    var createdAt = Instant.parse("2026-07-21T08:00:00Z");
    var row = mock(StockUnitRepository.QualityQueueRow.class);
    when(row.getBatchId()).thenReturn(batchId);
    when(row.getBatchCode()).thenReturn("LOT-QC-001");
    when(row.getProductId()).thenReturn(productId);
    when(row.getProductUid()).thenReturn("SYS-MAT-000001");
    when(row.getProductType()).thenReturn(ProductType.FIBER.name());
    when(row.getProductDisplayName()).thenReturn("Cotton (100%)");
    when(row.getColorId()).thenReturn(colorId);
    when(row.getColorName()).thenReturn("Navy");
    when(row.getSupplierBatchCode()).thenReturn("SUP-LOT-9");
    when(row.getPendingUnitCount()).thenReturn(3L);
    when(row.getBatchCreatedAt()).thenReturn(createdAt);
    when(stockUnitRepository.findQualityQueue(TENANT_ID, pageable))
        .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

    var result = service.getQueue(pageable);

    assertThat(result)
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.batchId()).isEqualTo(batchId);
              assertThat(item.batchCode()).isEqualTo("LOT-QC-001");
              assertThat(item.productId()).isEqualTo(productId);
              assertThat(item.productUid()).isEqualTo("SYS-MAT-000001");
              assertThat(item.productType()).isEqualTo(ProductType.FIBER);
              assertThat(item.productDisplayName()).isEqualTo("Cotton (100%)");
              assertThat(item.colorId()).isEqualTo(colorId);
              assertThat(item.colorName()).isEqualTo("Navy");
              assertThat(item.supplierBatchCode()).isEqualTo("SUP-LOT-9");
              assertThat(item.pendingUnitCount()).isEqualTo(3);
              assertThat(item.batchCreatedAt()).isEqualTo(createdAt);
            });
    verify(stockUnitRepository).findQualityQueue(TENANT_ID, pageable);
  }

  @Test
  void mapsTheTenantSafeBatchSummaryAndAllDispositionCounts() {
    var batchId = UUID.randomUUID();
    var productId = UUID.randomUUID();
    var colorId = UUID.randomUUID();
    var createdAt = Instant.parse("2026-07-21T08:00:00Z");
    var row = mock(BatchRepository.QualityBatchSummaryRow.class);
    when(row.getBatchId()).thenReturn(batchId);
    when(row.getBatchCode()).thenReturn("LOT-QC-001");
    when(row.getProductId()).thenReturn(productId);
    when(row.getProductUid()).thenReturn("SYS-MAT-000001");
    when(row.getProductDisplayName()).thenReturn("Cotton (100%)");
    when(row.getProductType()).thenReturn(ProductType.FIBER.name());
    when(row.getColorId()).thenReturn(colorId);
    when(row.getColorName()).thenReturn("Navy");
    when(row.getBatchCreatedAt()).thenReturn(createdAt);
    when(batchRepository.findQualityBatchSummary(TENANT_ID, batchId)).thenReturn(Optional.of(row));
    var dispositionCounts =
        List.of(
            dispositionCount(QualityDisposition.PENDING_INSPECTION, 2),
            dispositionCount(QualityDisposition.RELEASED, 3),
            dispositionCount(QualityDisposition.QUARANTINED, 1),
            dispositionCount(QualityDisposition.NONCONFORMING, 4));
    when(stockUnitRepository.countQualityDispositions(TENANT_ID, batchId))
        .thenReturn(dispositionCounts);

    var result = service.getSummary(batchId);

    assertThat(result.batchId()).isEqualTo(batchId);
    assertThat(result.productId()).isEqualTo(productId);
    assertThat(result.productDisplayName()).isEqualTo("Cotton (100%)");
    assertThat(result.colorId()).isEqualTo(colorId);
    assertThat(result.pendingInspectionCount()).isEqualTo(2);
    assertThat(result.releasedCount()).isEqualTo(3);
    assertThat(result.quarantinedCount()).isEqualTo(1);
    assertThat(result.nonconformingCount()).isEqualTo(4);
    assertThat(result.totalCount()).isEqualTo(10);
  }

  @Test
  void summaryRejectsMissingOrCrossTenantBatchWithoutCountingUnits() {
    var batchId = UUID.randomUUID();
    when(batchRepository.findQualityBatchSummary(TENANT_ID, batchId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getSummary(batchId)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void activeUnitsReadDoesNotApplyTheDecisionStatusFilter() {
    var batchId = UUID.randomUUID();
    var pageable = PageRequest.of(0, 20);
    var stockUnit = mock(StockUnit.class);
    when(stockUnitRepository.findByTenantIdAndBatchIdAndIsActiveTrue(TENANT_ID, batchId, pageable))
        .thenReturn(new PageImpl<>(List.of(stockUnit), pageable, 1));

    assertThat(service.getActiveUnits(batchId, pageable)).containsExactly(stockUnit);
    verify(stockUnitRepository)
        .findByTenantIdAndBatchIdAndIsActiveTrue(TENANT_ID, batchId, pageable);
  }

  private static StockUnitRepository.QualityDispositionCount dispositionCount(
      QualityDisposition disposition, long unitCount) {
    var count = mock(StockUnitRepository.QualityDispositionCount.class);
    when(count.getDisposition()).thenReturn(disposition);
    when(count.getUnitCount()).thenReturn(unitCount);
    return count;
  }
}
