package com.fabricmanagement.production.quality.decision.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.quality.decision.domain.QualityDecision;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionOutcome;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionScope;
import com.fabricmanagement.production.quality.decision.domain.QualityReasonCode;
import com.fabricmanagement.production.quality.decision.domain.exception.QualityDecisionException;
import com.fabricmanagement.production.quality.decision.infra.repository.QualityDecisionRepository;
import com.fabricmanagement.production.quality.decision.infra.repository.QualityDecisionUnitRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QualityDecisionServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID ACTOR_ID = UUID.randomUUID();
  private static final UUID BATCH_ID = UUID.randomUUID();

  @Mock private BatchRepository batchRepository;
  @Mock private StockUnitRepository stockUnitRepository;
  @Mock private QualityDecisionRepository decisionRepository;
  @Mock private QualityDecisionUnitRepository decisionUnitRepository;
  @InjectMocks private QualityDecisionService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentUserId(ACTOR_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void fullLotReleaseWritesLedgerUpdatesEveryUnitAndProjectsAvailable() {
    Batch batch = batch(BatchStatus.PENDING_QC);
    StockUnit first = unit("ROLL-1");
    StockUnit second = unit("ROLL-2");
    when(batchRepository.findByIdAndTenantIdForUpdate(BATCH_ID, TENANT_ID))
        .thenReturn(Optional.of(batch));
    when(stockUnitRepository.countByTenantIdAndBatchIdAndIsActiveTrue(TENANT_ID, BATCH_ID))
        .thenReturn(2L);
    when(stockUnitRepository.lockDecisionPopulation(eq(TENANT_ID), eq(BATCH_ID), anyCollection()))
        .thenReturn(List.of(first, second));
    when(decisionRepository.findMaxSeq(TENANT_ID, BATCH_ID)).thenReturn(4L);
    when(stockUnitRepository.applyQualityDisposition(
            eq(TENANT_ID),
            eq(BATCH_ID),
            anyCollection(),
            anyCollection(),
            eq(QualityDisposition.RELEASED)))
        .thenReturn(2);
    when(stockUnitRepository.countQualityDispositions(TENANT_ID, BATCH_ID))
        .thenReturn(List.of(dispositionCount(QualityDisposition.RELEASED, 2)));

    QualityDecision result =
        service.recordDecision(
            TrustedDecisionContext.manual(ACTOR_ID),
            new QualityDecisionCommand(
                BATCH_ID,
                QualityDecisionScope.FULL_LOT,
                QualityDecisionOutcome.RELEASED,
                null,
                "accepted",
                Set.of(),
                null));

    assertThat(result.getSeq()).isEqualTo(5);
    assertThat(result.getActorId()).isEqualTo(ACTOR_ID);
    assertThat(batch.getStatus()).isEqualTo(BatchStatus.AVAILABLE);
    verify(decisionRepository).save(result);
    verify(decisionUnitRepository).saveAll(any());
    verify(batchRepository).save(batch);
  }

  @Test
  void selectedDecisionRejectsUnitOutsideEligiblePopulation() {
    Batch batch = batch(BatchStatus.PENDING_QC);
    UUID requestedId = UUID.randomUUID();
    when(batchRepository.findByIdAndTenantIdForUpdate(BATCH_ID, TENANT_ID))
        .thenReturn(Optional.of(batch));
    when(stockUnitRepository.lockSelectedDecisionPopulation(
            eq(TENANT_ID), eq(BATCH_ID), eq(Set.of(requestedId)), anyCollection()))
        .thenReturn(List.of());

    assertThatThrownBy(
            () ->
                service.recordDecision(
                    TrustedDecisionContext.manual(ACTOR_ID),
                    new QualityDecisionCommand(
                        BATCH_ID,
                        QualityDecisionScope.SELECTED_UNITS,
                        QualityDecisionOutcome.QUARANTINED,
                        QualityReasonCode.AWAITING_LAB,
                        null,
                        Set.of(requestedId),
                        null)))
        .isInstanceOf(QualityDecisionException.class)
        .hasMessageContaining("Selected StockUnits");
    verify(decisionRepository, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(
      value = BatchStatus.class,
      names = {"RESERVED", "IN_PROGRESS", "ON_HOLD", "DEPLETED", "RETURNED", "DESTROYED"})
  void activeAndTerminalBatchesCannotBeRewrittenByQualityProjection(BatchStatus status) {
    Batch batch = batch(status);
    when(batchRepository.findByIdAndTenantIdForUpdate(BATCH_ID, TENANT_ID))
        .thenReturn(Optional.of(batch));

    assertThatThrownBy(() -> service.releaseFromQc(BATCH_ID))
        .isInstanceOf(QualityDecisionException.class)
        .hasMessageContaining(status.name());
    verify(stockUnitRepository, never()).lockDecisionPopulation(any(), any(), anyCollection());
  }

  @Test
  void selectedDecisionUpdatesOnlyFrozenUnitsAndProjectsMixedLotToQuarantine() {
    Batch batch = batch(BatchStatus.PENDING_QC);
    StockUnit selected = unit("ROLL-SELECTED");
    UUID selectedId = selected.getId();
    when(batchRepository.findByIdAndTenantIdForUpdate(BATCH_ID, TENANT_ID))
        .thenReturn(Optional.of(batch));
    when(stockUnitRepository.lockSelectedDecisionPopulation(
            eq(TENANT_ID), eq(BATCH_ID), eq(Set.of(selectedId)), anyCollection()))
        .thenReturn(List.of(selected));
    when(decisionRepository.findMaxSeq(TENANT_ID, BATCH_ID)).thenReturn(0L);
    when(stockUnitRepository.applyQualityDisposition(
            eq(TENANT_ID),
            eq(BATCH_ID),
            eq(List.of(selectedId)),
            anyCollection(),
            eq(QualityDisposition.RELEASED)))
        .thenReturn(1);
    when(stockUnitRepository.countQualityDispositions(TENANT_ID, BATCH_ID))
        .thenReturn(
            List.of(
                dispositionCount(QualityDisposition.RELEASED, 1),
                dispositionCount(QualityDisposition.PENDING_INSPECTION, 1)));

    service.recordDecision(
        TrustedDecisionContext.manual(ACTOR_ID),
        new QualityDecisionCommand(
            BATCH_ID,
            QualityDecisionScope.SELECTED_UNITS,
            QualityDecisionOutcome.RELEASED,
            null,
            null,
            Set.of(selectedId),
            null));

    assertThat(batch.getStatus()).isEqualTo(BatchStatus.QUARANTINE);
    verify(decisionUnitRepository).saveAll(any());
  }

  @Test
  void selectedDecisionCanInspectUntouchedUnitAfterEarlierConsumptionAndSkipsProjection() {
    Batch batch = batch(BatchStatus.IN_PROGRESS);
    batch.setConsumedQuantity(new BigDecimal("5.000"));
    StockUnit selected = unit("ROLL-AFTER-CONSUMPTION");
    UUID selectedId = selected.getId();
    when(batchRepository.findByIdAndTenantIdForUpdate(BATCH_ID, TENANT_ID))
        .thenReturn(Optional.of(batch));
    when(stockUnitRepository.lockSelectedDecisionPopulation(
            eq(TENANT_ID), eq(BATCH_ID), eq(Set.of(selectedId)), anyCollection()))
        .thenReturn(List.of(selected));
    when(decisionRepository.findMaxSeq(TENANT_ID, BATCH_ID)).thenReturn(2L);
    when(stockUnitRepository.applyQualityDisposition(
            eq(TENANT_ID),
            eq(BATCH_ID),
            eq(List.of(selectedId)),
            anyCollection(),
            eq(QualityDisposition.RELEASED)))
        .thenReturn(1);

    QualityDecision decision =
        service.recordDecision(
            TrustedDecisionContext.manual(ACTOR_ID),
            new QualityDecisionCommand(
                BATCH_ID,
                QualityDecisionScope.SELECTED_UNITS,
                QualityDecisionOutcome.RELEASED,
                null,
                "Remaining roll passed inspection",
                Set.of(selectedId),
                null));

    assertThat(decision.getSeq()).isEqualTo(3);
    assertThat(batch.getStatus()).isEqualTo(BatchStatus.IN_PROGRESS);
    verify(stockUnitRepository, never()).countQualityDispositions(any(), any());
    verify(batchRepository, never()).save(batch);
  }

  @Test
  void unresolvedBatchReservationStillBlocksSelectedUnitDecision() {
    Batch batch = batch(BatchStatus.AVAILABLE);
    batch.setReservedQuantity(BigDecimal.ONE);
    UUID selectedId = UUID.randomUUID();
    when(batchRepository.findByIdAndTenantIdForUpdate(BATCH_ID, TENANT_ID))
        .thenReturn(Optional.of(batch));

    assertThatThrownBy(
            () ->
                service.recordDecision(
                    TrustedDecisionContext.manual(ACTOR_ID),
                    new QualityDecisionCommand(
                        BATCH_ID,
                        QualityDecisionScope.SELECTED_UNITS,
                        QualityDecisionOutcome.RELEASED,
                        null,
                        null,
                        Set.of(selectedId),
                        null)))
        .isInstanceOf(QualityDecisionException.class)
        .hasMessageContaining("AVAILABLE");

    verify(stockUnitRepository, never())
        .lockSelectedDecisionPopulation(any(), any(), anyCollection(), anyCollection());
  }

  @Test
  void fullLotDecisionRemainsBlockedAfterConsumption() {
    Batch batch = batch(BatchStatus.QUARANTINE);
    batch.setConsumedQuantity(BigDecimal.ONE);
    when(batchRepository.findByIdAndTenantIdForUpdate(BATCH_ID, TENANT_ID))
        .thenReturn(Optional.of(batch));

    assertThatThrownBy(() -> service.releaseFromQc(BATCH_ID))
        .isInstanceOf(QualityDecisionException.class)
        .hasMessageContaining("QUARANTINE");

    verify(stockUnitRepository, never()).lockDecisionPopulation(any(), any(), anyCollection());
  }

  @Test
  void liveReservationBlocksDecisionEvenWhenLegacyBatchStatusStillAvailable() {
    Batch batch = batch(BatchStatus.AVAILABLE);
    batch.setReservedQuantity(BigDecimal.ONE);
    when(batchRepository.findByIdAndTenantIdForUpdate(BATCH_ID, TENANT_ID))
        .thenReturn(Optional.of(batch));

    assertThatThrownBy(() -> service.releaseFromQc(BATCH_ID))
        .isInstanceOf(QualityDecisionException.class)
        .hasMessageContaining("AVAILABLE");
    verify(stockUnitRepository, never()).lockDecisionPopulation(any(), any(), anyCollection());
  }

  @Test
  void bulkRowCountMismatchRejectsPopulationDriftBeforeProjection() {
    Batch batch = batch(BatchStatus.PENDING_QC);
    StockUnit unit = unit("ROLL-DRIFT");
    when(batchRepository.findByIdAndTenantIdForUpdate(BATCH_ID, TENANT_ID))
        .thenReturn(Optional.of(batch));
    when(stockUnitRepository.countByTenantIdAndBatchIdAndIsActiveTrue(TENANT_ID, BATCH_ID))
        .thenReturn(1L);
    when(stockUnitRepository.lockDecisionPopulation(eq(TENANT_ID), eq(BATCH_ID), anyCollection()))
        .thenReturn(List.of(unit));
    when(stockUnitRepository.applyQualityDisposition(
            eq(TENANT_ID),
            eq(BATCH_ID),
            anyCollection(),
            anyCollection(),
            eq(QualityDisposition.RELEASED)))
        .thenReturn(0);

    assertThatThrownBy(
            () ->
                service.recordDecision(
                    TrustedDecisionContext.manual(ACTOR_ID),
                    new QualityDecisionCommand(
                        BATCH_ID,
                        QualityDecisionScope.FULL_LOT,
                        QualityDecisionOutcome.RELEASED,
                        null,
                        null,
                        Set.of(),
                        null)))
        .isInstanceOf(QualityDecisionException.class)
        .hasMessageContaining("population changed");
    verify(batchRepository, never()).save(batch);
  }

  @Test
  void fullLotRejectsWhenConcurrentOperationalChangeRemovesAUnitFromEligiblePopulation() {
    Batch batch = batch(BatchStatus.PENDING_QC);
    when(batchRepository.findByIdAndTenantIdForUpdate(BATCH_ID, TENANT_ID))
        .thenReturn(Optional.of(batch));
    when(stockUnitRepository.countByTenantIdAndBatchIdAndIsActiveTrue(TENANT_ID, BATCH_ID))
        .thenReturn(2L);
    when(stockUnitRepository.lockDecisionPopulation(eq(TENANT_ID), eq(BATCH_ID), anyCollection()))
        .thenReturn(List.of(unit("ROLL-STILL-ELIGIBLE")));

    assertThatThrownBy(() -> service.releaseFromQc(BATCH_ID))
        .isInstanceOf(QualityDecisionException.class)
        .hasMessageContaining("population changed");
    verify(decisionRepository, never()).save(any());
  }

  @Test
  void manualOriginCannotSubmitSystemReasonCode() {
    assertThatThrownBy(
            () ->
                service.recordDecision(
                    TrustedDecisionContext.manual(ACTOR_ID),
                    new QualityDecisionCommand(
                        BATCH_ID,
                        QualityDecisionScope.FULL_LOT,
                        QualityDecisionOutcome.RELEASED,
                        QualityReasonCode.SYSTEM_QC_PASSED,
                        null,
                        Set.of(),
                        null)))
        .isInstanceOf(QualityDecisionException.class)
        .hasMessageContaining("Reason code");
    verify(batchRepository, never()).findByIdAndTenantIdForUpdate(any(), any());
  }

  @Test
  void releaseFromQcUsesSystemActorAndSystemReason() {
    Batch batch = batch(BatchStatus.PENDING_QC);
    StockUnit unit = unit("ROLL-SYSTEM");
    when(batchRepository.findByIdAndTenantIdForUpdate(BATCH_ID, TENANT_ID))
        .thenReturn(Optional.of(batch));
    when(stockUnitRepository.countByTenantIdAndBatchIdAndIsActiveTrue(TENANT_ID, BATCH_ID))
        .thenReturn(1L);
    when(stockUnitRepository.lockDecisionPopulation(eq(TENANT_ID), eq(BATCH_ID), anyCollection()))
        .thenReturn(List.of(unit));
    when(stockUnitRepository.applyQualityDisposition(
            eq(TENANT_ID),
            eq(BATCH_ID),
            anyCollection(),
            anyCollection(),
            eq(QualityDisposition.RELEASED)))
        .thenReturn(1);
    when(stockUnitRepository.countQualityDispositions(TENANT_ID, BATCH_ID))
        .thenReturn(List.of(dispositionCount(QualityDisposition.RELEASED, 1)));

    QualityDecision decision = service.releaseFromQc(BATCH_ID);

    assertThat(decision.getActorId()).isEqualTo(TenantContext.SYSTEM_ACTOR_ID);
    assertThat(decision.getReasonCode()).isEqualTo(QualityReasonCode.SYSTEM_QC_PASSED);
  }

  @Test
  void legacyOverrideWritesAReleasedDecisionThatSupersedesLatestLedgerEntry() {
    Batch batch = batch(BatchStatus.QC_REJECTED);
    StockUnit unit = unit("ROLL-OVERRIDE");
    QualityDecision rejected =
        QualityDecision.create(
            TENANT_ID,
            BATCH_ID,
            QualityDecisionScope.FULL_LOT,
            QualityDecisionOutcome.NONCONFORMING,
            QualityReasonCode.DAMAGE,
            "Damaged",
            ACTOR_ID,
            com.fabricmanagement.production.quality.decision.domain.QualityDecisionOrigin.MANUAL,
            null,
            null,
            1,
            java.time.Instant.now());
    when(batchRepository.findByIdAndTenantIdForUpdate(BATCH_ID, TENANT_ID))
        .thenReturn(Optional.of(batch));
    when(decisionRepository.findFirstByTenantIdAndBatchIdOrderByDecidedAtDescSeqDesc(
            TENANT_ID, BATCH_ID))
        .thenReturn(Optional.of(rejected));
    when(decisionRepository.findByIdAndTenantId(rejected.getId(), TENANT_ID))
        .thenReturn(Optional.of(rejected));
    when(stockUnitRepository.countByTenantIdAndBatchIdAndIsActiveTrue(TENANT_ID, BATCH_ID))
        .thenReturn(1L);
    when(stockUnitRepository.lockDecisionPopulation(eq(TENANT_ID), eq(BATCH_ID), anyCollection()))
        .thenReturn(List.of(unit));
    when(decisionRepository.findMaxSeq(TENANT_ID, BATCH_ID)).thenReturn(1L);
    when(stockUnitRepository.applyQualityDisposition(
            eq(TENANT_ID),
            eq(BATCH_ID),
            anyCollection(),
            anyCollection(),
            eq(QualityDisposition.RELEASED)))
        .thenReturn(1);
    when(stockUnitRepository.countQualityDispositions(TENANT_ID, BATCH_ID))
        .thenReturn(List.of(dispositionCount(QualityDisposition.RELEASED, 1)));

    QualityDecision override =
        service.overrideToReleased(BATCH_ID, "Manager reviewed and accepted the lot");

    assertThat(override.getSupersedesDecisionId()).isEqualTo(rejected.getId());
    assertThat(override.getRemarks()).isEqualTo("Manager reviewed and accepted the lot");
    assertThat(override.getActorId()).isEqualTo(ACTOR_ID);
    assertThat(batch.getStatus()).isEqualTo(BatchStatus.AVAILABLE);
  }

  @Test
  void otherReasonRequiresRemarks() {
    assertThatThrownBy(
            () ->
                service.recordDecision(
                    TrustedDecisionContext.manual(ACTOR_ID),
                    new QualityDecisionCommand(
                        BATCH_ID,
                        QualityDecisionScope.FULL_LOT,
                        QualityDecisionOutcome.NONCONFORMING,
                        QualityReasonCode.OTHER,
                        " ",
                        Set.of(),
                        null)))
        .isInstanceOf(QualityDecisionException.class)
        .hasMessageContaining("Remarks");
    verify(batchRepository, never()).findByIdAndTenantIdForUpdate(any(), any());
  }

  @Test
  void duplicateQcEventReturnsExistingDecisionWithoutApplyingAgain() {
    UUID eventId = UUID.randomUUID();
    QualityDecision existing =
        QualityDecision.create(
            TENANT_ID,
            BATCH_ID,
            QualityDecisionScope.FULL_LOT,
            QualityDecisionOutcome.RELEASED,
            QualityReasonCode.SYSTEM_QC_PASSED,
            null,
            ACTOR_ID,
            com.fabricmanagement.production.quality.decision.domain.QualityDecisionOrigin
                .SYSTEM_QC_EVENT,
            eventId,
            null,
            1,
            java.time.Instant.now());
    when(decisionRepository.findByTenantIdAndSourceEventId(TENANT_ID, eventId))
        .thenReturn(Optional.of(existing));

    QualityDecision result =
        service.recordDecision(
            TrustedDecisionContext.qcEvent(ACTOR_ID, eventId),
            new QualityDecisionCommand(
                BATCH_ID,
                QualityDecisionScope.FULL_LOT,
                QualityDecisionOutcome.RELEASED,
                QualityReasonCode.SYSTEM_QC_PASSED,
                null,
                Set.of(),
                null));

    assertThat(result).isSameAs(existing);
    verify(batchRepository, never()).findByIdAndTenantIdForUpdate(any(), any());
  }

  private Batch batch(BatchStatus status) {
    Batch batch = new Batch();
    batch.setId(BATCH_ID);
    batch.setTenantId(TENANT_ID);
    batch.setBatchCode("LOT-QC-1");
    batch.setStatus(status);
    batch.setReservedQuantity(BigDecimal.ZERO);
    batch.setConsumedQuantity(BigDecimal.ZERO);
    return batch;
  }

  private StockUnit unit(String barcode) {
    StockUnit unit =
        StockUnit.create(
            TENANT_ID,
            BATCH_ID,
            ProductType.FABRIC,
            barcode,
            null,
            PackageType.ROLL,
            new BigDecimal("25.000"),
            null,
            "KG",
            UUID.randomUUID(),
            StockUnitSourceType.GOODS_RECEIPT,
            UUID.randomUUID(),
            QualityDisposition.PENDING_INSPECTION);
    unit.setId(UUID.randomUUID());
    return unit;
  }

  private StockUnitRepository.QualityDispositionCount dispositionCount(
      QualityDisposition disposition, long count) {
    return new StockUnitRepository.QualityDispositionCount() {
      @Override
      public QualityDisposition getDisposition() {
        return disposition;
      }

      @Override
      public long getUnitCount() {
        return count;
      }
    };
  }
}
