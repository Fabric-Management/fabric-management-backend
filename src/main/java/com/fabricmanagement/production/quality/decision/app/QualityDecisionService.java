package com.fabricmanagement.production.quality.decision.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.quality.decision.domain.QualityDecision;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionOrigin;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionOutcome;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionScope;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionUnit;
import com.fabricmanagement.production.quality.decision.domain.QualityReasonCode;
import com.fabricmanagement.production.quality.decision.domain.exception.QualityDecisionException;
import com.fabricmanagement.production.quality.decision.infra.repository.QualityDecisionRepository;
import com.fabricmanagement.production.quality.decision.infra.repository.QualityDecisionUnitRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class QualityDecisionService {

  private static final Set<StockUnitStatus> DECISION_ELIGIBLE_STATUSES =
      EnumSet.of(
          StockUnitStatus.AVAILABLE,
          StockUnitStatus.PARTIAL,
          StockUnitStatus.QUARANTINE,
          StockUnitStatus.ON_HOLD);

  private static final Set<BatchStatus> ACTIVE_OR_TERMINAL_BATCH_STATUSES =
      EnumSet.of(
          BatchStatus.RESERVED,
          BatchStatus.IN_PROGRESS,
          BatchStatus.ON_HOLD,
          BatchStatus.DEPLETED,
          BatchStatus.RETURNED,
          BatchStatus.DESTROYED);

  private final BatchRepository batchRepository;
  private final StockUnitRepository stockUnitRepository;
  private final QualityDecisionRepository decisionRepository;
  private final QualityDecisionUnitRepository decisionUnitRepository;

  @Transactional
  public QualityDecision recordDecision(
      TrustedDecisionContext context, QualityDecisionCommand command) {
    UUID tenantId = TenantContext.requireTenantId();
    validateContext(context);
    validateCommand(context, command);

    if (context.sourceEventId() != null) {
      var existing =
          decisionRepository.findByTenantIdAndSourceEventId(tenantId, context.sourceEventId());
      if (existing.isPresent()) {
        return assertIdempotentEventMatchesCommand(existing.get(), command);
      }
    }

    Batch batch =
        batchRepository
            .findByIdAndTenantIdForUpdate(command.batchId(), tenantId)
            .orElseThrow(() -> new NotFoundException("Batch not found: " + command.batchId()));
    if (context.sourceEventId() != null) {
      var concurrentWinner =
          decisionRepository.findByTenantIdAndSourceEventId(tenantId, context.sourceEventId());
      if (concurrentWinner.isPresent()) {
        return assertIdempotentEventMatchesCommand(concurrentWinner.get(), command);
      }
    }
    assertBatchAllowsDecision(batch);

    List<StockUnit> population = lockPopulation(tenantId, command, batch);
    validateSupersedes(tenantId, command, batch);

    long seq = decisionRepository.findMaxSeq(tenantId, batch.getId()) + 1;
    Instant now = Instant.now();
    QualityDecision decision =
        QualityDecision.create(
            tenantId,
            batch.getId(),
            command.scope(),
            command.outcome(),
            command.reasonCode(),
            normalizedRemarks(command.remarks()),
            context.actorId(),
            context.origin(),
            context.sourceEventId(),
            command.supersedesDecisionId(),
            seq,
            now);
    decisionRepository.save(decision);
    decisionUnitRepository.saveAll(
        population.stream()
            .map(unit -> QualityDecisionUnit.of(tenantId, decision.getId(), unit.getId()))
            .toList());

    List<UUID> populationIds = population.stream().map(StockUnit::getId).toList();
    int affected =
        stockUnitRepository.applyQualityDisposition(
            tenantId,
            batch.getId(),
            populationIds,
            DECISION_ELIGIBLE_STATUSES,
            command.outcome().toDisposition());
    if (affected != populationIds.size()) {
      throw QualityDecisionException.populationDrift();
    }

    applyBatchProjection(tenantId, batch);
    batchRepository.save(batch);
    log.info(
        "Recorded quality decision: tenantId={}, batchId={}, decisionId={}, outcome={}, units={}",
        tenantId,
        batch.getId(),
        decision.getId(),
        decision.getOutcome(),
        population.size());
    return decision;
  }

  @Transactional
  public QualityDecision releaseFromQc(UUID batchId) {
    return recordDecision(
        TrustedDecisionContext.systemRelease(TenantContext.SYSTEM_ACTOR_ID),
        new QualityDecisionCommand(
            batchId,
            QualityDecisionScope.FULL_LOT,
            QualityDecisionOutcome.RELEASED,
            QualityReasonCode.SYSTEM_QC_PASSED,
            null,
            Set.of(),
            null));
  }

  /** Compatibility path for the legacy batch override action; the ledger remains authoritative. */
  @Transactional
  public QualityDecision overrideToReleased(UUID batchId, String reason) {
    UUID tenantId = TenantContext.requireTenantId();
    batchRepository
        .findByIdAndTenantIdForUpdate(batchId, tenantId)
        .orElseThrow(() -> new NotFoundException("Batch not found: " + batchId));
    UUID supersedesDecisionId =
        decisionRepository
            .findFirstByTenantIdAndBatchIdOrderByDecidedAtDescSeqDesc(tenantId, batchId)
            .map(QualityDecision::getId)
            .orElse(null);
    return recordDecision(
        TrustedDecisionContext.manual(TenantContext.getCurrentUserId()),
        new QualityDecisionCommand(
            batchId,
            QualityDecisionScope.FULL_LOT,
            QualityDecisionOutcome.RELEASED,
            null,
            reason,
            Set.of(),
            supersedesDecisionId));
  }

  private List<StockUnit> lockPopulation(
      UUID tenantId, QualityDecisionCommand command, Batch batch) {
    if (command.scope() == QualityDecisionScope.FULL_LOT) {
      long activeCount =
          stockUnitRepository.countByTenantIdAndBatchIdAndIsActiveTrue(tenantId, batch.getId());
      List<StockUnit> population =
          stockUnitRepository.lockDecisionPopulation(
              tenantId, batch.getId(), DECISION_ELIGIBLE_STATUSES);
      if (population.isEmpty()) {
        throw QualityDecisionException.noUnits();
      }
      if (population.size() != activeCount) {
        throw QualityDecisionException.populationDrift();
      }
      return population;
    }

    Set<UUID> requestedIds = command.stockUnitIds();
    List<StockUnit> population =
        stockUnitRepository.lockSelectedDecisionPopulation(
            tenantId, batch.getId(), requestedIds, DECISION_ELIGIBLE_STATUSES);
    if (population.size() != requestedIds.size()) {
      throw QualityDecisionException.unitMismatch();
    }
    return population;
  }

  private void validateSupersedes(UUID tenantId, QualityDecisionCommand command, Batch batch) {
    if (command.supersedesDecisionId() == null) {
      return;
    }
    QualityDecision superseded =
        decisionRepository
            .findByIdAndTenantId(command.supersedesDecisionId(), tenantId)
            .orElseThrow(QualityDecisionException::supersedesInvalid);
    if (!superseded.getBatchId().equals(batch.getId())) {
      throw QualityDecisionException.supersedesInvalid();
    }
  }

  private QualityDecision assertIdempotentEventMatchesCommand(
      QualityDecision existing, QualityDecisionCommand command) {
    if (!existing.getBatchId().equals(command.batchId())
        || existing.getDecisionScope() != command.scope()
        || existing.getOutcome() != command.outcome()
        || existing.getReasonCode() != command.reasonCode()) {
      throw QualityDecisionException.sourceEventConflict();
    }
    return existing;
  }

  private void assertBatchAllowsDecision(Batch batch) {
    if (ACTIVE_OR_TERMINAL_BATCH_STATUSES.contains(batch.getStatus())
        || batch.getReservedQuantity().compareTo(BigDecimal.ZERO) > 0
        || batch.getConsumedQuantity().compareTo(BigDecimal.ZERO) > 0) {
      throw QualityDecisionException.batchActive(batch.getStatus().name());
    }
  }

  private void applyBatchProjection(UUID tenantId, Batch batch) {
    var counts = stockUnitRepository.countQualityDispositions(tenantId, batch.getId());
    long total =
        counts.stream().mapToLong(StockUnitRepository.QualityDispositionCount::getUnitCount).sum();
    BatchStatus target = BatchStatus.QUARANTINE;
    if (counts.size() == 1 && total > 0) {
      QualityDisposition disposition = counts.get(0).getDisposition();
      target =
          switch (disposition) {
            case RELEASED -> BatchStatus.AVAILABLE;
            case PENDING_INSPECTION -> BatchStatus.PENDING_QC;
            case NONCONFORMING -> BatchStatus.QC_REJECTED;
            case QUARANTINED -> BatchStatus.QUARANTINE;
          };
    }
    batch.applyQualityProjection(target);
  }

  private void validateContext(TrustedDecisionContext context) {
    if (context == null || context.actorId() == null || context.origin() == null) {
      throw QualityDecisionException.actorMissing();
    }
    if (context.origin() == QualityDecisionOrigin.SYSTEM_QC_EVENT
        && context.sourceEventId() == null) {
      throw QualityDecisionException.commandInvalid("SYSTEM_QC_EVENT requires sourceEventId");
    }
    if (context.origin() != QualityDecisionOrigin.SYSTEM_QC_EVENT
        && context.sourceEventId() != null) {
      throw QualityDecisionException.commandInvalid("sourceEventId is reserved for QC events");
    }
    if (context.origin() == QualityDecisionOrigin.MIGRATION_BACKFILL) {
      throw QualityDecisionException.commandInvalid("MIGRATION_BACKFILL is migration-only");
    }
  }

  private void validateCommand(TrustedDecisionContext context, QualityDecisionCommand command) {
    if (command == null
        || command.batchId() == null
        || command.scope() == null
        || command.outcome() == null) {
      throw QualityDecisionException.commandInvalid("batchId, scope and outcome are required");
    }
    Set<UUID> ids = command.stockUnitIds() == null ? Set.of() : command.stockUnitIds();
    if (command.scope() == QualityDecisionScope.FULL_LOT && !ids.isEmpty()) {
      throw QualityDecisionException.commandInvalid("FULL_LOT must not include stockUnitIds");
    }
    if (command.scope() == QualityDecisionScope.SELECTED_UNITS && ids.isEmpty()) {
      throw QualityDecisionException.commandInvalid("SELECTED_UNITS requires stockUnitIds");
    }
    if (command.reasonCode() == QualityReasonCode.OTHER
        && normalizedRemarks(command.remarks()) == null) {
      throw QualityDecisionException.remarksRequired();
    }
    if (!reasonAllowed(context.origin(), command.outcome(), command.reasonCode())) {
      throw QualityDecisionException.reasonInvalid();
    }
  }

  private boolean reasonAllowed(
      QualityDecisionOrigin origin, QualityDecisionOutcome outcome, QualityReasonCode reasonCode) {
    if (origin == QualityDecisionOrigin.SYSTEM_RELEASE) {
      return outcome == QualityDecisionOutcome.RELEASED
          && reasonCode == QualityReasonCode.SYSTEM_QC_PASSED;
    }
    if (origin == QualityDecisionOrigin.SYSTEM_QC_EVENT) {
      return (outcome == QualityDecisionOutcome.RELEASED
              && reasonCode == QualityReasonCode.SYSTEM_QC_PASSED)
          || (outcome == QualityDecisionOutcome.NONCONFORMING
              && reasonCode == QualityReasonCode.SYSTEM_QC_REJECTED);
    }
    if (origin != QualityDecisionOrigin.MANUAL) {
      return false;
    }
    return switch (outcome) {
      case RELEASED -> reasonCode == null;
      case QUARANTINED ->
          reasonCode != null
              && Set.of(
                      QualityReasonCode.SUSPECTED_DAMAGE,
                      QualityReasonCode.AWAITING_LAB,
                      QualityReasonCode.SUPPLIER_DISPUTE,
                      QualityReasonCode.SHADE_CHECK,
                      QualityReasonCode.OTHER)
                  .contains(reasonCode);
      case NONCONFORMING ->
          reasonCode != null
              && Set.of(
                      QualityReasonCode.DAMAGE,
                      QualityReasonCode.STAIN,
                      QualityReasonCode.SHADE_VARIATION,
                      QualityReasonCode.SHORT_LENGTH,
                      QualityReasonCode.MEASURE_MISMATCH,
                      QualityReasonCode.OTHER)
                  .contains(reasonCode);
    };
  }

  private String normalizedRemarks(String remarks) {
    return remarks == null || remarks.isBlank() ? null : remarks.trim();
  }
}
