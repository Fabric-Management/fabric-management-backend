package com.fabricmanagement.production.execution.stockunit.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.domain.port.WarehouseLocationPort;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitAuditLogRepository;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.qualitygrade.app.QualityGradeService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class StockUnitServiceCreationTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID BATCH_ID = UUID.randomUUID();

  @Mock private StockUnitRepository stockUnitRepository;
  @Mock private BatchRepository batchRepository;
  @Mock private QualityGradeService qualityGradeService;
  @Mock private StockUnitAuditLogRepository auditLogRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private WarehouseLocationPort warehouseLocationPort;
  @InjectMocks private StockUnitService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentUserId(UUID.randomUUID());
    Batch batch = new Batch();
    batch.setId(BATCH_ID);
    batch.setTenantId(TENANT_ID);
    batch.setStatus(BatchStatus.AVAILABLE);
    batch.setReservedQuantity(BigDecimal.ZERO);
    batch.setConsumedQuantity(BigDecimal.ZERO);
    when(batchRepository.findByIdAndTenantId(BATCH_ID, TENANT_ID)).thenReturn(Optional.of(batch));
    when(stockUnitRepository.countQualityDispositions(TENANT_ID, BATCH_ID))
        .thenReturn(List.of(pendingCount(1)));
    when(stockUnitRepository.save(any(StockUnit.class)))
        .thenAnswer(
            invocation -> {
              StockUnit unit = invocation.getArgument(0);
              if (unit.getId() == null) {
                unit.setId(UUID.randomUUID());
              }
              return unit;
            });
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void manualCreateCannotUseProductionSourceToBypassInspection() {
    StockUnit result =
        service.create(
            BATCH_ID,
            ProductType.FABRIC,
            "ROLL-MANUAL-1",
            null,
            PackageType.ROLL,
            new BigDecimal("30.000"),
            null,
            "KG",
            new BigDecimal("100.000"),
            "M",
            UUID.randomUUID(),
            StockUnitSourceType.PRODUCTION,
            UUID.randomUUID());

    assertThat(result.getQualityDisposition()).isEqualTo(QualityDisposition.PENDING_INSPECTION);
  }

  @Test
  void productionOutputBulkCreationStartsPendingInspection() {
    List<StockUnit> result =
        service.createBulk(
            BATCH_ID,
            List.of(
                new StockUnitService.CreateStockUnitRequest(
                    ProductType.FABRIC,
                    "ROLL-OUTPUT-1",
                    null,
                    PackageType.ROLL,
                    new BigDecimal("30.000"),
                    null,
                    "KG",
                    new BigDecimal("100.000"),
                    "M",
                    UUID.randomUUID(),
                    StockUnitSourceType.PRODUCTION,
                    UUID.randomUUID())),
            TenantContext.SYSTEM_ACTOR_ID);

    assertThat(result)
        .extracting(StockUnit::getQualityDisposition)
        .containsExactly(QualityDisposition.PENDING_INSPECTION);
    verify(batchRepository).save(argThat(batch -> batch.getStatus() == BatchStatus.PENDING_QC));
  }

  @Test
  void goodsReceiptBulkCreationStartsPendingInspection() {
    List<StockUnit> result =
        service.createBulk(
            BATCH_ID,
            List.of(
                new StockUnitService.CreateStockUnitRequest(
                    ProductType.FABRIC,
                    "ROLL-RECEIPT-1",
                    null,
                    PackageType.ROLL,
                    new BigDecimal("30.000"),
                    null,
                    "KG",
                    new BigDecimal("100.000"),
                    "M",
                    UUID.randomUUID(),
                    StockUnitSourceType.GOODS_RECEIPT,
                    UUID.randomUUID())),
            TenantContext.SYSTEM_ACTOR_ID);

    assertThat(result)
        .extracting(StockUnit::getQualityDisposition)
        .containsExactly(QualityDisposition.PENDING_INSPECTION);
  }

  private StockUnitRepository.QualityDispositionCount pendingCount(long count) {
    return new StockUnitRepository.QualityDispositionCount() {
      @Override
      public QualityDisposition getDisposition() {
        return QualityDisposition.PENDING_INSPECTION;
      }

      @Override
      public long getUnitCount() {
        return count;
      }
    };
  }
}
