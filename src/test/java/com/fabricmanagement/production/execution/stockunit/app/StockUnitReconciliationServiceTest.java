package com.fabricmanagement.production.execution.stockunit.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.production.execution.batch.app.BatchPrimaryMeasureService;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.domain.PrimaryMeasure;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class StockUnitReconciliationServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  @Mock private TenantQueryPort tenantQueryPort;
  @Mock private BatchRepository batchRepository;
  @Mock private StockUnitRepository stockUnitRepository;
  @Mock private BatchPrimaryMeasureService primaryMeasureService;

  private StockUnitReconciliationService service;

  @BeforeEach
  void setUp() {
    service =
        new StockUnitReconciliationService(
            tenantQueryPort, batchRepository, stockUnitRepository, primaryMeasureService);
    when(tenantQueryPort.findAllActiveTenants())
        .thenReturn(List.of(new TenantReference(TENANT_ID, "TEST", "Test Tenant", "TENANT")));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void skipsNonWeightAndUnsupportedBatchesWithoutInterruptingWeightReconciliation(
      CapturedOutput output) {
    Batch fabric = batch("FABRIC-1", ProductType.FABRIC, "M", "10", "0");
    Batch yarn = batch("YARN-1", ProductType.YARN, "KG", "8", "0");
    Batch chemical = batch("CHEM-1", ProductType.CHEMICAL, "L", "4", "0");
    Batch fiber = batch("FIBER-1", ProductType.FIBER, "KG", "6", "1");
    Batch consumable = batch("CONS-1", ProductType.CONSUMABLE, "PCS", "3", "0");
    when(batchRepository.findByTenantIdAndStatusIn(eq(TENANT_ID), anyCollection()))
        .thenReturn(List.of(fabric, yarn, chemical, fiber, consumable));
    when(primaryMeasureService.primaryMeasure(ProductType.FABRIC))
        .thenReturn(PrimaryMeasure.LENGTH);
    when(primaryMeasureService.primaryMeasure(ProductType.YARN)).thenReturn(PrimaryMeasure.WEIGHT);
    when(primaryMeasureService.primaryMeasure(ProductType.CHEMICAL))
        .thenThrow(new IllegalArgumentException("Unsupported product type: CHEMICAL"));
    when(primaryMeasureService.primaryMeasure(ProductType.FIBER)).thenReturn(PrimaryMeasure.WEIGHT);
    when(primaryMeasureService.primaryMeasure(ProductType.CONSUMABLE))
        .thenThrow(new IllegalArgumentException("Unsupported product type: CONSUMABLE"));
    when(stockUnitRepository.sumCurrentWeightByBatchId(
            TENANT_ID, yarn.getId(), StockUnitStatus.DISPOSED))
        .thenReturn(new BigDecimal("8"));
    when(stockUnitRepository.sumCurrentWeightByBatchId(
            TENANT_ID, fiber.getId(), StockUnitStatus.DISPOSED))
        .thenReturn(new BigDecimal("5"));
    ReflectionTestUtils.setField(service, "autoFix", true);

    service.reconcileAllTenants();

    verify(stockUnitRepository)
        .sumCurrentWeightByBatchId(TENANT_ID, yarn.getId(), StockUnitStatus.DISPOSED);
    verify(stockUnitRepository)
        .sumCurrentWeightByBatchId(TENANT_ID, fiber.getId(), StockUnitStatus.DISPOSED);
    verify(stockUnitRepository, never())
        .sumCurrentWeightByBatchId(TENANT_ID, fabric.getId(), StockUnitStatus.DISPOSED);
    verify(stockUnitRepository, never())
        .sumCurrentWeightByBatchId(TENANT_ID, chemical.getId(), StockUnitStatus.DISPOSED);
    verify(stockUnitRepository, never())
        .sumCurrentWeightByBatchId(TENANT_ID, consumable.getId(), StockUnitStatus.DISPOSED);
    verify(batchRepository, never()).save(any(Batch.class));
    assertThat(output.getAll())
        .contains("Reconciliation skipped (dimension mismatch): batch=FABRIC-1")
        .contains("Reconciliation skipped (unsupported primary measure): batch=CHEM-1")
        .contains("Reconciliation skipped (unsupported primary measure): batch=CONS-1")
        .contains("no discrepancies and 3 measure skips");
  }

  @Test
  void weightBatchStillReconcilesAndAutoFixes() {
    Batch yarn = batch("YARN-2", ProductType.YARN, "KG", "10", "2");
    when(batchRepository.findByTenantIdAndStatusIn(eq(TENANT_ID), anyCollection()))
        .thenReturn(List.of(yarn));
    when(primaryMeasureService.primaryMeasure(ProductType.YARN)).thenReturn(PrimaryMeasure.WEIGHT);
    when(stockUnitRepository.sumCurrentWeightByBatchId(
            TENANT_ID, yarn.getId(), StockUnitStatus.DISPOSED))
        .thenReturn(new BigDecimal("7"));
    ReflectionTestUtils.setField(service, "autoFix", true);

    service.reconcileAllTenants();

    assertThat(yarn.getConsumedQuantity()).isEqualByComparingTo("3");
    verify(batchRepository).save(yarn);
  }

  private static Batch batch(
      String code, ProductType productType, String unit, String quantity, String consumedQuantity) {
    Batch batch =
        Batch.builder()
            .productId(UUID.randomUUID())
            .productType(productType)
            .batchCode(code)
            .quantity(new BigDecimal(quantity))
            .reservedQuantity(BigDecimal.ZERO)
            .consumedQuantity(new BigDecimal(consumedQuantity))
            .wasteQuantity(BigDecimal.ZERO)
            .unit(unit)
            .status(BatchStatus.AVAILABLE)
            .build();
    batch.setId(UUID.randomUUID());
    batch.setTenantId(TENANT_ID);
    return batch;
  }
}
