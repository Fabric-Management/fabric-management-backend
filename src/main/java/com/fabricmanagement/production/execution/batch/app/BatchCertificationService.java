package com.fabricmanagement.production.execution.batch.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.common.platform.organization.domain.OrganizationCertification;
import com.fabricmanagement.common.platform.organization.infra.repository.OrganizationCertificationRepository;
import com.fabricmanagement.common.platform.tradingpartner.domain.TradingPartnerCertification;
import com.fabricmanagement.common.platform.tradingpartner.infra.repository.TradingPartnerCertificationRepository;
import com.fabricmanagement.production.common.exception.BatchCertificationOverlapException;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchCertification;
import com.fabricmanagement.production.execution.batch.domain.BatchCertificationChangeReason;
import com.fabricmanagement.production.execution.batch.domain.BatchCertificationScope;
import com.fabricmanagement.production.execution.batch.dto.AddBatchCertificationRequest;
import com.fabricmanagement.production.execution.batch.dto.BatchCertificationAutoFillResponse;
import com.fabricmanagement.production.execution.batch.dto.BatchCertificationDto;
import com.fabricmanagement.production.execution.batch.dto.BatchCertificationResult;
import com.fabricmanagement.production.execution.batch.dto.UpdateBatchCertificationRequest;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchCertificationRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCertification;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberCertificationRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchCertificationService {

  private final BatchCertificationRepository certificationRepository;
  private final BatchRepository batchRepository;
  private final FiberCertificationRepository fiberCertificationRepository;
  private final TradingPartnerCertificationRepository partnerCertificationRepository;
  private final OrganizationCertificationRepository orgCertificationRepository;

  @Transactional(readOnly = true)
  public List<BatchCertificationDto> findByBatchId(UUID batchId) {
    Batch batch = getBatchOrThrow(batchId);
    return certificationRepository
        .findByBatch_IdAndIsActiveTrueWithAssociations(batch.getId())
        .stream()
        .map(BatchCertificationDto::from)
        .toList();
  }

  /**
   * Returns true if the batch has at least one active GOTS certification that is still valid
   * (validUntil is null or validUntil >= today). Used for enforce-on-reserve checks; callers may
   * use this or query via repository to avoid circular dependency.
   */
  @Transactional(readOnly = true)
  public boolean hasValidGotsCertification(UUID batchId) {
    LocalDate today = LocalDate.now();
    return certificationRepository.findByBatch_IdAndIsActiveTrueWithAssociations(batchId).stream()
        .filter(cert -> BatchCertificationPredicates.isCertificationStillValid(cert, today))
        .anyMatch(BatchCertificationPredicates::isGotsCertification);
  }

  @Transactional
  public BatchCertificationResult add(UUID batchId, AddBatchCertificationRequest request) {
    Batch batch = getBatchOrThrow(batchId);

    FiberCertification certification =
        fiberCertificationRepository
            .findByIdAndIsActiveTrue(request.getCertificationId())
            .orElseThrow(() -> new NotFoundException("Certification type is not active"));

    TradingPartnerCertification partnerCert = null;
    if (request.getPartnerCertificationId() != null) {
      partnerCert =
          partnerCertificationRepository
              .findById(request.getPartnerCertificationId())
              .filter(pc -> Boolean.TRUE.equals(pc.getIsActive()))
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Partner certification not found: "
                              + request.getPartnerCertificationId()));
    }

    OrganizationCertification orgCert = null;
    if (request.getOrgCertificationId() != null) {
      orgCert =
          orgCertificationRepository
              .findById(request.getOrgCertificationId())
              .filter(oc -> Boolean.TRUE.equals(oc.getIsActive()))
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Organization certification not found: "
                              + request.getOrgCertificationId()));
    }

    BatchCertificationScope scope =
        request.getScope() != null ? request.getScope() : BatchCertificationScope.BATCH;

    UUID partnerId = partnerCert != null ? partnerCert.getId() : null;
    UUID orgId = orgCert != null ? orgCert.getId() : null;
    ensureNoOverlap(
        batch.getId(),
        certification.getId(),
        scope,
        partnerId,
        orgId,
        null,
        request.getValidFrom(),
        request.getValidUntil());

    List<String> warnings = collectReferenceValidityWarnings(scope, partnerCert, orgCert);

    BatchCertification entity =
        BatchCertification.builder()
            .batch(batch)
            .certification(certification)
            .scope(scope)
            .partnerCertification(partnerCert)
            .orgCertification(orgCert)
            .certNumber(request.getCertNumber())
            .validFrom(request.getValidFrom())
            .validUntil(request.getValidUntil())
            .certifyingBodyRef(request.getCertifyingBodyRef())
            .documentUrl(request.getDocumentUrl())
            .remarks(request.getRemarks())
            .changeReason(BatchCertificationChangeReason.INITIAL)
            .isAutoFilled(Boolean.TRUE.equals(request.getIsAutoFilled()))
            .build();

    BatchCertification saved = certificationRepository.save(entity);
    log.info(
        "Added certification {} to batch {}: {}",
        certification.getCertificationCode(),
        batch.getBatchCode(),
        saved.getId());

    return BatchCertificationResult.of(BatchCertificationDto.from(saved), warnings);
  }

  /**
   * Copies all active certifications from source batch to target batch. changeReason is INITIAL.
   * Fails with 409 if any certification would have an overlapping validity range on the target
   * (same batch, cert, scope, partner/facility).
   *
   * @param targetBatchId batch to copy into (must belong to current tenant)
   * @param sourceBatchId batch to copy from (must belong to current tenant)
   * @return list of copied certification DTOs
   * @throws IllegalArgumentException if target/source not found or source equals target
   * @throws BatchCertificationOverlapException if target has overlapping validity for any copied
   *     cert
   */
  @Transactional
  public List<BatchCertificationDto> copyFromBatch(UUID targetBatchId, UUID sourceBatchId) {
    Batch target = getBatchOrThrow(targetBatchId);
    Batch source = getBatchOrThrow(sourceBatchId);
    if (source.getId().equals(target.getId())) {
      throw new IllegalArgumentException("Source and target batch must be different.");
    }

    List<BatchCertification> sourceCerts =
        certificationRepository.findByBatch_IdAndIsActiveTrueWithAssociations(source.getId());
    if (sourceCerts.isEmpty()) {
      log.info("Copy from batch: source {} has no active certifications.", source.getBatchCode());
      return List.of();
    }

    List<String> conflicts = new ArrayList<>();
    for (BatchCertification sc : sourceCerts) {
      UUID certId = sc.getCertification().getId();
      UUID partnerId =
          sc.getPartnerCertification() != null ? sc.getPartnerCertification().getId() : null;
      UUID orgId = sc.getOrgCertification() != null ? sc.getOrgCertification().getId() : null;
      List<BatchCertification> existingOnTarget =
          certificationRepository.findActiveByBatchAndCertAndScopeAndPartnerAndOrgExcludingId(
              target.getId(), certId, sc.getScope(), partnerId, orgId, null);
      for (BatchCertification existing : existingOnTarget) {
        if (rangesOverlap(
            sc.getValidFrom(), sc.getValidUntil(),
            existing.getValidFrom(), existing.getValidUntil())) {
          conflicts.add(describeCertForConflict(sc));
          break;
        }
      }
    }
    if (!conflicts.isEmpty()) {
      throw new BatchCertificationOverlapException(
          "Target batch already has certification(s) with overlapping validity: "
              + String.join("; ", conflicts)
              + ". Remove or edit them before copying.");
    }

    List<BatchCertificationDto> added = new ArrayList<>();
    for (BatchCertification sc : sourceCerts) {
      AddBatchCertificationRequest req = buildCopyRequest(sc);
      BatchCertificationResult result = add(targetBatchId, req);
      added.add(result.getData());
    }
    log.info(
        "Copied {} certification(s) from batch {} to batch {}",
        added.size(),
        source.getBatchCode(),
        target.getBatchCode());
    return added;
  }

  private static String describeCertForConflict(BatchCertification bc) {
    String code =
        bc.getCertification() != null ? bc.getCertification().getCertificationCode() : "?";
    return code + " (" + bc.getScope() + ")";
  }

  private static AddBatchCertificationRequest buildCopyRequest(BatchCertification sc) {
    return AddBatchCertificationRequest.builder()
        .certificationId(sc.getCertification().getId())
        .scope(sc.getScope())
        .partnerCertificationId(
            sc.getPartnerCertification() != null ? sc.getPartnerCertification().getId() : null)
        .orgCertificationId(
            sc.getOrgCertification() != null ? sc.getOrgCertification().getId() : null)
        .certNumber(sc.getCertNumber())
        .validFrom(sc.getValidFrom())
        .validUntil(sc.getValidUntil())
        .certifyingBodyRef(sc.getCertifyingBodyRef())
        .documentUrl(sc.getDocumentUrl())
        .remarks(sc.getRemarks())
        .isAutoFilled(false)
        .build();
  }

  /**
   * Auto-fill batch certification form from partner (SUPPLIER) or organization (FACILITY)
   * certification.
   *
   * @param scope BATCH returns null; SUPPLIER requires partnerCertificationId; FACILITY requires
   *     orgCertificationId
   * @return Suggested values for the form, or null if scope is BATCH or IDs are missing
   */
  @Transactional(readOnly = true)
  public BatchCertificationAutoFillResponse autoFill(
      BatchCertificationScope scope, UUID partnerCertificationId, UUID orgCertificationId) {
    if (scope == null || scope == BatchCertificationScope.BATCH) {
      return null;
    }
    UUID tenantId = TenantContext.getCurrentTenantId();

    if (scope == BatchCertificationScope.SUPPLIER && partnerCertificationId != null) {
      return partnerCertificationRepository
          .findById(partnerCertificationId)
          .filter(partnerCert -> isPartnerCertEligibleForAutofill(partnerCert, tenantId))
          .map(this::buildAutoFillFromPartnerCert)
          .orElse(null);
    }

    if (scope == BatchCertificationScope.FACILITY && orgCertificationId != null) {
      return orgCertificationRepository
          .findById(orgCertificationId)
          .filter(orgCert -> isOrgCertEligibleForAutofill(orgCert, tenantId))
          .map(this::buildAutoFillFromOrgCert)
          .orElse(null);
    }

    return null;
  }

  /**
   * Collects warnings when scope is SUPPLIER/FACILITY and the referenced certification has expired
   * (GOTS compliance). Not a hard error — operation still succeeds.
   */
  private List<String> collectReferenceValidityWarnings(
      BatchCertificationScope scope,
      TradingPartnerCertification partnerCert,
      OrganizationCertification orgCert) {
    List<String> list = new ArrayList<>();
    if (scope == BatchCertificationScope.SUPPLIER
        && partnerCert != null
        && !partnerCert.isValid()) {
      list.add(
          "Referenced supplier certification has expired (validUntil: "
              + (partnerCert.getValidUntil() != null ? partnerCert.getValidUntil() : "n/a")
              + ").");
    }
    if (scope == BatchCertificationScope.FACILITY && orgCert != null && !orgCert.isValid()) {
      list.add(
          "Referenced facility certification has expired (validUntil: "
              + (orgCert.getValidUntil() != null ? orgCert.getValidUntil() : "n/a")
              + ").");
    }
    return list;
  }

  private static boolean isPartnerCertEligibleForAutofill(
      TradingPartnerCertification partnerCert, UUID tenantId) {
    return tenantId.equals(partnerCert.getTenantId())
        && Boolean.TRUE.equals(partnerCert.getIsActive());
  }

  private static boolean isOrgCertEligibleForAutofill(
      OrganizationCertification orgCert, UUID tenantId) {
    return tenantId.equals(orgCert.getTenantId()) && Boolean.TRUE.equals(orgCert.getIsActive());
  }

  private BatchCertificationAutoFillResponse buildAutoFillFromPartnerCert(
      TradingPartnerCertification partnerCert) {
    var cert = partnerCert.getCertification();
    return BatchCertificationAutoFillResponse.builder()
        .certificationId(cert.getId())
        .certificationCode(cert.getCertificationCode())
        .certificationName(cert.getCertificationName())
        .scope(BatchCertificationScope.SUPPLIER)
        .partnerCertificationId(partnerCert.getId())
        .certNumber(partnerCert.getLicenseNo())
        .validFrom(partnerCert.getIssuedAt())
        .validUntil(partnerCert.getValidUntil())
        .certifyingBodyRef(cert.getCertifyingBody())
        .documentUrl(null)
        .remarks(
            partnerCert.getDocumentRef() != null
                ? "Doc ref: " + partnerCert.getDocumentRef()
                : null)
        .build();
  }

  private BatchCertificationAutoFillResponse buildAutoFillFromOrgCert(
      OrganizationCertification orgCert) {
    var cert = orgCert.getCertification();
    return BatchCertificationAutoFillResponse.builder()
        .certificationId(cert.getId())
        .certificationCode(cert.getCertificationCode())
        .certificationName(cert.getCertificationName())
        .scope(BatchCertificationScope.FACILITY)
        .orgCertificationId(orgCert.getId())
        .certNumber(orgCert.getLicenseNo())
        .validFrom(orgCert.getIssuedAt())
        .validUntil(orgCert.getValidUntil())
        .certifyingBodyRef(cert.getCertifyingBody())
        .documentUrl(null)
        .remarks(orgCert.getDocumentRef() != null ? "Doc ref: " + orgCert.getDocumentRef() : null)
        .build();
  }

  /**
   * Ensures no active batch certification for the same (batch, cert, scope, partner, org) has a
   * validity period overlapping with the given range. Throws 409 if overlap found.
   *
   * @param excludeId when updating, the id of the record being updated; when adding, null
   */
  private void ensureNoOverlap(
      UUID batchId,
      UUID certId,
      BatchCertificationScope scope,
      UUID partnerId,
      UUID orgId,
      UUID excludeId,
      LocalDate newValidFrom,
      LocalDate newValidUntil) {
    List<BatchCertification> existing =
        certificationRepository.findActiveByBatchAndCertAndScopeAndPartnerAndOrgExcludingId(
            batchId, certId, scope, partnerId, orgId, excludeId);
    for (BatchCertification bc : existing) {
      if (rangesOverlap(newValidFrom, newValidUntil, bc.getValidFrom(), bc.getValidUntil())) {
        throw new BatchCertificationOverlapException(
            "Another active certification for this batch, certification type, and scope already"
                + " has a validity period that overlaps with the given dates. Use a non-overlapping"
                + " period or update the existing record.");
      }
    }
  }

  /**
   * Two validity ranges overlap if they share any day (inclusive: validUntil is end-of-period).
   * Null from/until treated as open-ended.
   */
  private static boolean rangesOverlap(
      LocalDate from1, LocalDate until1, LocalDate from2, LocalDate until2) {
    boolean range1BeforeEndOf2 = from1 == null || until2 == null || !from1.isAfter(until2);
    boolean range2BeforeEndOf1 = from2 == null || until1 == null || !from2.isAfter(until1);
    return range1BeforeEndOf2 && range2BeforeEndOf1;
  }

  /**
   * Loads batch by id with tenant check. Use in add/update/delete/findByBatchId for consistent
   * lookup and tenant isolation.
   */
  private Batch getBatchOrThrow(UUID batchId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return batchRepository
        .findByIdAndTenantId(batchId, tenantId)
        .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
  }

  private BatchCertification findBatchCertificationOrThrow(
      Batch batch, UUID certificationId, UUID tenantId, boolean requireActive) {
    var byBatchAndTenant =
        certificationRepository
            .findById(certificationId)
            .filter(bc -> matchesBatchAndTenant(bc, batch, tenantId));
    if (requireActive) {
      byBatchAndTenant = byBatchAndTenant.filter(this::isActive);
    }
    return byBatchAndTenant.orElseThrow(
        () ->
            new IllegalArgumentException(
                "Batch certification not found: "
                    + certificationId
                    + " for batch "
                    + batch.getId()
                    + (requireActive ? " (or record is deleted)" : "")));
  }

  private static boolean matchesBatchAndTenant(BatchCertification bc, Batch batch, UUID tenantId) {
    return bc.getBatch().getId().equals(batch.getId()) && tenantId.equals(bc.getTenantId());
  }

  private boolean isActive(BatchCertification bc) {
    return Boolean.TRUE.equals(bc.getIsActive());
  }

  /**
   * Updates an existing batch certification (partial update). Only non-null request fields are
   * applied. changeReason is always updated. Soft-deleted records cannot be updated.
   *
   * @param batchId batch ID (must belong to current tenant)
   * @param certificationId batch certification record ID
   * @param request update request; changeReason required, other fields optional
   * @return updated DTO
   * @throws IllegalArgumentException if batch not found, certification not found, or record is
   *     soft-deleted
   */
  @Transactional
  public BatchCertificationResult update(
      UUID batchId, UUID certificationId, UpdateBatchCertificationRequest request) {
    Batch batch = getBatchOrThrow(batchId);
    UUID tenantId = TenantContext.getCurrentTenantId();
    BatchCertification entity =
        findBatchCertificationOrThrow(batch, certificationId, tenantId, true);

    List<String> warnings =
        collectReferenceValidityWarnings(
            entity.getScope(), entity.getPartnerCertification(), entity.getOrgCertification());

    entity.setIsAutoFilled(false);
    entity.setChangeReason(request.getChangeReason());
    if (request.getCertNumber() != null) {
      entity.setCertNumber(request.getCertNumber());
    }
    if (request.getValidFrom() != null) {
      entity.setValidFrom(request.getValidFrom());
    }
    if (request.getValidUntil() != null) {
      entity.setValidUntil(request.getValidUntil());
    }
    if (request.getCertifyingBodyRef() != null) {
      entity.setCertifyingBodyRef(request.getCertifyingBodyRef());
    }
    if (request.getDocumentUrl() != null) {
      entity.setDocumentUrl(request.getDocumentUrl());
    }
    if (request.getRemarks() != null) {
      entity.setRemarks(request.getRemarks());
    }

    UUID partnerId =
        entity.getPartnerCertification() != null ? entity.getPartnerCertification().getId() : null;
    UUID orgId = entity.getOrgCertification() != null ? entity.getOrgCertification().getId() : null;
    ensureNoOverlap(
        batch.getId(),
        entity.getCertification().getId(),
        entity.getScope(),
        partnerId,
        orgId,
        entity.getId(),
        entity.getValidFrom(),
        entity.getValidUntil());

    BatchCertification saved = certificationRepository.save(entity);
    log.info(
        "Updated batch certification: batch={}, cert={}, reason={}",
        batch.getBatchCode(),
        certificationId,
        request.getChangeReason());

    return BatchCertificationResult.of(BatchCertificationDto.from(saved), warnings);
  }

  /**
   * Snapshots cert number and valid-until for all active batch certifications when the batch
   * completes (status DEPLETED). Called from {@link BatchCompletedEvent} listener. GOTS TC: freezes
   * certification data at completion time so historical data is preserved.
   */
  @Transactional
  public void snapshotCertificationsForCompletedBatch(UUID batchId) {
    getBatchOrThrow(batchId);

    List<BatchCertification> list = certificationRepository.findByBatch_IdAndIsActiveTrue(batchId);
    for (BatchCertification bc : list) {
      bc.setCertNumberAtCompletion(bc.getCertNumber());
      bc.setValidUntilAtCompletion(bc.getValidUntil());
    }
    if (!list.isEmpty()) {
      certificationRepository.saveAll(list);
      log.info(
          "Snapshot certifications for completed batch: batchId={}, count={}",
          batchId,
          list.size());
    }
  }

  @Transactional
  public void delete(UUID batchId, UUID certificationId) {
    Batch batch = getBatchOrThrow(batchId);
    UUID tenantId = TenantContext.getCurrentTenantId();
    BatchCertification entity =
        findBatchCertificationOrThrow(batch, certificationId, tenantId, true);

    entity.delete();
    certificationRepository.save(entity);
    log.info(
        "Deleted batch certification: batch={}, cert={}", batch.getBatchCode(), certificationId);
  }
}
