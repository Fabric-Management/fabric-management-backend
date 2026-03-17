package com.fabricmanagement.production.execution.batch.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.common.exception.InsufficientStockException;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchCertification;
import com.fabricmanagement.production.execution.batch.domain.BatchOverrideLog;
import com.fabricmanagement.production.execution.batch.domain.BatchReservation;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.domain.ReservationStatus;
import com.fabricmanagement.production.execution.batch.domain.event.*;
import com.fabricmanagement.production.execution.batch.domain.exception.BatchCertificationExpiredException;
import com.fabricmanagement.production.execution.batch.domain.exception.BatchDomainException;
import com.fabricmanagement.production.execution.batch.dto.*;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchCertificationRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchOverrideLogRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchReservationRepository;
import com.fabricmanagement.production.execution.lineage.app.BatchLineageService;
import com.fabricmanagement.production.execution.lineage.dto.CreateBatchLineageRequest;
import com.fabricmanagement.production.execution.warehouse.app.WarehouseLocationService;
import com.fabricmanagement.production.execution.warehouse.domain.WarehouseLocationType;
import com.fabricmanagement.production.execution.warehouse.dto.WarehouseLocationDto;
import com.fabricmanagement.production.masterdata.fiber.domain.Fiber;
import com.fabricmanagement.production.masterdata.fiber.domain.FiberQualityStandard;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberQualityStandardRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberRepository;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing batches with named-reservation logic. */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchService {

  private static final BigDecimal BLEND_PERCENT_TOTAL = new BigDecimal("100");

  private final BatchRepository batchRepository;
  private final BatchReservationRepository reservationRepository;
  private final BatchOverrideLogRepository overrideLogRepository;
  private final BatchCertificationRepository batchCertificationRepository;
  private final BatchCodeGenerator batchCodeGenerator;
  private final FiberRepository fiberRepository;
  private final FiberQualityStandardRepository qualityStandardRepository;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final WarehouseLocationService warehouseLocationService;
  private final BatchLineageService batchLineageService;

  @Value("${batch.certification.enforce-on-reserve:true}")
  private boolean certEnforceOnReserve;

  // ── CRUD ───────────────────────────────────────────────────────────────────

  @Transactional
  public BatchDto create(CreateBatchRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Creating batch: tenantId={}, request={}", tenantId, request);

    if (batchRepository.existsByTenantIdAndBatchCode(tenantId, request.getBatchCode())) {
      throw new BatchDomainException("Batch code already exists: " + request.getBatchCode());
    }

    Map<String, Object> attributes = resolveAttributes(request);
    if (request.getComposition() != null && !request.getComposition().isEmpty()) {
      Map<String, Object> compMap = new HashMap<>();
      for (Map.Entry<UUID, BigDecimal> e : request.getComposition().entrySet()) {
        compMap.put(e.getKey().toString(), e.getValue());
      }
      attributes.put("composition", compMap);
    }

    UUID qualityStandardId = resolveQualityStandardId(request);

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
            qualityStandardId,
            request.getRemarks(),
            attributes);

    batch = batchRepository.save(batch);

    applicationEventPublisher.publishEvent(
        new BatchCreatedEvent(
            tenantId, batch.getId(), batch.getQuantity(), batch.getUnit(), batch.getLocationId()));

    log.info("Created batch: id={}, batchCode={}", batch.getId(), batch.getBatchCode());

    return toBatchDto(batch);
  }

  /**
   * Resolves qualityStandardId for batch creation. If request has qualityStandardId, validate and
   * use it. If null and FIBER: apply default profile for material's ISO code. If no default, skip.
   */
  private UUID resolveQualityStandardId(CreateBatchRequest request) {
    if (request.getQualityStandardId() != null) {
      UUID tenantId = TenantContext.getCurrentTenantId();
      if (qualityStandardRepository
          .findByTenantIdAndId(tenantId, request.getQualityStandardId())
          .isEmpty()) {
        throw new BatchDomainException(
            "Quality standard not found: " + request.getQualityStandardId());
      }
      return request.getQualityStandardId();
    }
    if (request.getMaterialType() != MaterialType.FIBER) {
      return null;
    }
    UUID tenantId = TenantContext.getCurrentTenantId();
    Optional<Fiber> fiberOpt = fiberRepository.findByMaterialId(request.getMaterialId());
    if (fiberOpt.isEmpty()) {
      fiberOpt = fiberRepository.findById(request.getMaterialId());
    }
    if (fiberOpt.isEmpty()) {
      return null;
    }
    UUID isoCodeId = fiberOpt.get().getFiberIsoCodeId();
    if (isoCodeId == null) {
      return null;
    }
    return qualityStandardRepository
        .findByTenantIdAndIsoCode_IdAndIsDefaultTrueAndIsActiveTrue(tenantId, isoCodeId)
        .map(FiberQualityStandard::getId)
        .orElse(null);
  }

  /**
   * Builds the attributes map for the batch. For FIBER material type, maps optional fiber-specific
   * request fields into the "fiber_" prefix convention; only non-null values are included. Merges
   * with any request.getAttributes() so client can override or add.
   */
  private Map<String, Object> resolveAttributes(CreateBatchRequest request) {
    Map<String, Object> attrs =
        request.getAttributes() != null ? new HashMap<>(request.getAttributes()) : new HashMap<>();

    if (request.getMaterialType() == MaterialType.FIBER) {
      if (request.getMicronaire() != null) {
        attrs.put("fiber_micronaire", request.getMicronaire());
      }
      if (request.getStapleLength() != null) {
        attrs.put("fiber_staple_length", request.getStapleLength());
      }
      if (request.getFiberGrade() != null) {
        attrs.put("fiber_grade", request.getFiberGrade());
      }
      if (request.getFiberShade() != null) {
        attrs.put("fiber_shade", request.getFiberShade());
      }
      if (request.getOrganicCertNo() != null) {
        attrs.put("fiber_organic_cert_no", request.getOrganicCertNo());
      }
    }

    return attrs;
  }

  /**
   * Resolve effective composition: Batch.attributes.composition if present, else Fiber.composition.
   * Returns empty map for non-FIBER or when neither has composition.
   */
  @SuppressWarnings("unchecked")
  private Map<UUID, BigDecimal> resolveComposition(Batch batch) {
    if (batch.getMaterialType() != MaterialType.FIBER) {
      return Map.of();
    }
    Object compObj =
        batch.getAttributes() != null ? batch.getAttributes().get("composition") : null;
    if (compObj instanceof Map) {
      Map<?, ?> compMap = (Map<?, ?>) compObj;
      if (!compMap.isEmpty()) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        for (Map.Entry<?, ?> e : compMap.entrySet()) {
          try {
            UUID key =
                e.getKey() instanceof UUID
                    ? (UUID) e.getKey()
                    : UUID.fromString(String.valueOf(e.getKey()));
            BigDecimal val =
                e.getValue() instanceof BigDecimal
                    ? (BigDecimal) e.getValue()
                    : new BigDecimal(String.valueOf(e.getValue()));
            result.put(key, val);
          } catch (Exception ignored) {
            // skip invalid entries
          }
        }
        return result;
      }
    }
    // For FIBER, materialId references prod_fiber.id (see V061 migration)
    return fiberRepository
        .findById(batch.getMaterialId())
        .map(Fiber::getComposition)
        .orElse(Map.of());
  }

  /** Build BatchDto with resolved composition (for FIBER: batch override or fiber default). */
  public BatchDto toBatchDto(Batch batch) {
    BatchDto dto = BatchDto.from(batch);
    dto.setComposition(resolveComposition(batch));
    return dto;
  }

  @Transactional(readOnly = true)
  public List<BatchDto> getAll() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting all batches: tenantId={}", tenantId);

    return batchRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
        .map(this::toBatchDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public Optional<BatchDto> getById(UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting batch: tenantId={}, id={}", tenantId, id);

    return batchRepository
        .findById(id)
        .filter(batch -> batch.getTenantId().equals(tenantId))
        .map(this::toBatchDto);
  }

  @Transactional(readOnly = true)
  public List<BatchDto> getByMaterialId(UUID materialId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting batches by materialId: tenantId={}, materialId={}", tenantId, materialId);

    return batchRepository.findByTenantIdAndMaterialIdAndIsActiveTrue(tenantId, materialId).stream()
        .map(this::toBatchDto)
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
    assertGotsCertificationValid(batch);
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

    return toBatchDto(saved);
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

    return toBatchDto(saved);
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
    if (saved.getStatus() == BatchStatus.DEPLETED) {
      applicationEventPublisher.publishEvent(new BatchCompletedEvent(tenantId, saved.getId()));
    }

    log.info(
        "Consumed from batch: id={}, consumedQty={}, status={}",
        saved.getId(),
        saved.getConsumedQuantity(),
        saved.getStatus());

    return toBatchDto(saved);
  }

  // ── Waste ──────────────────────────────────────────────────────────────────

  /** Record production waste (fire/telef) against a batch. */
  @Transactional
  public BatchDto recordWaste(UUID batchId, RecordWasteRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug(
        "Recording waste: tenantId={}, batchId={}, quantity={}, category={}",
        tenantId,
        batchId,
        request.getQuantity(),
        request.getWasteCategory());

    Batch batch = loadBatchWithLock(batchId, tenantId);
    batch.recordWaste(request.getQuantity());

    Batch saved = batchRepository.save(batch);

    applicationEventPublisher.publishEvent(
        new BatchWasteRecordedEvent(
            tenantId,
            batchId,
            request.getQuantity(),
            saved.getUnit(),
            saved.getLocationId(),
            request.getWasteCategory(),
            request.getReason()));

    log.info(
        "Waste recorded: id={}, wasteQty={}, category={}, wastePercent={}%",
        saved.getId(),
        saved.getWasteQuantity(),
        request.getWasteCategory(),
        saved.getWastePercentage());

    return toBatchDto(saved);
  }

  /**
   * Create a blended batch atomically: one child batch from multiple parent batches (e.g. blending
   * fiber lots into one yarn batch). Ensures lineage records and parent consumption in a single
   * transaction. Child batch has {@code parentBatchId = null}; lineage is stored in
   * production_execution_batch_lineage.
   */
  @Transactional
  public BatchDto createBlendedBatch(CreateBlendedBatchRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug(
        "Creating blended batch: tenantId={}, batchCode={}, parents={}",
        tenantId,
        request.getBatchCode(),
        request.getParents().size());

    if (request.getParents() == null || request.getParents().size() < 2) {
      throw new BatchDomainException("Blending requires at least 2 parent batches");
    }

    BigDecimal pctSum =
        request.getParents().stream()
            .map(BlendParentRequest::getConsumptionPercentage)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (pctSum.compareTo(BLEND_PERCENT_TOTAL) != 0) {
      throw new BatchDomainException(
          String.format(
              "Consumption percentages must sum to 100%%, got %s%%", pctSum.stripTrailingZeros()));
    }

    List<Batch> parentBatches = new ArrayList<>();
    for (BlendParentRequest p : request.getParents()) {
      Batch parent = loadBatchWithLock(p.getParentBatchId(), tenantId);
      if (parent.getStatus() != BatchStatus.AVAILABLE) {
        throw new BatchDomainException(
            String.format(
                "Parent batch %s must be AVAILABLE for blending, current status: %s",
                parent.getBatchCode(), parent.getStatus()));
      }
      if (parent.getAvailableQuantity().compareTo(p.getConsumedQuantity()) < 0) {
        throw new InsufficientStockException(
            parent.getBatchCode(),
            p.getConsumedQuantity(),
            parent.getAvailableQuantity(),
            parent.getUnit());
      }
      parentBatches.add(parent);
    }

    if (batchRepository.existsByTenantIdAndBatchCode(tenantId, request.getBatchCode())) {
      throw new BatchDomainException("Batch code already exists: " + request.getBatchCode());
    }

    Batch childBatch =
        Batch.create(
            tenantId,
            request.getMaterialId(),
            request.getMaterialType(),
            request.getBatchCode(),
            null,
            request.getQuantity(),
            request.getUnit(),
            Instant.now(),
            null,
            request.getLocationId(),
            null,
            request.getRemarks(),
            new HashMap<>());
    childBatch.setStatus(BatchStatus.AVAILABLE);
    Batch savedChild = batchRepository.save(childBatch);

    for (int i = 0; i < request.getParents().size(); i++) {
      BlendParentRequest p = request.getParents().get(i);
      Batch parent = parentBatches.get(i);

      batchLineageService.create(
          CreateBatchLineageRequest.builder()
              .parentBatchId(parent.getId())
              .childBatchId(savedChild.getId())
              .consumedQuantity(p.getConsumedQuantity())
              .unit(parent.getUnit())
              .consumptionPercentage(p.getConsumptionPercentage())
              .consumedAt(Instant.now())
              .processReference("BLEND")
              .remarks(request.getRemarks())
              .build());

      parent.consumeFromAvailable(p.getConsumedQuantity());
      batchRepository.save(parent);

      applicationEventPublisher.publishEvent(
          new BatchConsumedEvent(
              tenantId,
              parent.getId(),
              p.getConsumedQuantity(),
              parent.getUnit(),
              parent.getLocationId(),
              savedChild.getId(),
              "BLEND"));
    }

    List<UUID> parentIds =
        request.getParents().stream().map(BlendParentRequest::getParentBatchId).toList();
    applicationEventPublisher.publishEvent(
        new BlendedBatchCreatedEvent(
            tenantId, savedChild.getId(), parentIds, request.getQuantity(), request.getUnit()));

    log.info(
        "Blended batch created: childId={}, batchCode={}, parentCount={}",
        savedChild.getId(),
        savedChild.getBatchCode(),
        request.getParents().size());
    return toBatchDto(savedChild);
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
    if (saved.getStatus() == BatchStatus.DEPLETED) {
      applicationEventPublisher.publishEvent(new BatchCompletedEvent(tenantId, saved.getId()));
    }

    log.info(
        "Batch adjusted: id={}, delta={}, newQty={}, available={}",
        saved.getId(),
        request.getDelta(),
        saved.getQuantity(),
        saved.getAvailableQuantity());

    return toBatchDto(saved);
  }

  // ── Split & Transfer ───────────────────────────────────────────────────────

  /**
   * Split batch: acceptedQuantity → new AVAILABLE batch; remainder stays in source with
   * RETURNED/DESTROYED. Both operations in a single transaction.
   *
   * @return SplitBatchResponse with source batch (updated) and new batch
   */
  @Transactional
  public SplitBatchResponse splitBatch(UUID batchId, SplitBatchRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    UUID actorId = TenantContext.getCurrentUserId();
    log.debug(
        "Splitting batch: tenantId={}, batchId={}, acceptedQty={}",
        tenantId,
        batchId,
        request.getAcceptedQuantity());

    Batch sourceBatch = loadBatchWithLock(batchId, tenantId);

    if (!Set.of(BatchStatus.PENDING_QC, BatchStatus.QUARANTINE, BatchStatus.QC_REJECTED)
        .contains(sourceBatch.getStatus())) {
      throw new BatchDomainException(
          "Split only allowed for PENDING_QC, QUARANTINE, or QC_REJECTED. Current: "
              + sourceBatch.getStatus());
    }

    BatchStatus rejectedStatus =
        request.getRejectedStatus() != null ? request.getRejectedStatus() : BatchStatus.RETURNED;
    if (!Set.of(BatchStatus.RETURNED, BatchStatus.DESTROYED).contains(rejectedStatus)) {
      throw new BatchDomainException(
          "rejectedStatus must be RETURNED or DESTROYED. Got: " + rejectedStatus);
    }

    BigDecimal acceptedQty = request.getAcceptedQuantity();
    BigDecimal totalQty = sourceBatch.getQuantity();
    if (acceptedQty.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BatchDomainException("Accepted quantity must be greater than 0");
    }
    if (acceptedQty.compareTo(totalQty) >= 0) {
      throw new BatchDomainException(
          String.format(
              "Accepted quantity (%.3f) must be less than source quantity (%.3f %s)",
              acceptedQty, totalQty, sourceBatch.getUnit()));
    }

    // 1. Source batch: reduce quantity, set status to RETURNED or DESTROYED
    sourceBatch.adjustQuantity(acceptedQty.negate());
    sourceBatch.transitionStatus(rejectedStatus, actorId);
    Batch savedSource = batchRepository.save(sourceBatch);

    // 2. New batch: acceptedQuantity, AVAILABLE, parentBatchId = source, code = -P1, -P2, ...
    String newBatchCode =
        batchCodeGenerator.generateSplitCode(sourceBatch.getId(), sourceBatch.getBatchCode());

    Batch childBatch =
        Batch.create(
            tenantId,
            sourceBatch.getMaterialId(),
            sourceBatch.getMaterialType(),
            newBatchCode,
            sourceBatch.getSupplierBatchCode(),
            acceptedQty,
            sourceBatch.getUnit(),
            Instant.now(),
            sourceBatch.getExpiryDate(),
            sourceBatch.getLocationId(),
            sourceBatch.getQualityStandardId(),
            request.getReason(),
            sourceBatch.getAttributes() != null
                ? new HashMap<>(sourceBatch.getAttributes())
                : new HashMap<>());
    childBatch.setParentBatchId(sourceBatch.getId());
    childBatch.setStatus(BatchStatus.AVAILABLE);
    Batch savedChild = batchRepository.save(childBatch);

    log.info(
        "Split batch: source {} → {} ({} {}), child {} ({} {} AVAILABLE)",
        sourceBatch.getBatchCode(),
        rejectedStatus,
        savedSource.getQuantity(),
        sourceBatch.getUnit(),
        savedChild.getBatchCode(),
        acceptedQty,
        sourceBatch.getUnit());

    applicationEventPublisher.publishEvent(
        new BatchSplitEvent(
            tenantId,
            sourceBatch.getId(),
            savedChild.getId(),
            acceptedQty,
            sourceBatch.getUnit(),
            sourceBatch.getLocationId(),
            savedChild.getLocationId(),
            sourceBatch.getBatchCode(),
            savedChild.getBatchCode(),
            request.getReason()));

    return SplitBatchResponse.builder()
        .sourceBatch(toBatchDto(savedSource))
        .newBatch(toBatchDto(savedChild))
        .build();
  }

  /**
   * Partial acceptance split (QC kısmi kabul). Source batch remainder gets rejectedStatus; new
   * batch with acceptedQuantity is AVAILABLE.
   *
   * <p>Source must be PENDING_QC or QUARANTINE. Both operations run in a single transaction.
   */
  @Transactional
  public BatchDto splitPartialAcceptance(UUID batchId, PartialAcceptanceSplitRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    UUID actorId = TenantContext.getCurrentUserId();
    log.debug(
        "Partial acceptance split: tenantId={}, batchId={}, acceptedQty={}",
        tenantId,
        batchId,
        request.getAcceptedQuantity());

    Batch sourceBatch = loadBatchWithLock(batchId, tenantId);

    if (!Set.of(BatchStatus.PENDING_QC, BatchStatus.QUARANTINE, BatchStatus.QC_REJECTED)
        .contains(sourceBatch.getStatus())) {
      throw new BatchDomainException(
          "Partial acceptance split only allowed for PENDING_QC, QUARANTINE, or QC_REJECTED. Current: "
              + sourceBatch.getStatus());
    }

    BatchStatus rejectedStatus =
        request.getRejectedStatus() != null ? request.getRejectedStatus() : BatchStatus.QC_REJECTED;
    if (!Set.of(BatchStatus.QC_REJECTED, BatchStatus.RETURNED, BatchStatus.DESTROYED)
        .contains(rejectedStatus)) {
      throw new BatchDomainException(
          "rejectedStatus must be QC_REJECTED, RETURNED, or DESTROYED. Got: " + rejectedStatus);
    }
    if (sourceBatch.getStatus() == BatchStatus.PENDING_QC
        && rejectedStatus != BatchStatus.QC_REJECTED) {
      throw new BatchDomainException(
          "From PENDING_QC, rejectedStatus must be QC_REJECTED. Use QUARANTINE first for RETURNED/DESTROYED.");
    }
    // For QC_REJECTED source, remainder stays QC_REJECTED
    if (sourceBatch.getStatus() == BatchStatus.QC_REJECTED) {
      rejectedStatus = BatchStatus.QC_REJECTED;
    }

    BigDecimal acceptedQty = request.getAcceptedQuantity();
    BigDecimal available = sourceBatch.getAvailableQuantity();
    if (acceptedQty.compareTo(available) > 0) {
      throw new BatchDomainException(
          String.format(
              "Accepted quantity (%.3f %s) exceeds available (%.3f %s) for batch %s",
              acceptedQty,
              sourceBatch.getUnit(),
              available,
              sourceBatch.getUnit(),
              sourceBatch.getBatchCode()));
    }
    if (acceptedQty.compareTo(sourceBatch.getQuantity()) >= 0) {
      throw new BatchDomainException(
          "Accepted quantity must be less than total quantity so remainder stays in source batch.");
    }

    // 1. Reduce source batch quantity (remainder stays)
    sourceBatch.adjustQuantity(acceptedQty.negate());
    sourceBatch.transitionStatus(rejectedStatus, actorId);
    batchRepository.save(sourceBatch);

    // 2. Create new batch with accepted quantity, AVAILABLE, parentBatchId = source
    String newBatchCode =
        batchCodeGenerator.generateSplitCode(sourceBatch.getId(), sourceBatch.getBatchCode());

    Batch childBatch =
        Batch.create(
            tenantId,
            sourceBatch.getMaterialId(),
            sourceBatch.getMaterialType(),
            newBatchCode,
            sourceBatch.getSupplierBatchCode(),
            acceptedQty,
            sourceBatch.getUnit(),
            Instant.now(),
            sourceBatch.getExpiryDate(),
            sourceBatch.getLocationId(),
            sourceBatch.getQualityStandardId(),
            request.getReason(),
            sourceBatch.getAttributes() != null
                ? new HashMap<>(sourceBatch.getAttributes())
                : new HashMap<>());
    childBatch.setParentBatchId(sourceBatch.getId());
    childBatch.setStatus(BatchStatus.AVAILABLE);
    Batch savedChild = batchRepository.save(childBatch);

    log.info(
        "Partial acceptance split: source {} → {} ({} {} rejected), child {} ({} {} accepted)",
        sourceBatch.getBatchCode(),
        rejectedStatus,
        sourceBatch.getQuantity(),
        sourceBatch.getUnit(),
        savedChild.getBatchCode(),
        acceptedQty,
        sourceBatch.getUnit());

    applicationEventPublisher.publishEvent(
        new BatchSplitEvent(
            tenantId,
            sourceBatch.getId(),
            savedChild.getId(),
            acceptedQty,
            sourceBatch.getUnit(),
            sourceBatch.getLocationId(),
            savedChild.getLocationId(),
            sourceBatch.getBatchCode(),
            savedChild.getBatchCode(),
            request.getReason()));

    return toBatchDto(savedChild);
  }

  /**
   * Override batch status (QC_REJECTED or QUARANTINE → AVAILABLE). Logs to override_log for audit.
   */
  @Transactional
  public BatchDto overrideStatus(UUID batchId, OverrideStatusRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    UUID actorId = TenantContext.getCurrentUserId();
    log.debug("Override batch status: tenantId={}, batchId={}", tenantId, batchId);

    Batch batch = loadBatchWithLock(batchId, tenantId);

    if (!Set.of(BatchStatus.QC_REJECTED, BatchStatus.QUARANTINE).contains(batch.getStatus())) {
      throw new BatchDomainException(
          "Override only allowed for QC_REJECTED or QUARANTINE. Current: " + batch.getStatus());
    }

    BatchStatus fromStatus = batch.getStatus();
    batch.transitionStatus(BatchStatus.AVAILABLE, actorId);
    batchRepository.save(batch);

    BatchOverrideLog logEntry =
        BatchOverrideLog.create(
            batchId, fromStatus.name(), BatchStatus.AVAILABLE.name(), actorId, request.getReason());
    overrideLogRepository.save(logEntry);

    log.info(
        "Batch status overridden: batchId={}, {} → AVAILABLE, reason={}",
        batchId,
        fromStatus,
        request.getReason());

    return toBatchDto(batch);
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
    return toBatchDto(saved);
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
    assertGotsCertificationValid(batch);

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

    return toBatchDto(saved);
  }

  // ── Internal ───────────────────────────────────────────────────────────────

  /**
   * When {@code batch.certification.enforce-on-reserve} is true, blocks reserve/start-production
   * for organic FIBER batches (fiber_organic_cert_no present) that have no valid GOTS certification
   * (validUntil null or >= today). Uses repository directly to avoid circular dependency with
   * BatchCertificationService.
   *
   * <p>Technical debt: organic detection is based on attributes key; consider BatchAttribute
   * ORGANIC=true in future.
   */
  private void assertGotsCertificationValid(Batch batch) {
    if (!certEnforceOnReserve) return;
    if (batch.getMaterialType() != MaterialType.FIBER) return;
    boolean isOrganic =
        batch.getAttributes() != null && batch.getAttributes().containsKey("fiber_organic_cert_no");
    if (!isOrganic) return;

    LocalDate today = LocalDate.now();
    List<BatchCertification> certs =
        batchCertificationRepository.findByBatch_IdAndIsActiveTrueWithAssociations(batch.getId());
    boolean hasValid =
        certs.stream()
            .filter(cert -> BatchCertificationPredicates.isCertificationStillValid(cert, today))
            .anyMatch(BatchCertificationPredicates::isGotsCertification);
    if (hasValid) return;

    LocalDate expiredDate = findLatestExpiredDateAmongGots(certs);
    throw new BatchCertificationExpiredException(batch.getBatchCode(), "GOTS", expiredDate);
  }

  private static LocalDate findLatestExpiredDateAmongGots(List<BatchCertification> certs) {
    return certs.stream()
        .filter(BatchCertificationPredicates::isGotsCertification)
        .map(BatchCertification::getValidUntil)
        .filter(Objects::nonNull)
        .max(LocalDate::compareTo)
        .orElse(null);
  }

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
