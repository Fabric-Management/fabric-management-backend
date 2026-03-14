package com.fabricmanagement.production.execution.batch.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.organization.domain.OrganizationCertification;
import com.fabricmanagement.common.platform.organization.infra.repository.OrganizationCertificationRepository;
import com.fabricmanagement.common.platform.tradingpartner.domain.TradingPartnerCertification;
import com.fabricmanagement.common.platform.tradingpartner.infra.repository.TradingPartnerCertificationRepository;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchCertification;
import com.fabricmanagement.production.execution.batch.domain.BatchCertificationScope;
import com.fabricmanagement.production.execution.batch.dto.AddBatchCertificationRequest;
import com.fabricmanagement.production.execution.batch.dto.BatchCertificationAutoFillResponse;
import com.fabricmanagement.production.execution.batch.dto.BatchCertificationDto;
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

    return certificationRepository.findByBatch_IdAndIsActiveTrue(batch.getId()).stream()
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
              .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
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
              .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
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
          .filter(c -> tenantId.equals(c.getTenantId()))
          .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
          .map(
              pc ->
                  BatchCertificationAutoFillResponse.builder()
                      .certificationId(pc.getCertification().getId())
                      .certificationCode(pc.getCertification().getCertificationCode())
                      .certificationName(pc.getCertification().getCertificationName())
                      .scope(BatchCertificationScope.SUPPLIER)
                      .partnerCertificationId(pc.getId())
                      .certNumber(pc.getLicenseNo())
                      .validFrom(pc.getIssuedAt())
                      .validUntil(pc.getValidUntil())
                      .certifyingBodyRef(pc.getCertification().getCertifyingBody())
                      .documentUrl(null)
                      .remarks(
                          pc.getDocumentRef() != null ? "Doc ref: " + pc.getDocumentRef() : null)
                      .build())
          .orElse(null);
    }

    if (scope == BatchCertificationScope.FACILITY && orgCertificationId != null) {
      return orgCertificationRepository
          .findById(orgCertificationId)
          .filter(c -> tenantId.equals(c.getTenantId()))
          .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
          .map(
              oc ->
                  BatchCertificationAutoFillResponse.builder()
                      .certificationId(oc.getCertification().getId())
                      .certificationCode(oc.getCertification().getCertificationCode())
                      .certificationName(oc.getCertification().getCertificationName())
                      .scope(BatchCertificationScope.FACILITY)
                      .orgCertificationId(oc.getId())
                      .certNumber(oc.getLicenseNo())
                      .validFrom(oc.getIssuedAt())
                      .validUntil(oc.getValidUntil())
                      .certifyingBodyRef(oc.getCertification().getCertifyingBody())
                      .documentUrl(null)
                      .remarks(
                          oc.getDocumentRef() != null ? "Doc ref: " + oc.getDocumentRef() : null)
                      .build())
          .orElse(null);
    }

    return null;
  }

  @Transactional
  public void delete(UUID batchId, UUID certificationId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    Batch batch =
        batchRepository
            .findByIdAndTenantId(batchId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

    BatchCertification entity =
        certificationRepository
            .findById(certificationId)
            .filter(c -> c.getBatch().getId().equals(batch.getId()))
            .filter(c -> tenantId.equals(c.getTenantId()))
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Batch certification not found: "
                            + certificationId
                            + " for batch "
                            + batchId));

    entity.delete();
    certificationRepository.save(entity);
    log.info(
        "Deleted batch certification: batch={}, cert={}", batch.getBatchCode(), certificationId);
  }
}
