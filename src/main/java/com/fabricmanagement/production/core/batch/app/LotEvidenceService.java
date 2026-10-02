package com.fabricmanagement.production.core.batch.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.domain.BatchFinishedWidthMeasurement;
import com.fabricmanagement.production.core.batch.domain.LotCompatibilityConfirmation;
import com.fabricmanagement.production.core.batch.dto.LotEvidenceDtos;
import com.fabricmanagement.production.core.batch.infra.repository.BatchFinishedWidthMeasurementRepository;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.core.batch.infra.repository.LotCompatibilityConfirmationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records lot evidence order intake depends on: measured finished width (SOI IK-08) and the
 * technical confirmation that lots may ship together (SOI A04). Evidence is never invented here; a
 * missing record keeps the dependent decision unknown.
 */
@Service
@RequiredArgsConstructor
public class LotEvidenceService {

  private final BatchRepository batchRepository;
  private final BatchFinishedWidthMeasurementRepository widthRepository;
  private final LotCompatibilityConfirmationRepository compatibilityRepository;
  private final LotCompatibilityRequestService requestService;
  private final Clock clock;

  @Transactional
  public LotEvidenceDtos.FinishedWidthMeasurementDto recordFinishedWidth(
      UUID batchId, LotEvidenceDtos.RecordFinishedWidthRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    Batch batch = batch(tenantId, batchId);
    if (batch.getProductType() != ProductType.FABRIC) {
      throw new IllegalArgumentException("Finished width is recorded for fabric lots only");
    }
    BatchFinishedWidthMeasurement saved =
        widthRepository.save(
            BatchFinishedWidthMeasurement.record(
                batchId,
                request.value(),
                request.unit(),
                request.methodNote(),
                actor(),
                Instant.now(clock)));
    return toDto(saved);
  }

  @Transactional(readOnly = true)
  public List<LotEvidenceDtos.FinishedWidthMeasurementDto> finishedWidths(UUID batchId) {
    UUID tenantId = TenantContext.requireTenantId();
    batch(tenantId, batchId);
    return widthRepository
        .findByTenantIdAndBatchIdAndIsActiveTrueOrderByMeasuredAtDesc(tenantId, batchId)
        .stream()
        .map(LotEvidenceService::toDto)
        .toList();
  }

  /**
   * Confirms that lots of the same product and colour may ship together. Different products or
   * colours are never "compatible lots"; that would be a different order line.
   */
  @Transactional
  public LotEvidenceDtos.CompatibilityConfirmationDto confirmCompatibility(
      LotEvidenceDtos.ConfirmCompatibilityRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    Set<UUID> ids = new HashSet<>(request.batchIds());
    List<Batch> batches = batchRepository.findByTenantIdAndIdInAndIsActiveTrue(tenantId, ids);
    if (batches.size() != ids.size()) {
      throw new NotFoundException("One or more lots were not found");
    }
    Batch first = batches.getFirst();
    boolean sameArticle =
        batches.stream()
            .allMatch(
                batch ->
                    Objects.equals(batch.getProductId(), first.getProductId())
                        && Objects.equals(batch.getColorId(), first.getColorId()));
    if (!sameArticle) {
      throw new IllegalArgumentException(
          "Only lots of the same product and colour can be confirmed as compatible");
    }
    LotCompatibilityConfirmation saved =
        compatibilityRepository.save(
            LotCompatibilityConfirmation.confirm(
                ids, request.customerId(), request.conditions(), actor(), Instant.now(clock)));
    requestService.answeredBy(tenantId, saved);
    return toDto(saved);
  }

  @Transactional
  public LotEvidenceDtos.CompatibilityConfirmationDto revokeCompatibility(UUID confirmationId) {
    UUID tenantId = TenantContext.requireTenantId();
    LotCompatibilityConfirmation confirmation =
        compatibilityRepository
            .findByTenantIdAndId(tenantId, confirmationId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Compatibility confirmation not found: " + confirmationId));
    confirmation.revoke(actor(), Instant.now(clock));
    return toDto(compatibilityRepository.save(confirmation));
  }

  @Transactional(readOnly = true)
  public List<LotEvidenceDtos.CompatibilityConfirmationDto> compatibilityFor(UUID batchId) {
    UUID tenantId = TenantContext.requireTenantId();
    return compatibilityRepository
        .findByTenantIdAndRevokedAtIsNullAndIsActiveTrue(tenantId)
        .stream()
        .filter(confirmation -> batchId == null || confirmation.getBatchIds().contains(batchId))
        .map(LotEvidenceService::toDto)
        .toList();
  }

  private Batch batch(UUID tenantId, UUID batchId) {
    return batchRepository
        .findByIdAndTenantId(batchId, tenantId)
        .filter(batch -> Boolean.TRUE.equals(batch.getIsActive()))
        .orElseThrow(() -> new NotFoundException("Lot not found: " + batchId));
  }

  private static UUID actor() {
    UUID actor = TenantContext.getCurrentUserId();
    if (actor == null) {
      throw new IllegalStateException("An authenticated actor is required for lot evidence");
    }
    return actor;
  }

  static LotEvidenceDtos.FinishedWidthMeasurementDto toDto(BatchFinishedWidthMeasurement value) {
    return new LotEvidenceDtos.FinishedWidthMeasurementDto(
        value.getId(),
        value.getBatchId(),
        value.getWidthValue(),
        value.getWidthUnit(),
        value.getMethodNote(),
        value.getMeasuredBy(),
        value.getMeasuredAt());
  }

  static LotEvidenceDtos.CompatibilityConfirmationDto toDto(LotCompatibilityConfirmation value) {
    return new LotEvidenceDtos.CompatibilityConfirmationDto(
        value.getId(),
        value.getBatchIds(),
        value.getCustomerId(),
        value.getConditions(),
        value.getConfirmedBy(),
        value.getConfirmedAt(),
        value.getRevokedAt());
  }
}
