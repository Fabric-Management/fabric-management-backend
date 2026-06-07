package com.fabricmanagement.production.execution.batch.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.common.exception.InsufficientStockException;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchOverrideLog;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.domain.CreateBatchCommand;
import com.fabricmanagement.production.execution.batch.domain.event.*;
import com.fabricmanagement.production.execution.batch.domain.exception.BatchDomainException;
import com.fabricmanagement.production.execution.batch.domain.port.LocationValidationResult;
import com.fabricmanagement.production.execution.batch.domain.port.WarehouseLocationPort;
import com.fabricmanagement.production.execution.batch.dto.*;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchOverrideLogRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.lineage.app.BatchLineageService;
import com.fabricmanagement.production.execution.lineage.dto.CreateBatchLineageRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles complex batch operations that go beyond simple CRUD and stock management.
 *
 * <p>Extracted from {@link BatchService} (God Class refactoring — F8). Responsible for:
 *
 * <ul>
 *   <li>Blended batch creation (multi-parent lineage)
 *   <li>Inventory quantity adjustment
 *   <li>Split (QC accept/reject partial acceptance)
 *   <li>Status override (audit-logged)
 *   <li>Location transfer
 *   <li>Start production (WIP)
 * </ul>
 *
 * <p>Delegates entity loading and DTO mapping to {@link BatchService} to avoid duplication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchOperationsService {

  private static final BigDecimal BLEND_PERCENT_TOTAL = new BigDecimal("100");

  private final BatchService batchService;
  private final BatchRepository batchRepository;
  private final BatchOverrideLogRepository overrideLogRepository;
  private final BatchCodeGenerator batchCodeGenerator;
  private final BatchLineageService batchLineageService;
  private final WarehouseLocationPort warehouseLocationPort;
  private final ApplicationEventPublisher applicationEventPublisher;

  // ── Blend ─────────────────────────────────────────────────────────────────

  /**
   * Create a blended batch atomically: one child batch from multiple parent batches (e.g. blending
   * fiber lots into one yarn batch). Ensures lineage records and parent consumption in a single
   * transaction.
   */
  @Transactional
  public BatchDto createBlendedBatch(CreateBlendedBatchRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
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
      Batch parent = loadBatch(p.getParentBatchId(), tenantId);
      if (parent.getStatus() != BatchStatus.AVAILABLE) {
        throw new BatchDomainException(
            String.format(
                "Parent batch %s must be AVAILABLE for blending, current status: %s",
                parent.getBatchCode(), parent.getStatus()));
      }
      if (parent.getAvailableQuantity().compareTo(p.getConsumedQuantity()) < 0) {
        throw new InsufficientStockException(
            parent.getId(),
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
            new CreateBatchCommand(
                tenantId,
                request.getProductId(),
                request.getProductType(),
                request.getBatchCode(),
                null,
                request.getQuantity(),
                request.getUnit(),
                Instant.now(),
                null,
                request.getLocationId(),
                null,
                request.getRemarks(),
                new HashMap<>(),
                request.getSourceType(),
                request.getSourceId()));
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
    return batchService.toBatchDto(savedChild);
  }

  // ── Inventory Adjustment ───────────────────────────────────────────────────

  /**
   * Adjust the total quantity of a batch (physical count correction, write-off, damage, etc.). Logs
   * an ADJUSTMENT inventory transaction for audit trail.
   */
  @Transactional
  public BatchDto adjust(UUID batchId, AdjustmentRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    log.debug(
        "Adjusting batch: tenantId={}, batchId={}, delta={}, reason={}",
        tenantId,
        batchId,
        request.getDelta(),
        request.getReason());

    Batch batch = loadBatch(batchId, tenantId);
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

    return batchService.toBatchDto(saved);
  }

  // ── Split ──────────────────────────────────────────────────────────────────

  /**
   * Split batch: acceptedQuantity → new AVAILABLE batch; remainder stays in source with
   * RETURNED/DESTROYED. Both operations in a single transaction.
   */
  @Transactional
  public SplitBatchResponse splitBatch(UUID batchId, SplitBatchRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    UUID actorId = TenantContext.getCurrentUserId();
    log.debug(
        "Splitting batch: tenantId={}, batchId={}, acceptedQty={}",
        tenantId,
        batchId,
        request.getAcceptedQuantity());

    Batch sourceBatch = loadBatch(batchId, tenantId);

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

    sourceBatch.adjustQuantity(acceptedQty.negate());
    sourceBatch.transitionStatus(rejectedStatus, actorId);
    Batch savedSource = batchRepository.save(sourceBatch);

    String newBatchCode =
        batchCodeGenerator.generateSplitCode(sourceBatch.getId(), sourceBatch.getBatchCode());

    Batch childBatch =
        Batch.create(
            new CreateBatchCommand(
                tenantId,
                sourceBatch.getProductId(),
                sourceBatch.getProductType(),
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
                    : new HashMap<>(),
                sourceBatch.getSourceType(),
                sourceBatch.getSourceId()));
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
        .sourceBatch(batchService.toBatchDto(savedSource))
        .newBatch(batchService.toBatchDto(savedChild))
        .build();
  }

  /**
   * Partial acceptance split (QC kısmi kabul). Source batch remainder gets rejectedStatus; new
   * batch with acceptedQuantity is AVAILABLE. Source must be PENDING_QC or QUARANTINE.
   */
  @Transactional
  public BatchDto splitPartialAcceptance(UUID batchId, PartialAcceptanceSplitRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    UUID actorId = TenantContext.getCurrentUserId();
    log.debug(
        "Partial acceptance split: tenantId={}, batchId={}, acceptedQty={}",
        tenantId,
        batchId,
        request.getAcceptedQuantity());

    Batch sourceBatch = loadBatch(batchId, tenantId);

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

    sourceBatch.adjustQuantity(acceptedQty.negate());
    sourceBatch.transitionStatus(rejectedStatus, actorId);
    batchRepository.save(sourceBatch);

    String newBatchCode =
        batchCodeGenerator.generateSplitCode(sourceBatch.getId(), sourceBatch.getBatchCode());

    Batch childBatch =
        Batch.create(
            new CreateBatchCommand(
                tenantId,
                sourceBatch.getProductId(),
                sourceBatch.getProductType(),
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
                    : new HashMap<>(),
                sourceBatch.getSourceType(),
                sourceBatch.getSourceId()));
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

    return batchService.toBatchDto(savedChild);
  }

  // ── Override ───────────────────────────────────────────────────────────────

  /**
   * Override batch status (QC_REJECTED or QUARANTINE → AVAILABLE). Logs to override_log for audit.
   */
  @Transactional
  public BatchDto overrideStatus(UUID batchId, OverrideStatusRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    UUID actorId = TenantContext.getCurrentUserId();
    log.debug("Override batch status: tenantId={}, batchId={}", tenantId, batchId);

    Batch batch = loadBatch(batchId, tenantId);

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

    return batchService.toBatchDto(batch);
  }

  // ── Transfer ───────────────────────────────────────────────────────────────

  /** Transfer batch to a new warehouse location. */
  @Transactional
  public BatchDto transferBatch(UUID batchId, TransferBatchRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    log.debug(
        "Transferring batch: tenantId={}, batchId={}, request={}", tenantId, batchId, request);

    Batch batch = loadBatch(batchId, tenantId);

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
    return batchService.toBatchDto(saved);
  }

  // ── Start Production ───────────────────────────────────────────────────────

  /**
   * Start production: transfer batch to a machine location and mark IN_PROGRESS.
   *
   * <p>Per the WIP Location Strategy, product is NOT consumed when entering production. It is
   * transferred to the machine's warehouse location, keeping it visible in the system.
   */
  @Transactional
  public BatchDto startProduction(UUID batchId, StartProductionRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    log.debug(
        "Starting production: tenantId={}, batchId={}, machineLocationId={}",
        tenantId,
        batchId,
        request.getMachineLocationId());

    Batch batch = loadBatch(batchId, tenantId);
    batchService.assertGotsCertificationValid(batch);

    if (batch.getStatus() == BatchStatus.DEPLETED) {
      throw new BatchDomainException(
          "Cannot start production on a depleted batch: " + batch.getBatchCode());
    }
    if (batch.getStatus() == BatchStatus.IN_PROGRESS) {
      throw new BatchDomainException("Batch is already in production: " + batch.getBatchCode());
    }

    LocationValidationResult locationResult =
        warehouseLocationPort.validateProductionLocation(request.getMachineLocationId());
    if (!locationResult.validProductionLocation()) {
      throw new BatchDomainException(
          "Target location must be a MACHINE or PRODUCTION_LINE. Location '"
              + locationResult.locationCode()
              + "' is not a valid production location.");
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
            locationResult.locationCode()));

    log.info(
        "Production started: batchId={}, batchCode={}, machine={}, status={}",
        saved.getId(),
        saved.getBatchCode(),
        locationResult.locationCode(),
        saved.getStatus());

    return batchService.toBatchDto(saved);
  }

  // ── Internal ───────────────────────────────────────────────────────────────

  private Batch loadBatch(UUID id, UUID tenantId) {
    return batchRepository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new NotFoundException("Batch not found: " + id));
  }
}
