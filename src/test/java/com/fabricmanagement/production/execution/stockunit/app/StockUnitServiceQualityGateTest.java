package com.fabricmanagement.production.execution.stockunit.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.domain.port.QcLocationValidationResult;
import com.fabricmanagement.production.execution.batch.domain.port.WarehouseLocationPort;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitAuditLog;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.execution.stockunit.domain.exception.QcRelocationException;
import com.fabricmanagement.production.execution.stockunit.domain.exception.StockUnitNotReleasedException;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitAuditLogRepository;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.qualitygrade.app.QualityGradeService;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class StockUnitServiceQualityGateTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID ACTOR_ID = UUID.randomUUID();
  private static final UUID BATCH_ID = UUID.randomUUID();

  @Mock private StockUnitRepository stockUnitRepository;
  @Mock private BatchRepository batchRepository;
  @Mock private QualityGradeService qualityGradeService;
  @Mock private StockUnitAuditLogRepository auditLogRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private WarehouseLocationPort warehouseLocationPort;
  @InjectMocks private StockUnitService service;

  @BeforeEach
  void setUpContext() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentUserId(ACTOR_ID);
    lenient()
        .when(stockUnitRepository.save(any(StockUnit.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void pendingUnitCannotBeConsumed() {
    StockUnit pending = unit(QualityDisposition.PENDING_INSPECTION, UUID.randomUUID());
    Batch batch = batch(BatchStatus.PENDING_QC);
    givenUnitAndBatch(pending, batch);

    assertThatThrownBy(() -> service.consume(pending.getId(), BigDecimal.ONE))
        .isInstanceOf(StockUnitNotReleasedException.class)
        .extracting(error -> ((StockUnitNotReleasedException) error).getErrorCode())
        .isEqualTo("STOCK_UNIT_NOT_RELEASED");

    verify(stockUnitRepository, never()).save(any());
    assertThat(batch.getConsumedQuantity()).isZero();
  }

  @Test
  void releasedUnitInMixedBatchCanBeConsumedWithoutOverwritingProjection() {
    StockUnit released = unit(QualityDisposition.RELEASED, UUID.randomUUID());
    Batch mixed = batch(BatchStatus.QUARANTINE);
    givenUnitAndBatch(released, mixed);

    StockUnit result = service.consume(released.getId(), new BigDecimal("2.000"));

    assertThat(result.getCurrentWeight()).isEqualByComparingTo("8.000");
    assertThat(result.getStatus()).isEqualTo(StockUnitStatus.PARTIAL);
    assertThat(mixed.getConsumedQuantity()).isEqualByComparingTo("2.000");
    assertThat(mixed.getStatus()).isEqualTo(BatchStatus.QUARANTINE);
  }

  @Test
  void qcRelocationPreservesStatusAndDispositionAndWritesReasonedAudit() {
    UUID source = UUID.randomUUID();
    UUID target = UUID.randomUUID();
    StockUnit quarantined = unit(QualityDisposition.QUARANTINED, source);
    quarantined.quarantine();
    when(stockUnitRepository.findById(quarantined.getId())).thenReturn(Optional.of(quarantined));
    when(warehouseLocationPort.validateQcLocation(target))
        .thenReturn(new QcLocationValidationResult(target, "QC-HOLD", true));

    StockUnit result = service.relocateForQuality(quarantined.getId(), target, "  Lab review  ");

    assertThat(result.getLocationId()).isEqualTo(target);
    assertThat(result.getPreviousLocationId()).isEqualTo(source);
    assertThat(result.getStatus()).isEqualTo(StockUnitStatus.QUARANTINE);
    assertThat(result.getQualityDisposition()).isEqualTo(QualityDisposition.QUARANTINED);
    ArgumentCaptor<StockUnitAuditLog> audit = ArgumentCaptor.forClass(StockUnitAuditLog.class);
    verify(auditLogRepository).save(audit.capture());
    assertThat(audit.getValue().getOperationType()).isEqualTo(StockUnitAuditLog.OP_QC_RELOCATE);
    assertThat(audit.getValue().getOldValue()).isEqualTo(source.toString());
    assertThat(audit.getValue().getNewValue()).isEqualTo(target.toString());
    assertThat(audit.getValue().getReason()).isEqualTo("Lab review");
  }

  @Test
  void invalidQcTargetIsRejectedBeforeMutation() {
    UUID source = UUID.randomUUID();
    UUID target = UUID.randomUUID();
    StockUnit pending = unit(QualityDisposition.PENDING_INSPECTION, source);
    when(stockUnitRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
    when(warehouseLocationPort.validateQcLocation(target))
        .thenReturn(new QcLocationValidationResult(target, "GENERAL", false));

    assertThatThrownBy(() -> service.relocateForQuality(pending.getId(), target, "Inspection"))
        .isInstanceOf(QcRelocationException.class)
        .extracting(error -> ((QcRelocationException) error).getErrorCode())
        .isEqualTo("QC_RELOCATE_TARGET_INVALID");

    assertThat(pending.getLocationId()).isEqualTo(source);
    verify(stockUnitRepository, never()).save(any());
  }

  @Test
  void disposedUnitIsRejectedBeforeQcLocationLookup() {
    StockUnit disposed = unit(QualityDisposition.NONCONFORMING, UUID.randomUUID());
    disposed.hold();
    disposed.dispose();
    when(stockUnitRepository.findById(disposed.getId())).thenReturn(Optional.of(disposed));

    assertThatThrownBy(
            () -> service.relocateForQuality(disposed.getId(), UUID.randomUUID(), "Move reject"))
        .isInstanceOf(QcRelocationException.class)
        .extracting(error -> ((QcRelocationException) error).getErrorCode())
        .isEqualTo("QC_RELOCATE_STATUS_INVALID");

    verify(warehouseLocationPort, never()).validateQcLocation(any());
  }

  @Test
  void releasedUnitMustUseStandardTransferPath() {
    StockUnit released = unit(QualityDisposition.RELEASED, UUID.randomUUID());
    when(stockUnitRepository.findById(released.getId())).thenReturn(Optional.of(released));

    assertThatThrownBy(
            () -> service.relocateForQuality(released.getId(), UUID.randomUUID(), "QC move"))
        .isInstanceOf(QcRelocationException.class)
        .extracting(error -> ((QcRelocationException) error).getErrorCode())
        .isEqualTo("QC_RELOCATE_RELEASED_UNIT");

    verify(warehouseLocationPort, never()).validateQcLocation(any());
  }

  @Test
  void relocationToCurrentLocationIsRejectedBeforeQcLocationLookup() {
    UUID currentLocation = UUID.randomUUID();
    StockUnit pending = unit(QualityDisposition.PENDING_INSPECTION, currentLocation);
    when(stockUnitRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

    assertThatThrownBy(
            () -> service.relocateForQuality(pending.getId(), currentLocation, "Remain in QC"))
        .isInstanceOf(QcRelocationException.class)
        .extracting(error -> ((QcRelocationException) error).getErrorCode())
        .isEqualTo("QC_RELOCATE_SAME_LOCATION");

    verify(warehouseLocationPort, never()).validateQcLocation(any());
  }

  private void givenUnitAndBatch(StockUnit unit, Batch batch) {
    when(stockUnitRepository.findById(unit.getId())).thenReturn(Optional.of(unit));
    when(batchRepository.findByIdAndTenantIdForUpdate(BATCH_ID, TENANT_ID))
        .thenReturn(Optional.of(batch));
  }

  private StockUnit unit(QualityDisposition disposition, UUID locationId) {
    StockUnit unit =
        StockUnit.create(
            TENANT_ID,
            BATCH_ID,
            ProductType.FABRIC,
            "ROLL-" + UUID.randomUUID(),
            null,
            PackageType.ROLL,
            new BigDecimal("10.000"),
            null,
            "KG",
            locationId,
            StockUnitSourceType.GOODS_RECEIPT,
            UUID.randomUUID(),
            disposition);
    unit.setId(UUID.randomUUID());
    return unit;
  }

  private Batch batch(BatchStatus status) {
    Batch batch =
        Batch.builder()
            .productId(UUID.randomUUID())
            .productType(ProductType.FABRIC)
            .batchCode("LOT-QC-MIXED")
            .quantity(new BigDecimal("20.000"))
            .reservedQuantity(BigDecimal.ZERO)
            .consumedQuantity(BigDecimal.ZERO)
            .wasteQuantity(BigDecimal.ZERO)
            .unit("KG")
            .status(status)
            .build();
    batch.setId(BATCH_ID);
    batch.setTenantId(TENANT_ID);
    return batch;
  }
}
