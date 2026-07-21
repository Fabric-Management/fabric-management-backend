package com.fabricmanagement.production.execution.stockunit.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.domain.WasteCategory;
import com.fabricmanagement.production.execution.batch.domain.event.BatchAdjustedEvent;
import com.fabricmanagement.production.execution.batch.domain.event.BatchConsumedEvent;
import com.fabricmanagement.production.execution.batch.domain.event.BatchWasteRecordedEvent;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitAuditLog;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.execution.stockunit.domain.event.StockUnitConsumedEvent;
import com.fabricmanagement.production.execution.stockunit.domain.event.StockUnitCreatedEvent;
import com.fabricmanagement.production.execution.stockunit.domain.event.StockUnitDepletedEvent;
import com.fabricmanagement.production.execution.stockunit.domain.event.StockUnitDisposedEvent;
import com.fabricmanagement.production.execution.stockunit.domain.event.StockUnitGradeChangedEvent;
import com.fabricmanagement.production.execution.stockunit.domain.event.StockUnitTransferredEvent;
import com.fabricmanagement.production.execution.stockunit.domain.exception.StockUnitDomainException;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitAuditLogRepository;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.qualitygrade.app.QualityGradeService;
import com.fabricmanagement.production.masterdata.qualitygrade.domain.QualityGrade;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for all {@link StockUnit} lifecycle operations.
 *
 * <h2>Actor Resolution</h2>
 *
 * <p>All operations resolve the acting user via {@code TenantContext.getCurrentUserId()}
 * internally. The controller layer does NOT pass actorId — consistent with {@code
 * BatchOperationsService} pattern.
 *
 * <h2>Invariant Enforcement</h2>
 *
 * <p>All weight invariants and state transitions are enforced by the {@link StockUnit} entity
 * itself. This service is responsible for:
 *
 * <ul>
 *   <li>Tenant isolation — every operation starts with {@code TenantContext.requireTenantId()}
 *   <li>Batch status gate — AVAILABLE and PARTIAL checks before consumption/transfer
 *   <li>Grade upgrade approval gate — checks {@code requiresApprovalForTransition} before {@code
 *       changeGrade()}
 *   <li>Audit log writing — every state change produces a {@link StockUnitAuditLog} entry
 *   <li>Domain event publishing — via {@link ApplicationEventPublisher}
 * </ul>
 *
 * <h2>F5: RESERVED → Consume Flow</h2>
 *
 * <p>If a StockUnit is RESERVED, it must be consumed via {@link #consumeReserved(UUID, BigDecimal,
 * UUID)} which releases the reservation and immediately consumes in one atomic operation. This
 * avoids the 2-call dance of {@code releaseReservation()} → {@code consume()} and
 *
 * <p>Historical note: Pre-Sprint 2 Batch counter discrepancies (if any) are detected nightly by
 * StockUnitReconciliationService. All new operations maintain atomic Batch ↔ StockUnit
 * synchronization within the same transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockUnitService {

  private final StockUnitRepository stockUnitRepository;
  private final BatchRepository batchRepository;
  private final QualityGradeService qualityGradeService;
  private final StockUnitAuditLogRepository auditLogRepository;
  private final ApplicationEventPublisher eventPublisher;

  // ── Creation ─────────────────────────────────────────────────────────────

  /**
   * Creates a new StockUnit for an existing Batch.
   *
   * <p>Validates that the parent Batch exists and belongs to the current tenant. Package type
   * compatibility is validated inside {@link StockUnit#create}.
   */
  @Transactional
  public StockUnit create(
      UUID batchId,
      ProductType productType,
      String barcode,
      String serialNumber,
      PackageType packageType,
      BigDecimal initialWeight,
      BigDecimal grossWeight,
      String unit,
      BigDecimal length,
      String lengthUnit,
      UUID locationId,
      StockUnitSourceType sourceType,
      UUID sourceId) {

    UUID tenantId = TenantContext.requireTenantId();
    UUID actorId = TenantContext.getCurrentUserId();
    Batch batch = batchId == null ? null : loadBatch(batchId, tenantId);

    StockUnit created =
        internalCreate(
            tenantId,
            batchId,
            productType,
            barcode,
            serialNumber,
            packageType,
            initialWeight,
            grossWeight,
            unit,
            length,
            lengthUnit,
            locationId,
            sourceType,
            sourceId,
            QualityDisposition.PENDING_INSPECTION,
            actorId);
    projectQualityAfterBirth(batch, tenantId);
    return created;
  }

  private StockUnit internalCreate(
      UUID tenantId,
      UUID batchId,
      ProductType productType,
      String barcode,
      String serialNumber,
      PackageType packageType,
      BigDecimal initialWeight,
      BigDecimal grossWeight,
      String unit,
      BigDecimal length,
      String lengthUnit,
      UUID locationId,
      StockUnitSourceType sourceType,
      UUID sourceId,
      QualityDisposition initialQualityDisposition,
      UUID actorId) {

    StockUnit stockUnit =
        StockUnit.create(
            tenantId,
            batchId,
            productType,
            barcode,
            serialNumber,
            packageType,
            initialWeight,
            grossWeight,
            unit,
            locationId,
            sourceType,
            sourceId,
            initialQualityDisposition);
    if (length != null || lengthUnit != null) {
      stockUnit.recordLength(length, lengthUnit);
    }

    stockUnit = stockUnitRepository.save(stockUnit);

    auditLogRepository.save(
        StockUnitAuditLog.of(
            tenantId,
            stockUnit.getId(),
            StockUnitAuditLog.OP_CREATE,
            null,
            null,
            "CREATED",
            actorId,
            1,
            null));

    eventPublisher.publishEvent(
        new StockUnitCreatedEvent(
            tenantId,
            stockUnit.getId(),
            stockUnit.getBarcode(),
            batchId,
            productType,
            packageType,
            initialWeight,
            unit,
            locationId));

    log.info(
        "StockUnit created: id={}, barcode={}, batchId={}, weight={} {}",
        stockUnit.getId(),
        stockUnit.getBarcode(),
        batchId,
        initialWeight,
        unit);

    return stockUnit;
  }

  // ── Consumption ───────────────────────────────────────────────────────────

  /**
   * Consumes weight from an AVAILABLE or PARTIAL StockUnit.
   *
   * <p>Checks the parent Batch status as a gate — consumption is blocked if the Batch is ON_HOLD,
   * QUARANTINE, QC_REJECTED, RETURNED, or DESTROYED.
   *
   * @param stockUnitId the unit to consume from
   * @param amount weight to consume (must be positive and ≤ currentWeight)
   */
  @Transactional
  public StockUnit consume(UUID stockUnitId, BigDecimal amount) {
    UUID tenantId = TenantContext.requireTenantId();
    UUID actorId = TenantContext.getCurrentUserId();
    StockUnit unit = loadUnit(stockUnitId, tenantId);
    Batch batch = loadBatchForUpdate(unit.getBatchId(), tenantId);

    assertBatchAllowsConsumption(batch);

    BigDecimal prevWeight = unit.getCurrentWeight();
    StockUnitStatus prevStatus = unit.getStatus();

    unit.consume(amount);
    unit = stockUnitRepository.save(unit);

    batch.consumeFromAvailable(amount);
    batchRepository.save(batch);

    writeAuditLog(
        tenantId,
        stockUnitId,
        StockUnitAuditLog.OP_CONSUME,
        "currentWeight",
        prevWeight.toPlainString(),
        unit.getCurrentWeight().toPlainString(),
        actorId,
        1,
        null);

    eventPublisher.publishEvent(
        new StockUnitConsumedEvent(
            tenantId,
            unit.getId(),
            unit.getBarcode(),
            unit.getBatchId(),
            amount,
            unit.getCurrentWeight(),
            unit.getUnit()));

    eventPublisher.publishEvent(
        new BatchConsumedEvent(
            tenantId,
            batch.getId(),
            amount,
            batch.getUnit(),
            batch.getLocationId(),
            unit.getId(),
            "STOCK_UNIT"));

    if (unit.getStatus() == StockUnitStatus.DEPLETED && prevStatus != StockUnitStatus.DEPLETED) {
      eventPublisher.publishEvent(
          new StockUnitDepletedEvent(
              tenantId,
              unit.getId(),
              unit.getBarcode(),
              unit.getBatchId(),
              unit.getLocationId(),
              unit.getInitialWeight(),
              unit.getUnit()));
      log.info("StockUnit depleted: id={}, barcode={}", unit.getId(), unit.getBarcode());
    }

    log.debug("Consumed {} {} from StockUnit {}", amount, unit.getUnit(), unit.getBarcode());
    return unit;
  }

  /**
   * Consumes from a RESERVED StockUnit atomically — releases the reservation and consumes in one
   * transaction.
   *
   * <p>This is the preferred path for work-order-driven consumption where the unit was previously
   * reserved. Avoids the race window of releaseReservation() → consume().
   *
   * @param stockUnitId the reserved unit
   * @param amount weight to consume
   * @param workOrderId the consuming work order (for audit trail)
   */
  @Transactional
  public StockUnit consumeReserved(UUID stockUnitId, BigDecimal amount, UUID workOrderId) {
    UUID tenantId = TenantContext.requireTenantId();
    UUID actorId = TenantContext.getCurrentUserId();
    StockUnit unit = loadUnit(stockUnitId, tenantId);
    Batch batch = loadBatchForUpdate(unit.getBatchId(), tenantId);

    assertBatchAllowsConsumption(batch);

    if (unit.getStatus() != StockUnitStatus.RESERVED) {
      throw new StockUnitDomainException(
          String.format(
              "StockUnit %s is not RESERVED (status=%s). Use consume() for AVAILABLE/PARTIAL units.",
              unit.getBarcode(), unit.getStatus()));
    }

    BigDecimal prevWeight = unit.getCurrentWeight();

    // Release → consume in a single method call to bypass status check
    unit.releaseReservation();
    unit.consume(amount);
    unit = stockUnitRepository.save(unit);

    batch.consumeFromReservation(amount);
    batchRepository.save(batch);

    writeAuditLog(
        tenantId,
        stockUnitId,
        StockUnitAuditLog.OP_CONSUME,
        "currentWeight",
        prevWeight.toPlainString(),
        unit.getCurrentWeight().toPlainString(),
        actorId,
        1,
        "Reserved consumption — workOrder: " + workOrderId);

    eventPublisher.publishEvent(
        new StockUnitConsumedEvent(
            tenantId,
            unit.getId(),
            unit.getBarcode(),
            unit.getBatchId(),
            amount,
            unit.getCurrentWeight(),
            unit.getUnit()));

    eventPublisher.publishEvent(
        new BatchConsumedEvent(
            tenantId,
            batch.getId(),
            amount,
            batch.getUnit(),
            batch.getLocationId(),
            unit.getId(),
            "STOCK_UNIT"));

    if (unit.getStatus() == StockUnitStatus.DEPLETED) {
      eventPublisher.publishEvent(
          new StockUnitDepletedEvent(
              tenantId,
              unit.getId(),
              unit.getBarcode(),
              unit.getBatchId(),
              unit.getLocationId(),
              unit.getInitialWeight(),
              unit.getUnit()));
    }

    return unit;
  }

  /**
   * Reverses a previous consumption — adds weight back.
   *
   * @param stockUnitId the unit to reverse consumption on
   * @param amount the weight to add back (must not exceed consumed amount)
   * @param reason mandatory justification for the reversal
   */
  @Transactional
  public StockUnit reverseConsumption(UUID stockUnitId, BigDecimal amount, String reason) {
    UUID tenantId = TenantContext.requireTenantId();
    UUID actorId = TenantContext.getCurrentUserId();
    StockUnit unit = loadUnit(stockUnitId, tenantId);
    Batch batch = loadBatchForUpdate(unit.getBatchId(), tenantId);

    BigDecimal prevWeight = unit.getCurrentWeight();

    unit.reverseConsumption(amount, reason);
    unit = stockUnitRepository.save(unit);

    // Sync: Reduce consumed quantity at Batch level
    batch.reverseConsumption(amount);
    batchRepository.save(batch);

    // Publish BatchAdjustedEvent to bridge this correction to inventory
    eventPublisher.publishEvent(
        new BatchAdjustedEvent(
            tenantId,
            batch.getId(),
            amount,
            batch.getUnit(),
            batch.getLocationId(),
            reason,
            "StockUnit consumption reversal: " + unit.getBarcode()));

    writeAuditLog(
        tenantId,
        stockUnitId,
        StockUnitAuditLog.OP_REVERSAL,
        "currentWeight",
        prevWeight.toPlainString(),
        unit.getCurrentWeight().toPlainString(),
        actorId,
        2,
        reason);

    log.info(
        "Reversed {} {} on StockUnit {}: reason={}",
        amount,
        unit.getUnit(),
        unit.getBarcode(),
        reason);
    return unit;
  }

  // ── Transfer ──────────────────────────────────────────────────────────────

  /** Initiates a transfer — transitions StockUnit to IN_TRANSIT. */
  @Transactional
  public StockUnit startTransfer(UUID stockUnitId, UUID targetLocationId) {
    UUID tenantId = TenantContext.requireTenantId();
    UUID actorId = TenantContext.getCurrentUserId();
    StockUnit unit = loadUnit(stockUnitId, tenantId);

    UUID fromLocation = unit.getLocationId();
    unit.startTransfer(targetLocationId);
    unit = stockUnitRepository.save(unit);

    writeAuditLog(
        tenantId,
        stockUnitId,
        StockUnitAuditLog.OP_TRANSFER,
        "locationId",
        fromLocation != null ? fromLocation.toString() : null,
        targetLocationId.toString(),
        actorId,
        1,
        null);

    log.debug(
        "Transfer started: StockUnit={}, from={}, to={}",
        unit.getBarcode(),
        fromLocation,
        targetLocationId);
    return unit;
  }

  /** Completes a transfer — StockUnit arrives at its destination. */
  @Transactional
  public StockUnit completeTransfer(UUID stockUnitId, UUID finalLocationId) {
    UUID tenantId = TenantContext.requireTenantId();
    UUID actorId = TenantContext.getCurrentUserId();
    StockUnit unit = loadUnit(stockUnitId, tenantId);

    UUID fromLocation = unit.getPreviousLocationId();
    unit.arriveAt(finalLocationId);
    unit = stockUnitRepository.save(unit);

    writeAuditLog(
        tenantId,
        stockUnitId,
        StockUnitAuditLog.OP_TRANSFER,
        "locationId",
        fromLocation != null ? fromLocation.toString() : null,
        finalLocationId.toString(),
        actorId,
        1,
        "Transfer completed");

    eventPublisher.publishEvent(
        new StockUnitTransferredEvent(
            tenantId,
            unit.getId(),
            unit.getBarcode(),
            unit.getBatchId(),
            unit.getCurrentWeight(),
            unit.getUnit(),
            fromLocation,
            finalLocationId));

    log.info("Transfer completed: StockUnit={}, arrivedAt={}", unit.getBarcode(), finalLocationId);
    return unit;
  }

  // ── Grade Change ──────────────────────────────────────────────────────────

  /**
   * Changes the quality grade of a StockUnit.
   *
   * <p>Checks the approval requirement by comparing old and new grade ranks via {@link
   * QualityGrade#requiresApprovalForTransition(QualityGrade)}. If approval is required, the caller
   * must pass a valid {@code approvalId}; otherwise this method throws.
   *
   * @param stockUnitId the unit to re-grade
   * @param newGradeId the target quality grade
   * @param reason mandatory justification for both upgrades and downgrades
   * @param approvalId required if the transition is a promotion; null allowed for demotions
   */
  @Transactional
  public StockUnit changeGrade(UUID stockUnitId, UUID newGradeId, String reason, UUID approvalId) {
    UUID tenantId = TenantContext.requireTenantId();
    UUID actorId = TenantContext.getCurrentUserId();
    StockUnit unit = loadUnit(stockUnitId, tenantId);
    QualityGrade newGrade = qualityGradeService.findById(newGradeId);

    boolean isPromotion = false;

    if (unit.getQualityGradeId() != null) {
      QualityGrade currentGrade = qualityGradeService.findById(unit.getQualityGradeId());
      isPromotion = currentGrade.isPromotionTo(newGrade);

      if (currentGrade.requiresApprovalForTransition(newGrade) && approvalId == null) {
        throw new StockUnitDomainException(
            String.format(
                "Grade change from %s to %s requires approval. Provide approvalId.",
                currentGrade.getCode(), newGrade.getCode()));
      }
    }

    UUID prevGradeId = unit.getQualityGradeId();
    unit.changeGrade(newGradeId);
    unit = stockUnitRepository.save(unit);

    writeAuditLog(
        tenantId,
        stockUnitId,
        StockUnitAuditLog.OP_GRADE_CHANGE,
        "qualityGradeId",
        prevGradeId != null ? prevGradeId.toString() : null,
        newGradeId.toString(),
        actorId,
        isPromotion ? 3 : 2,
        reason);

    eventPublisher.publishEvent(
        new StockUnitGradeChangedEvent(
            tenantId,
            unit.getId(),
            unit.getBarcode(),
            unit.getBatchId(),
            prevGradeId,
            newGradeId,
            isPromotion));

    log.info(
        "Grade changed: StockUnit={}, prevGrade={}, newGrade={}, promotion={}",
        unit.getBarcode(),
        prevGradeId,
        newGradeId,
        isPromotion);
    return unit;
  }

  // ── Hold / Quarantine ─────────────────────────────────────────────────────

  @Transactional
  public StockUnit hold(UUID stockUnitId, String reason) {
    UUID tenantId = TenantContext.requireTenantId();
    UUID actorId = TenantContext.getCurrentUserId();
    StockUnit unit = loadUnit(stockUnitId, tenantId);
    StockUnitStatus prev = unit.getStatus();
    unit.hold();
    unit = stockUnitRepository.save(unit);
    writeAuditLog(
        tenantId,
        stockUnitId,
        StockUnitAuditLog.OP_HOLD,
        "status",
        prev.name(),
        StockUnitStatus.ON_HOLD.name(),
        actorId,
        1,
        reason);
    return unit;
  }

  @Transactional
  public StockUnit releaseHold(UUID stockUnitId, String reason) {
    UUID tenantId = TenantContext.requireTenantId();
    UUID actorId = TenantContext.getCurrentUserId();
    StockUnit unit = loadUnit(stockUnitId, tenantId);
    unit.releaseHold();
    unit = stockUnitRepository.save(unit);
    writeAuditLog(
        tenantId,
        stockUnitId,
        StockUnitAuditLog.OP_HOLD_RELEASE,
        "status",
        StockUnitStatus.ON_HOLD.name(),
        unit.getStatus().name(),
        actorId,
        1,
        reason);
    return unit;
  }

  @Transactional
  public StockUnit quarantine(UUID stockUnitId, String reason) {
    UUID tenantId = TenantContext.requireTenantId();
    UUID actorId = TenantContext.getCurrentUserId();
    StockUnit unit = loadUnit(stockUnitId, tenantId);
    StockUnitStatus prev = unit.getStatus();
    unit.quarantine();
    unit = stockUnitRepository.save(unit);
    writeAuditLog(
        tenantId,
        stockUnitId,
        StockUnitAuditLog.OP_QUARANTINE,
        "status",
        prev.name(),
        StockUnitStatus.QUARANTINE.name(),
        actorId,
        2,
        reason);
    return unit;
  }

  @Transactional
  public StockUnit releaseQuarantine(UUID stockUnitId, String reason) {
    UUID tenantId = TenantContext.requireTenantId();
    UUID actorId = TenantContext.getCurrentUserId();
    StockUnit unit = loadUnit(stockUnitId, tenantId);
    unit.releaseQuarantine();
    unit = stockUnitRepository.save(unit);
    writeAuditLog(
        tenantId,
        stockUnitId,
        StockUnitAuditLog.OP_QUARANTINE_RELEASE,
        "status",
        StockUnitStatus.QUARANTINE.name(),
        unit.getStatus().name(),
        actorId,
        3,
        reason);
    return unit;
  }

  // ── Disposal ──────────────────────────────────────────────────────────────

  /**
   * Disposes a StockUnit — terminal operation.
   *
   * @param stockUnitId the unit to dispose
   * @param reason mandatory reason for disposal
   */
  @Transactional
  public StockUnit dispose(UUID stockUnitId, String reason) {
    UUID tenantId = TenantContext.requireTenantId();
    UUID actorId = TenantContext.getCurrentUserId();
    StockUnit unit = loadUnit(stockUnitId, tenantId);
    Batch batch = loadBatchForUpdate(unit.getBatchId(), tenantId);

    if (reason == null || reason.isBlank()) {
      throw new StockUnitDomainException("Disposal reason must not be blank");
    }

    BigDecimal disposedWeight = unit.getCurrentWeight();
    unit.dispose();
    unit = stockUnitRepository.save(unit);

    if (disposedWeight.compareTo(BigDecimal.ZERO) > 0) {
      batch.consumeFromAvailable(disposedWeight);
      batch.recordWaste(disposedWeight);
      batchRepository.save(batch);
    }

    writeAuditLog(
        tenantId,
        stockUnitId,
        StockUnitAuditLog.OP_DISPOSE,
        "status",
        "ACTIVE",
        StockUnitStatus.DISPOSED.name(),
        actorId,
        4,
        reason);

    eventPublisher.publishEvent(
        new StockUnitDisposedEvent(
            tenantId,
            unit.getId(),
            unit.getBarcode(),
            unit.getBatchId(),
            unit.getLocationId(),
            disposedWeight,
            unit.getUnit(),
            reason));

    if (disposedWeight.compareTo(BigDecimal.ZERO) > 0) {
      eventPublisher.publishEvent(
          new BatchWasteRecordedEvent(
              tenantId,
              batch.getId(),
              disposedWeight,
              batch.getUnit(),
              batch.getLocationId(),
              WasteCategory.OTHER,
              reason));
    }

    log.warn(
        "StockUnit disposed: id={}, barcode={}, reason={}",
        unit.getId(),
        unit.getBarcode(),
        reason);
    return unit;
  }

  // ── Reservation ───────────────────────────────────────────────────────────

  @Transactional
  public StockUnit reserve(UUID stockUnitId) {
    UUID tenantId = TenantContext.requireTenantId();
    UUID actorId = TenantContext.getCurrentUserId();
    StockUnit unit = loadUnit(stockUnitId, tenantId);
    unit.reserve();
    unit = stockUnitRepository.save(unit);
    writeAuditLog(
        tenantId,
        stockUnitId,
        StockUnitAuditLog.OP_RESERVE,
        "status",
        StockUnitStatus.AVAILABLE.name(),
        StockUnitStatus.RESERVED.name(),
        actorId,
        1,
        null);
    return unit;
  }

  @Transactional
  public StockUnit releaseReservation(UUID stockUnitId) {
    UUID tenantId = TenantContext.requireTenantId();
    UUID actorId = TenantContext.getCurrentUserId();
    StockUnit unit = loadUnit(stockUnitId, tenantId);
    unit.releaseReservation();
    unit = stockUnitRepository.save(unit);
    writeAuditLog(
        tenantId,
        stockUnitId,
        StockUnitAuditLog.OP_RESERVE_RELEASE,
        "status",
        StockUnitStatus.RESERVED.name(),
        unit.getStatus().name(),
        actorId,
        1,
        null);
    return unit;
  }

  // ── Bulk Creation (GR Listener convenience) ───────────────────────────────

  /**
   * Creates multiple StockUnits for the same Batch in a single transaction.
   *
   * <p>Used by the {@code GoodsReceiptConfirmedEventListener} to bulk-create units from all items
   * in a confirmed goods receipt. The {@code actorId} is still passed explicitly here because the
   * listener runs in a system context without an authenticated user.
   *
   * @param batchId parent batch
   * @param requests list of unit creation parameters
   * @param actorId explicit actor ID — use TenantContext.SYSTEM_ACTOR_ID for system-triggered
   *     operations (e.g. GR listener). Unlike other methods, this does NOT call getCurrentUserId()
   *     because the listener runs outside an authenticated request context.
   * @return list of saved StockUnits in the same order as {@code requests}
   */
  @Transactional
  public List<StockUnit> createBulk(
      UUID batchId, List<CreateStockUnitRequest> requests, UUID actorId) {
    UUID tenantId = TenantContext.requireTenantId();
    Batch batch = batchId == null ? null : loadBatch(batchId, tenantId);

    List<StockUnit> created =
        requests.stream()
            .map(
                r ->
                    internalCreate(
                        tenantId,
                        batchId,
                        r.productType(),
                        r.barcode(),
                        r.serialNumber(),
                        r.packageType(),
                        r.initialWeight(),
                        r.grossWeight(),
                        r.unit(),
                        r.length(),
                        r.lengthUnit(),
                        r.locationId(),
                        r.sourceType(),
                        r.sourceId(),
                        QualityDisposition.PENDING_INSPECTION,
                        actorId))
            .toList();
    if (!created.isEmpty()) {
      projectQualityAfterBirth(batch, tenantId);
    }
    return created;
  }

  /** Immutable command record for bulk creation — avoids long parameter lists. */
  public record CreateStockUnitRequest(
      ProductType productType,
      String barcode,
      String serialNumber,
      PackageType packageType,
      BigDecimal initialWeight,
      BigDecimal grossWeight,
      String unit,
      BigDecimal length,
      String lengthUnit,
      UUID locationId,
      StockUnitSourceType sourceType,
      UUID sourceId) {}

  // ── Private Helpers ───────────────────────────────────────────────────────

  private StockUnit loadUnit(UUID stockUnitId, UUID tenantId) {
    return stockUnitRepository
        .findById(stockUnitId)
        .filter(u -> u.getTenantId().equals(tenantId))
        .orElseThrow(() -> new NotFoundException("StockUnit not found: " + stockUnitId));
  }

  private Batch loadBatchForUpdate(UUID batchId, UUID tenantId) {
    return batchRepository
        .findByIdAndTenantIdForUpdate(batchId, tenantId)
        .orElseThrow(() -> new NotFoundException("Batch not found: " + batchId));
  }

  private Batch loadBatch(UUID batchId, UUID tenantId) {
    return batchRepository
        .findByIdAndTenantId(batchId, tenantId)
        .orElseThrow(() -> new NotFoundException("Batch not found: " + batchId));
  }

  private void projectQualityAfterBirth(Batch batch, UUID tenantId) {
    if (batch == null) {
      return;
    }
    var counts = stockUnitRepository.countQualityDispositions(tenantId, batch.getId());
    long total =
        counts.stream().mapToLong(StockUnitRepository.QualityDispositionCount::getUnitCount).sum();
    BatchStatus target = BatchStatus.QUARANTINE;
    if (counts.size() == 1
        && total > 0
        && counts.getFirst().getDisposition() == QualityDisposition.PENDING_INSPECTION) {
      target = BatchStatus.PENDING_QC;
    }
    batch.applyQualityProjection(target);
    batchRepository.save(batch);
  }

  /**
   * Batch status gate — blocks consumption/transfer if the parent batch is in a non-operational
   * state. StockUnit statuses are NOT cascaded; the batch acts as a gate only.
   */
  private void assertBatchAllowsConsumption(Batch batch) {
    if (BatchStatus.BLOCKED_FOR_PRODUCTION.contains(batch.getStatus())) {
      throw new StockUnitDomainException(
          String.format(
              "Batch %s is in status %s — consumption and transfer are blocked.",
              batch.getBatchCode(), batch.getStatus()));
    }
  }

  private void writeAuditLog(
      UUID tenantId,
      UUID stockUnitId,
      String operationType,
      String fieldName,
      String oldValue,
      String newValue,
      UUID actorId,
      int trustLevel,
      String reason) {
    auditLogRepository.save(
        StockUnitAuditLog.of(
            tenantId,
            stockUnitId,
            operationType,
            fieldName,
            oldValue,
            newValue,
            actorId,
            trustLevel,
            reason));
  }
}
