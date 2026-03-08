package com.fabricmanagement.production.execution.batch.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchReservation;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.domain.ReservationStatus;
import com.fabricmanagement.production.execution.batch.domain.event.*;
import com.fabricmanagement.production.execution.batch.domain.exception.BatchDomainException;
import com.fabricmanagement.production.execution.batch.dto.*;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchReservationRepository;
import com.fabricmanagement.production.execution.warehouse.app.WarehouseLocationService;
import com.fabricmanagement.production.execution.warehouse.domain.WarehouseLocationType;
import com.fabricmanagement.production.execution.warehouse.dto.WarehouseLocationDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing batches with named-reservation logic. */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchService {

  private final BatchRepository batchRepository;
  private final BatchReservationRepository reservationRepository;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final WarehouseLocationService warehouseLocationService;

  // ── CRUD ───────────────────────────────────────────────────────────────────

  @Transactional
  public BatchDto create(CreateBatchRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Creating batch: tenantId={}, request={}", tenantId, request);

    if (batchRepository.existsByTenantIdAndBatchCode(tenantId, request.getBatchCode())) {
      throw new BatchDomainException("Batch code already exists: " + request.getBatchCode());
    }

    Batch batch =
        Batch.create(
            tenantId,
            request.getMaterialId(),
            request.getMaterialType(),
            request.getBatchCode(),
            request.getSupplierBatchCode(),
            request.getQuantity(),
            request.getUnit(),
            request.getProductionDate() != null ? request.getProductionDate() : Instant.now(),
            request.getExpiryDate(),
            request.getLocationId(),
            request.getRemarks(),
            request.getAttributes());

    batch = batchRepository.save(batch);

    applicationEventPublisher.publishEvent(
        new BatchCreatedEvent(
            tenantId, batch.getId(), batch.getQuantity(), batch.getUnit(), batch.getLocationId()));

    log.info("Created batch: id={}, batchCode={}", batch.getId(), batch.getBatchCode());

    return BatchDto.from(batch);
  }

  @Transactional(readOnly = true)
  public List<BatchDto> getAll() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting all batches: tenantId={}", tenantId);

    return batchRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
        .map(BatchDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public Optional<BatchDto> getById(UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting batch: tenantId={}, id={}", tenantId, id);

    return batchRepository
        .findById(id)
        .filter(batch -> batch.getTenantId().equals(tenantId))
        .map(BatchDto::from);
  }

  @Transactional(readOnly = true)
  public List<BatchDto> getByMaterialId(UUID materialId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting batches by materialId: tenantId={}, materialId={}", tenantId, materialId);

    return batchRepository.findByTenantIdAndMaterialIdAndIsActiveTrue(tenantId, materialId).stream()
        .map(BatchDto::from)
        .toList();
  }

  // ── Named Reservation ──────────────────────────────────────────────────────

  /**
   * Reserve quantity from a batch against a named reference (work order, sample request, etc.).
   * Creates a {@link BatchReservation} record and updates the batch's denormalized counter.
   */
  @Transactional
  public BatchReservationDto reserve(UUID batchId, ReserveRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug(
        "Reserving batch: tenantId={}, batchId={}, qty={}, refType={}",
        tenantId,
        batchId,
        request.getQuantity(),
        request.getReferenceType());

    Batch batch = loadBatchWithLock(batchId, tenantId);
    batch.reserve(request.getQuantity());

    BatchReservation reservation =
        BatchReservation.create(
            tenantId,
            batchId,
            request.getReferenceId(),
            request.getReferenceType(),
            request.getQuantity(),
            batch.getUnit(),
            request.getRemarks());

    batchRepository.save(batch);
    reservation = reservationRepository.save(reservation);

    applicationEventPublisher.publishEvent(
        new BatchReservedEvent(
            tenantId,
            batchId,
            request.getQuantity(),
            batch.getUnit(),
            batch.getLocationId(),
            reservation.getId(),
            request.getReferenceType()));

    log.info(
        "Reserved batch: id={}, reservationId={}, reservedQty={}, availableQty={}",
        batch.getId(),
        reservation.getId(),
        batch.getReservedQuantity(),
        batch.getAvailableQuantity());

    return BatchReservationDto.from(reservation);
  }

  /**
   * Release (cancel) a specific reservation. The remaining unconsumed quantity is released back to
   * the batch's available stock.
   */
  @Transactional
  public BatchDto releaseReservation(UUID batchId, UUID reservationId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug(
        "Releasing reservation: tenantId={}, batchId={}, reservationId={}",
        tenantId,
        batchId,
        reservationId);

    BatchReservation reservation = loadReservationWithLock(reservationId, tenantId);

    if (!reservation.getBatchId().equals(batchId)) {
      throw new BatchDomainException(
          "Reservation " + reservationId + " does not belong to batch " + batchId);
    }

    BigDecimal releasedQty = reservation.cancel();

    Batch batch = loadBatchWithLock(reservation.getBatchId(), tenantId);
    if (releasedQty.compareTo(BigDecimal.ZERO) > 0) {
      batch.release(releasedQty);
    }

    reservationRepository.save(reservation);
    Batch saved = batchRepository.save(batch);

    applicationEventPublisher.publishEvent(
        new BatchReservationReleasedEvent(
            tenantId,
            batch.getId(),
            releasedQty,
            batch.getUnit(),
            batch.getLocationId(),
            reservationId));

    log.info(
        "Released reservation: reservationId={}, releasedQty={}, batchAvailable={}",
        reservationId,
        releasedQty,
        saved.getAvailableQuantity());

    return BatchDto.from(saved);
  }

  /**
   * Complete a specific reservation. Any remaining unconsumed quantity is released back to the
   * batch's available stock.
   */
  @Transactional
  public BatchDto completeReservation(UUID batchId, UUID reservationId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug(
        "Completing reservation: tenantId={}, batchId={}, reservationId={}",
        tenantId,
        batchId,
        reservationId);

    BatchReservation reservation = loadReservationWithLock(reservationId, tenantId);

    if (!reservation.getBatchId().equals(batchId)) {
      throw new BatchDomainException(
          "Reservation " + reservationId + " does not belong to batch " + batchId);
    }

    BigDecimal releasedQty = reservation.complete();

    Batch batch = loadBatchWithLock(reservation.getBatchId(), tenantId);
    if (releasedQty.compareTo(BigDecimal.ZERO) > 0) {
      batch.release(releasedQty);
    }

    reservationRepository.save(reservation);
    Batch saved = batchRepository.save(batch);

    applicationEventPublisher.publishEvent(
        new BatchReservationCompletedEvent(
            tenantId,
            batch.getId(),
            reservationId,
            releasedQty,
            batch.getUnit(),
            batch.getLocationId()));

    log.info(
        "Completed reservation: reservationId={}, releasedQty={}, batchAvailable={}",
        reservationId,
        releasedQty,
        saved.getAvailableQuantity());

    return BatchDto.from(saved);
  }

  /** List all reservations for a batch (active + partially consumed). */
  @Transactional(readOnly = true)
  public List<BatchReservationDto> getReservations(UUID batchId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return reservationRepository
        .findByTenantIdAndBatchIdAndStatusInAndIsActiveTrue(
            tenantId,
            batchId,
            Set.of(ReservationStatus.ACTIVE, ReservationStatus.PARTIALLY_CONSUMED))
        .stream()
        .map(BatchReservationDto::from)
        .toList();
  }

  // ── Consume ────────────────────────────────────────────────────────────────

  /**
   * Consume quantity from batch.
   *
   * <p>When {@code request.reservationId} is provided, consumption is drawn from that specific
   * reservation's reserved stock. When omitted, consumption is drawn from unreserved available
   * stock only -- protecting other work orders' reservations.
   */
  @Transactional
  public BatchDto consume(UUID batchId, ConsumeRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    BigDecimal qty = request.getQuantity();

    log.debug(
        "Consuming batch: tenantId={}, batchId={}, qty={}, reservationId={}",
        tenantId,
        batchId,
        qty,
        request.getReservationId());

    Batch batch = loadBatchWithLock(batchId, tenantId);

    UUID referenceId = null;
    String referenceType = null;

    if (request.getReservationId() != null) {
      BatchReservation reservation = loadReservationWithLock(request.getReservationId(), tenantId);

      if (!reservation.getBatchId().equals(batchId)) {
        throw new BatchDomainException(
            "Reservation " + reservation.getId() + " does not belong to batch " + batchId);
      }

      reservation.consume(qty);
      batch.consumeFromReservation(qty);

      reservationRepository.save(reservation);
      referenceId = reservation.getReferenceId();
      referenceType = reservation.getReferenceType();
    } else {
      batch.consumeFromAvailable(qty);
    }

    Batch saved = batchRepository.save(batch);

    applicationEventPublisher.publishEvent(
        new BatchConsumedEvent(
            tenantId,
            batchId,
            qty,
            saved.getUnit(),
            saved.getLocationId(),
            referenceId,
            referenceType));

    log.info(
        "Consumed from batch: id={}, consumedQty={}, status={}",
        saved.getId(),
        saved.getConsumedQuantity(),
        saved.getStatus());

    return BatchDto.from(saved);
  }

  // ── Waste ──────────────────────────────────────────────────────────────────

  /** Record production waste (fire/telef) against a batch. */
  @Transactional
  public BatchDto recordWaste(UUID batchId, BigDecimal quantity) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Recording waste: tenantId={}, batchId={}, quantity={}", tenantId, batchId, quantity);

    Batch batch = loadBatchWithLock(batchId, tenantId);
    batch.recordWaste(quantity);

    Batch saved = batchRepository.save(batch);

    applicationEventPublisher.publishEvent(
        new BatchWasteRecordedEvent(
            tenantId, batchId, quantity, saved.getUnit(), saved.getLocationId()));

    log.info(
        "Waste recorded: id={}, wasteQty={}, wastePercent={}%",
        saved.getId(), saved.getWasteQuantity(), saved.getWastePercentage());

    return BatchDto.from(saved);
  }

  // ── Inventory Adjustment ───────────────────────────────────────────────────

  /**
   * Adjust the total quantity of a batch (physical count correction, write-off, damage, etc.). Logs
   * an ADJUSTMENT inventory transaction for audit trail.
   */
  @Transactional
  public BatchDto adjust(UUID batchId, AdjustmentRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug(
        "Adjusting batch: tenantId={}, batchId={}, delta={}, reason={}",
        tenantId,
        batchId,
        request.getDelta(),
        request.getReason());

    Batch batch = loadBatchWithLock(batchId, tenantId);
    batch.adjustQuantity(request.getDelta());

    Batch saved = batchRepository.save(batch);

    applicationEventPublisher.publishEvent(
        new BatchAdjustedEvent(
            tenantId,
            batchId,
            request.getDelta(),
            saved.getUnit(),
            saved.getLocationId(),
            request.getReason(),
            request.getRemarks()));

    log.info(
        "Batch adjusted: id={}, delta={}, newQty={}, available={}",
        saved.getId(),
        request.getDelta(),
        saved.getQuantity(),
        saved.getAvailableQuantity());

    return BatchDto.from(saved);
  }

  // ── Split & Transfer ───────────────────────────────────────────────────────

  @Transactional
  public BatchDto splitBatch(UUID parentBatchId, SplitBatchRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug(
        "Splitting batch: tenantId={}, parentBatchId={}, request={}",
        tenantId,
        parentBatchId,
        request);

    Batch parentBatch = loadBatchWithLock(parentBatchId, tenantId);

    if (batchRepository.existsByTenantIdAndBatchCode(tenantId, request.getNewBatchCode())) {
      throw new BatchDomainException("Batch code already exists: " + request.getNewBatchCode());
    }

    // 1. Consume from parent
    parentBatch.consumeFromAvailable(request.getSplitQuantity());
    Batch savedParent = batchRepository.save(parentBatch);

    // 2. Create child batch
    Batch childBatch =
        Batch.create(
            tenantId,
            parentBatch.getMaterialId(),
            parentBatch.getMaterialType(),
            request.getNewBatchCode(),
            parentBatch.getSupplierBatchCode(),
            request.getSplitQuantity(),
            parentBatch.getUnit(),
            Instant.now(),
            parentBatch.getExpiryDate(),
            request.getNewLocationId(),
            request.getRemarks(),
            parentBatch.getAttributes() != null
                ? new java.util.HashMap<>(parentBatch.getAttributes())
                : new java.util.HashMap<>());
    Batch savedChild = batchRepository.save(childBatch);

    // 3. Publish event
    applicationEventPublisher.publishEvent(
        new BatchSplitEvent(
            tenantId,
            savedParent.getId(),
            savedChild.getId(),
            request.getSplitQuantity(),
            savedParent.getUnit(),
            savedParent.getLocationId(),
            savedChild.getLocationId(),
            savedParent.getBatchCode(),
            savedChild.getBatchCode(),
            request.getRemarks()));

    log.info(
        "Successfully split batch {} into {}",
        savedParent.getBatchCode(),
        savedChild.getBatchCode());
    return BatchDto.from(savedChild);
  }

  @Transactional
  public BatchDto transferBatch(UUID batchId, TransferBatchRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug(
        "Transferring batch: tenantId={}, batchId={}, request={}", tenantId, batchId, request);

    Batch batch = loadBatchWithLock(batchId, tenantId);

    UUID oldLocationId = batch.getLocationId();
    batch.setLocationId(request.getNewLocationId());
    Batch saved = batchRepository.save(batch);

    applicationEventPublisher.publishEvent(
        new BatchTransferredEvent(
            tenantId,
            batchId,
            batch.getQuantity(),
            batch.getUnit(),
            oldLocationId,
            request.getNewLocationId(),
            request.getRemarks()));

    log.info(
        "Successfully transferred batch {} to location {}",
        batch.getBatchCode(),
        request.getNewLocationId());
    return BatchDto.from(saved);
  }

  // ── Start Production (WIP) ─────────────────────────────────────────────────

  /**
   * Start production: transfer batch to a machine location and mark IN_PROGRESS.
   *
   * <p>Per the WIP Location Strategy, material is NOT consumed when entering production. It is
   * transferred to the machine's warehouse location, keeping it visible in the system. Real
   * consumption only occurs when production completes and a new product is created (Lineage).
   */
  @Transactional
  public BatchDto startProduction(UUID batchId, StartProductionRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug(
        "Starting production: tenantId={}, batchId={}, machineLocationId={}",
        tenantId,
        batchId,
        request.getMachineLocationId());

    Batch batch = loadBatchWithLock(batchId, tenantId);

    if (batch.getStatus() == BatchStatus.DEPLETED) {
      throw new BatchDomainException(
          "Cannot start production on a depleted batch: " + batch.getBatchCode());
    }
    if (batch.getStatus() == BatchStatus.IN_PROGRESS) {
      throw new BatchDomainException("Batch is already in production: " + batch.getBatchCode());
    }

    WarehouseLocationDto machineLocation =
        warehouseLocationService.getById(request.getMachineLocationId());
    if (machineLocation.getType() != WarehouseLocationType.MACHINE
        && machineLocation.getType() != WarehouseLocationType.PRODUCTION_LINE) {
      throw new BatchDomainException(
          "Target location must be a MACHINE or PRODUCTION_LINE, got: "
              + machineLocation.getType());
    }

    UUID previousLocationId = batch.getLocationId();

    batch.setLocationId(request.getMachineLocationId());
    batch.markInUse();

    Batch saved = batchRepository.save(batch);

    applicationEventPublisher.publishEvent(
        new BatchProductionStartedEvent(
            tenantId,
            batchId,
            batch.getQuantity(),
            batch.getUnit(),
            previousLocationId,
            request.getMachineLocationId(),
            machineLocation.getCode()));

    log.info(
        "Production started: batchId={}, batchCode={}, machine={}, status={}",
        saved.getId(),
        saved.getBatchCode(),
        machineLocation.getCode(),
        saved.getStatus());

    return BatchDto.from(saved);
  }

  // ── Internal ───────────────────────────────────────────────────────────────

  private Batch loadBatchWithLock(UUID id, UUID tenantId) {
    return batchRepository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new NotFoundException("Batch not found: " + id));
  }

  private BatchReservation loadReservationWithLock(UUID id, UUID tenantId) {
    return reservationRepository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new NotFoundException("Reservation not found: " + id));
  }
}
