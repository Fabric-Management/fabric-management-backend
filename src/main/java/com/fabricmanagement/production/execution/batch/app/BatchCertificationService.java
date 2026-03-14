package com.fabricmanagement.production.execution.batch.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.organization.domain.OrganizationCertification;
import com.fabricmanagement.common.platform.organization.infra.repository.OrganizationCertificationRepository;
import com.fabricmanagement.common.platform.tradingpartner.domain.TradingPartnerCertification;
import com.fabricmanagement.common.platform.tradingpartner.infra.repository.TradingPartnerCertificationRepository;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchCertification;
import com.fabricmanagement.production.execution.batch.domain.BatchCertificationChangeReason;
import com.fabricmanagement.production.execution.batch.domain.BatchCertificationScope;
import com.fabricmanagement.production.execution.batch.dto.AddBatchCertificationRequest;
import com.fabricmanagement.production.execution.batch.dto.BatchCertificationAutoFillResponse;
import com.fabricmanagement.production.execution.batch.dto.BatchCertificationDto;
import com.fabricmanagement.production.execution.batch.dto.UpdateBatchCertificationRequest;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchCertificationRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCertification;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberCertificationRepository;
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
    UUID tenantId = TenantContext.getCurrentTenantId();
    Batch batch =
        batchRepository
            .findByIdAndTenantId(batchId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

    return certificationRepository
        .findByBatch_IdAndIsActiveTrueWithAssociations(batch.getId())
        .stream()
        .map(BatchCertificationDto::from)
        .toList();
  }

  @Transactional
  public BatchCertificationDto add(UUID batchId, AddBatchCertificationRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    Batch batch =
        batchRepository
            .findByIdAndTenantId(batchId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

    FiberCertification certification =
        fiberCertificationRepository
            .findById(request.getCertificationId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Certification not found: " + request.getCertificationId()));

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
            .build();

    BatchCertification saved = certificationRepository.save(entity);
    log.info(
        "Added certification {} to batch {}: {}",
        certification.getCertificationCode(),
        batch.getBatchCode(),
        saved.getId());

    return BatchCertificationDto.from(saved);
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
          .filter(partnerCert -> tenantId.equals(partnerCert.getTenantId()))
          .filter(partnerCert -> Boolean.TRUE.equals(partnerCert.getIsActive()))
          .map(this::buildAutoFillFromPartnerCert)
          .orElse(null);
    }

    if (scope == BatchCertificationScope.FACILITY && orgCertificationId != null) {
      return orgCertificationRepository
          .findById(orgCertificationId)
          .filter(orgCert -> tenantId.equals(orgCert.getTenantId()))
          .filter(orgCert -> Boolean.TRUE.equals(orgCert.getIsActive()))
          .map(this::buildAutoFillFromOrgCert)
          .orElse(null);
    }

    return null;
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
        .remarks(
            orgCert.getDocumentRef() != null ? "Doc ref: " + orgCert.getDocumentRef() : null)
        .build();
  }

  private BatchCertification findBatchCertificationOrThrow(
      Batch batch, UUID certificationId, UUID tenantId, boolean requireActive) {
    var byBatchAndTenant =
        certificationRepository
            .findById(certificationId)
            .filter(batchCert -> batchCert.getBatch().getId().equals(batch.getId()))
            .filter(batchCert -> tenantId.equals(batchCert.getTenantId()));
    if (requireActive) {
      byBatchAndTenant =
          byBatchAndTenant.filter(batchCert -> Boolean.TRUE.equals(batchCert.getIsActive()));
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
  public BatchCertificationDto update(
      UUID batchId, UUID certificationId, UpdateBatchCertificationRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    Batch batch =
        batchRepository
            .findByIdAndTenantId(batchId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
    BatchCertification entity =
        findBatchCertificationOrThrow(batch, certificationId, tenantId, true);

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

    BatchCertification saved = certificationRepository.save(entity);
    log.info(
        "Updated batch certification: batch={}, cert={}, reason={}",
        batch.getBatchCode(),
        certificationId,
        request.getChangeReason());

    return BatchCertificationDto.from(saved);
  }

  @Transactional
  public void delete(UUID batchId, UUID certificationId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    Batch batch =
        batchRepository
            .findByIdAndTenantId(batchId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
    BatchCertification entity =
        findBatchCertificationOrThrow(batch, certificationId, tenantId, true);

    entity.delete();
    certificationRepository.save(entity);
    log.info(
        "Deleted batch certification: batch={}, cert={}", batch.getBatchCode(), certificationId);
  }
}
