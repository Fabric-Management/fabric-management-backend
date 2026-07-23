package com.fabricmanagement.production.quality.decision.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.domain.port.QualityRelocationTarget;
import com.fabricmanagement.production.execution.batch.domain.port.WarehouseLocationPort;
import com.fabricmanagement.production.execution.batch.domain.port.WarehouseLocationRef;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.quality.decision.domain.ManualQualityReasonCode;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionBlockedReason;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionOutcome;
import com.fabricmanagement.production.quality.decision.domain.QualityReasonCode;
import com.fabricmanagement.production.quality.decision.infra.repository.QualityDecisionRepository;
import com.fabricmanagement.production.quality.decision.mapper.QualityDecisionMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
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
  @Mock private WarehouseLocationPort warehouseLocationPort;

  private QualityDecisionQueryService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    service =
        new QualityDecisionQueryService(
            decisionRepository,
            stockUnitRepository,
            batchRepository,
            warehouseLocationPort,
            Mappers.getMapper(QualityDecisionMapper.class));
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
    when(row.getStatus()).thenReturn(BatchStatus.PENDING_QC.name());
    when(row.getReservedQuantity()).thenReturn(BigDecimal.ZERO);
    when(row.getConsumedQuantity()).thenReturn(BigDecimal.ZERO);
    when(batchRepository.findQualityBatchSummary(TENANT_ID, batchId)).thenReturn(Optional.of(row));
    var dispositionCounts =
        List.of(
            dispositionCount(QualityDisposition.PENDING_INSPECTION, 2),
            dispositionCount(QualityDisposition.RELEASED, 3),
            dispositionCount(QualityDisposition.QUARANTINED, 1),
            dispositionCount(QualityDisposition.NONCONFORMING, 4));
    when(stockUnitRepository.countQualityDispositions(TENANT_ID, batchId))
        .thenReturn(dispositionCounts);
    when(stockUnitRepository.countByTenantIdAndBatchIdAndIsActiveTrue(TENANT_ID, batchId))
        .thenReturn(10L);
    when(stockUnitRepository.countByTenantIdAndBatchIdAndIsActiveTrueAndStatusIn(
            eq(TENANT_ID), eq(batchId), anyCollection()))
        .thenReturn(10L);

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
    assertThat(result.fullLotDecisionAllowed()).isTrue();
    assertThat(result.fullLotBlockedReason()).isNull();
    assertThat(result.selectedUnitsDecisionAllowed()).isTrue();
    assertThat(result.selectedUnitsBlockedReason()).isNull();
  }

  @Test
  void summaryExposesTheBatchReasonBeforeThePopulationReason() {
    var batchId = UUID.randomUUID();
    var row = mock(BatchRepository.QualityBatchSummaryRow.class);
    when(row.getProductType()).thenReturn(ProductType.FIBER.name());
    when(row.getStatus()).thenReturn(BatchStatus.IN_PROGRESS.name());
    when(row.getReservedQuantity()).thenReturn(BigDecimal.ZERO);
    when(row.getConsumedQuantity()).thenReturn(BigDecimal.ONE);
    when(batchRepository.findQualityBatchSummary(TENANT_ID, batchId)).thenReturn(Optional.of(row));
    when(stockUnitRepository.countQualityDispositions(TENANT_ID, batchId)).thenReturn(List.of());
    when(stockUnitRepository.countByTenantIdAndBatchIdAndIsActiveTrue(TENANT_ID, batchId))
        .thenReturn(0L);
    when(stockUnitRepository.countByTenantIdAndBatchIdAndIsActiveTrueAndStatusIn(
            eq(TENANT_ID), eq(batchId), anyCollection()))
        .thenReturn(0L);

    var result = service.getSummary(batchId);

    assertThat(result.fullLotDecisionAllowed()).isFalse();
    assertThat(result.fullLotBlockedReason())
        .isEqualTo(QualityDecisionBlockedReason.BATCH_CONSUMED);
    assertThat(result.selectedUnitsDecisionAllowed()).isFalse();
    assertThat(result.selectedUnitsBlockedReason())
        .isEqualTo(QualityDecisionBlockedReason.NO_ELIGIBLE_UNITS);
  }

  @Test
  void returnsDeterministicManualDecisionOptions() {
    var options = service.getDecisionOptions().options();

    assertThat(options)
        .extracting(option -> option.outcome())
        .containsExactly(
            QualityDecisionOutcome.RELEASED,
            QualityDecisionOutcome.QUARANTINED,
            QualityDecisionOutcome.NONCONFORMING);
    assertThat(options.getFirst().reasonRequired()).isFalse();
    assertThat(options.getFirst().reasons()).isEmpty();
    assertThat(options.get(1).reasons())
        .extracting(reason -> reason.code())
        .containsExactly(
            ManualQualityReasonCode.SUSPECTED_DAMAGE,
            ManualQualityReasonCode.AWAITING_LAB,
            ManualQualityReasonCode.SUPPLIER_DISPUTE,
            ManualQualityReasonCode.SHADE_CHECK,
            ManualQualityReasonCode.OTHER);
    assertThat(options.getLast().reasons().getLast().remarksRequired()).isTrue();
    assertThat(options.stream().flatMap(option -> option.reasons().stream()))
        .extracting(reason -> reason.code().name())
        .doesNotContain(
            QualityReasonCode.SYSTEM_QC_PASSED.name(),
            QualityReasonCode.SYSTEM_QC_REJECTED.name(),
            QualityReasonCode.MIGRATION_BASELINE.name());
  }

  @Test
  void summaryRejectsMissingOrCrossTenantBatchWithoutCountingUnits() {
    var batchId = UUID.randomUUID();
    when(batchRepository.findQualityBatchSummary(TENANT_ID, batchId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getSummary(batchId)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void activeUnitsReadEnrichesLocationsWithOneBulkLookup() {
    var batchId = UUID.randomUUID();
    var pageable = PageRequest.of(0, 20);
    var locationId = UUID.randomUUID();
    var stockUnit =
        StockUnit.builder()
            .barcode("ROLL-001")
            .locationId(locationId)
            .status(StockUnitStatus.AVAILABLE)
            .qualityDisposition(QualityDisposition.PENDING_INSPECTION)
            .build();
    var locationRef = new WarehouseLocationRef(locationId, "QC-01", "QC Hold");
    when(stockUnitRepository.findByTenantIdAndBatchIdAndIsActiveTrue(TENANT_ID, batchId, pageable))
        .thenReturn(new PageImpl<>(List.of(stockUnit), pageable, 1));
    when(warehouseLocationPort.findLocationRefs(TENANT_ID, Set.of(locationId)))
        .thenReturn(List.of(locationRef));

    assertThat(service.getActiveUnits(batchId, pageable))
        .singleElement()
        .satisfies(
            unit -> {
              assertThat(unit.locationId()).isEqualTo(locationId);
              assertThat(unit.locationCode()).isEqualTo("QC-01");
              assertThat(unit.locationName()).isEqualTo("QC Hold");
              assertThat(unit.qualityRelocationAllowed()).isTrue();
            });
    verify(stockUnitRepository)
        .findByTenantIdAndBatchIdAndIsActiveTrue(TENANT_ID, batchId, pageable);
    verify(warehouseLocationPort).findLocationRefs(TENANT_ID, Set.of(locationId));
  }

  @Test
  void activeUnitsPreserveNullLocationWithoutLookupPerUnit() {
    var batchId = UUID.randomUUID();
    var pageable = PageRequest.of(0, 20);
    var stockUnit =
        StockUnit.builder()
            .barcode("ROLL-NO-LOCATION")
            .status(StockUnitStatus.DISPOSED)
            .qualityDisposition(QualityDisposition.NONCONFORMING)
            .build();
    when(stockUnitRepository.findByTenantIdAndBatchIdAndIsActiveTrue(TENANT_ID, batchId, pageable))
        .thenReturn(new PageImpl<>(List.of(stockUnit), pageable, 1));
    when(warehouseLocationPort.findLocationRefs(TENANT_ID, Set.of())).thenReturn(List.of());

    assertThat(service.getActiveUnits(batchId, pageable))
        .singleElement()
        .satisfies(
            unit -> {
              assertThat(unit.locationId()).isNull();
              assertThat(unit.locationCode()).isNull();
              assertThat(unit.locationName()).isNull();
              assertThat(unit.qualityRelocationAllowed()).isFalse();
            });
    verify(warehouseLocationPort).findLocationRefs(TENANT_ID, Set.of());
  }

  @Test
  void mapsDeterministicQualityRelocationTargetsFromThePort() {
    var first = new QualityRelocationTarget(UUID.randomUUID(), "QC-A", "QC Alpha", "SITE/QC-A");
    var second = new QualityRelocationTarget(UUID.randomUUID(), "QC-B", "QC Beta", "SITE/QC-B");
    when(warehouseLocationPort.findQualityRelocationTargets(TENANT_ID))
        .thenReturn(List.of(first, second));

    assertThat(service.getRelocationTargets())
        .extracting(target -> target.code())
        .containsExactly("QC-A", "QC-B");
    verify(warehouseLocationPort).findQualityRelocationTargets(TENANT_ID);
  }

  private static StockUnitRepository.QualityDispositionCount dispositionCount(
      QualityDisposition disposition, long unitCount) {
    var count = mock(StockUnitRepository.QualityDispositionCount.class);
    when(count.getDisposition()).thenReturn(disposition);
    when(count.getUnitCount()).thenReturn(unitCount);
    return count;
  }
}
