package com.fabricmanagement.production.execution.batch.api.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.batch.app.BatchCommitmentQuantityService;
import com.fabricmanagement.production.execution.batch.app.BatchPrimaryMeasureService;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchSourceType;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.domain.PrimaryMeasure;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchLotQuantityIntentRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitSoftHoldRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.qualitygrade.api.query.QualityGradeQueryService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductionSalesLotQueryServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  @Mock private BatchRepository batchRepository;
  @Mock private BatchLotQuantityIntentRepository lotIntentRepository;
  @Mock private StockUnitRepository stockUnitRepository;
  @Mock private StockUnitSoftHoldRepository softHoldRepository;
  @Mock private QualityGradeQueryService qualityGradeQueryService;
  @Mock private BatchPrimaryMeasureService primaryMeasureService;
  @Mock private BatchCommitmentQuantityService commitmentQuantityService;

  private ProductionSalesLotQueryService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    service =
        new ProductionSalesLotQueryService(
            batchRepository,
            lotIntentRepository,
            stockUnitRepository,
            softHoldRepository,
            qualityGradeQueryService,
            primaryMeasureService,
            commitmentQuantityService);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void listSaleableLots_resolvesColorsWithOneBulkJoinAndLeavesNullColorUnmapped() {
    Batch colored = batch("LOT-COLOR", UUID.randomUUID());
    Batch uncolored = batch("LOT-NULL", null);
    List<UUID> batchIds = List.of(colored.getId(), uncolored.getId());
    BatchRepository.BatchColorProjection projection =
        mock(BatchRepository.BatchColorProjection.class);
    when(projection.getBatchId()).thenReturn(colored.getId());
    when(projection.getColorId()).thenReturn(colored.getColorId());
    when(projection.getColorCode()).thenReturn("NAVY-01");
    when(projection.getColorName()).thenReturn("Navy");
    when(projection.getColorHex()).thenReturn("#1F2A44");

    when(batchRepository.findByTenantIdAndStatusIn(eq(TENANT_ID), any()))
        .thenReturn(List.of(colored, uncolored));
    when(stockUnitRepository.findByTenantIdAndBatchIdInAndIsActiveTrue(TENANT_ID, batchIds))
        .thenReturn(List.of());
    when(softHoldRepository.countActiveByStockUnitIds(TENANT_ID, List.of())).thenReturn(Map.of());
    when(lotIntentRepository.findActiveByBatchIds(TENANT_ID, batchIds, null)).thenReturn(List.of());
    when(batchRepository.findColorReferencesByBatchIds(TENANT_ID, batchIds))
        .thenReturn(List.of(projection));
    when(primaryMeasureService.resolve(any(Batch.class)))
        .thenReturn(new BatchPrimaryMeasureService.Resolution(PrimaryMeasure.LENGTH, "M"));
    when(primaryMeasureService.toCanonical(any(), any(), any()))
        .thenReturn(java.util.Optional.of(BigDecimal.TEN));
    when(commitmentQuantityService.summarize(TENANT_ID, List.of(colored, uncolored), null))
        .thenReturn(
            Map.of(
                colored.getId(),
                new BatchCommitmentQuantityService.Summary(
                    BigDecimal.ZERO, BigDecimal.ZERO, List.of()),
                uncolored.getId(),
                new BatchCommitmentQuantityService.Summary(
                    BigDecimal.ZERO, BigDecimal.ZERO, List.of())));

    var lots = service.listSaleableLots();

    assertThat(lots).hasSize(2);
    assertThat(lots.get(0).colour().id()).isEqualTo(colored.getColorId());
    assertThat(lots.get(0).colour().code()).isEqualTo("NAVY-01");
    assertThat(lots.get(1).colour()).isNull();
    verify(batchRepository).findColorReferencesByBatchIds(TENANT_ID, batchIds);
  }

  @Test
  void listSaleableLots_flagsAndClampsNegativeFreeQuantity() {
    Batch batch = batch("LOT-OVER", null);
    List<UUID> batchIds = List.of(batch.getId());
    when(batchRepository.findByTenantIdAndStatusIn(eq(TENANT_ID), any()))
        .thenReturn(List.of(batch));
    when(stockUnitRepository.findByTenantIdAndBatchIdInAndIsActiveTrue(TENANT_ID, batchIds))
        .thenReturn(List.of());
    when(softHoldRepository.countActiveByStockUnitIds(TENANT_ID, List.of())).thenReturn(Map.of());
    when(lotIntentRepository.findActiveByBatchIds(TENANT_ID, batchIds, null)).thenReturn(List.of());
    when(batchRepository.findColorReferencesByBatchIds(TENANT_ID, batchIds)).thenReturn(List.of());
    when(primaryMeasureService.resolve(batch))
        .thenReturn(new BatchPrimaryMeasureService.Resolution(PrimaryMeasure.LENGTH, "M"));
    when(primaryMeasureService.toCanonical(any(), any(), any()))
        .thenReturn(java.util.Optional.of(BigDecimal.TEN));
    when(commitmentQuantityService.summarize(TENANT_ID, List.of(batch), null))
        .thenReturn(
            Map.of(
                batch.getId(),
                new BatchCommitmentQuantityService.Summary(
                    new BigDecimal("15"), BigDecimal.ZERO, List.of())));

    var lot = service.listSaleableLots().getFirst();

    assertThat(lot.freeQuantity()).isZero();
    assertThat(lot.overCommitted()).isTrue();
  }

  private Batch batch(String code, UUID colorId) {
    Batch batch =
        Batch.builder()
            .productId(UUID.randomUUID())
            .productType(ProductType.FABRIC)
            .colorId(colorId)
            .batchCode(code)
            .quantity(new BigDecimal("10"))
            .unit("KG")
            .status(BatchStatus.AVAILABLE)
            .sourceType(BatchSourceType.INITIAL_STOCK)
            .build();
    batch.setId(UUID.randomUUID());
    batch.setTenantId(TENANT_ID);
    batch.setIsActive(true);
    return batch;
  }
}
