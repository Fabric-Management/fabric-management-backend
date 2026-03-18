package com.fabricmanagement.production.execution.batch.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchCertification;
import com.fabricmanagement.production.execution.batch.domain.BatchReservation;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.domain.CreateBatchCommand;
import com.fabricmanagement.production.execution.batch.domain.ReservationStatus;
import com.fabricmanagement.production.execution.batch.domain.event.*;
import com.fabricmanagement.production.execution.batch.domain.exception.BatchCertificationExpiredException;
import com.fabricmanagement.production.execution.batch.domain.exception.BatchDomainException;
import com.fabricmanagement.production.execution.batch.dto.*;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchCertificationRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchReservationRepository;
import com.fabricmanagement.production.masterdata.fiber.domain.Fiber;
import com.fabricmanagement.production.masterdata.fiber.domain.FiberQualityStandard;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberQualityStandardRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberRepository;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

  private final BatchRepository batchRepository;
  private final BatchReservationRepository reservationRepository;
  private final BatchCertificationRepository batchCertificationRepository;
  private final FiberRepository fiberRepository;
  private final FiberQualityStandardRepository qualityStandardRepository;
  private final ApplicationEventPublisher applicationEventPublisher;

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
            new CreateBatchCommand(
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
                attributes));

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

  // ── Internal ───────────────────────────────────────────────────────────────

  /**
   * Validates GOTS certification for organic FIBER batches before reserve/start-production.
   *
   * <p>Package-private: intentionally accessible to {@link BatchOperationsService} which handles
   * {@code startProduction}. Not intended for use outside the {@code batch.app} package.
   *
   * <p>Technical debt: organic detection is based on attributes key {@code fiber_organic_cert_no};
   * consider a dedicated {@code BatchAttribute.ORGANIC} flag in future.
   */
  void assertGotsCertificationValid(Batch batch) {
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
