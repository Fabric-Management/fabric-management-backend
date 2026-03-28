package com.fabricmanagement.platform.organization.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationCertification;
import com.fabricmanagement.platform.organization.dto.AddOrganizationCertificationRequest;
import com.fabricmanagement.platform.organization.dto.OrganizationCertificationDto;
import com.fabricmanagement.platform.organization.dto.UpdateOrganizationCertificationRequest;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationCertificationRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
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
public class OrganizationCertificationService {

  private final OrganizationCertificationRepository certificationRepository;
  private final OrganizationRepository organizationRepository;
  private final FiberCertificationRepository fiberCertificationRepository;

  @Transactional(readOnly = true)
  public List<OrganizationCertificationDto> findByOrganizationId(UUID organizationId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    Organization org =
        organizationRepository
            .findByTenantIdAndId(tenantId, organizationId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Organization not found",
                        "ORG_NOT_FOUND",
                        404,
                        new Object[] {organizationId}));

    return certificationRepository.findByOrganizationIdAndIsActiveTrue(org.getId()).stream()
        .map(OrganizationCertificationDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public OrganizationCertificationDto findById(UUID organizationId, UUID certificationId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    Organization org =
        organizationRepository
            .findByTenantIdAndId(tenantId, organizationId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Organization not found",
                        "ORG_NOT_FOUND",
                        404,
                        new Object[] {organizationId}));

    return certificationRepository
        .findById(certificationId)
        .filter(c -> c.getOrganization().getId().equals(org.getId()))
        .filter(c -> tenantId.equals(c.getTenantId()))
        .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
        .map(OrganizationCertificationDto::from)
        .orElseThrow(
            () ->
                new PlatformDomainException(
                    "Certification not found for organization",
                    "ORG_CERTIFICATION_NOT_FOUND",
                    404,
                    new Object[] {certificationId, organizationId}));
  }

  @Transactional
  public OrganizationCertificationDto add(
      UUID organizationId, AddOrganizationCertificationRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    Organization org =
        organizationRepository
            .findByTenantIdAndId(tenantId, organizationId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Organization not found",
                        "ORG_NOT_FOUND",
                        404,
                        new Object[] {organizationId}));

    FiberCertification certification =
        fiberCertificationRepository
            .findById(request.getCertificationId())
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Certification not found",
                        "ORG_CERTIFICATION_NOT_FOUND",
                        404,
                        new Object[] {request.getCertificationId()}));

    OrganizationCertification entity =
        OrganizationCertification.builder()
            .organization(org)
            .certification(certification)
            .licenseNo(request.getLicenseNo())
            .issuedAt(request.getIssuedAt())
            .validUntil(request.getValidUntil())
            .documentRef(request.getDocumentRef())
            .build();

    OrganizationCertification saved = certificationRepository.save(entity);
    log.info(
        "Added certification {} to organization {}: {}",
        certification.getCertificationCode(),
        org.getUid(),
        saved.getId());

    return OrganizationCertificationDto.from(saved);
  }

  @Transactional
  public OrganizationCertificationDto update(
      UUID organizationId, UUID certificationId, UpdateOrganizationCertificationRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    Organization org =
        organizationRepository
            .findByTenantIdAndId(tenantId, organizationId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Organization not found",
                        "ORG_NOT_FOUND",
                        404,
                        new Object[] {organizationId}));

    OrganizationCertification entity =
        certificationRepository
            .findById(certificationId)
            .filter(c -> c.getOrganization().getId().equals(org.getId()))
            .filter(c -> tenantId.equals(c.getTenantId()))
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Certification not found for organization",
                        "ORG_CERTIFICATION_NOT_FOUND",
                        404,
                        new Object[] {certificationId, organizationId}));

    if (request.getVersion() != null && !request.getVersion().equals(entity.getVersion())) {
      throw new PlatformDomainException(
          "Version conflict - record was modified by another user",
          "ORG_CERTIFICATE_VERSION_CONFLICT",
          409);
    }

    if (request.getCertificationId() != null) {
      FiberCertification certification =
          fiberCertificationRepository
              .findById(request.getCertificationId())
              .orElseThrow(
                  () ->
                      new PlatformDomainException(
                          "Certification not found",
                          "ORG_CERTIFICATION_NOT_FOUND",
                          404,
                          new Object[] {request.getCertificationId()}));
      entity.setCertification(certification);
    }
    if (request.getLicenseNo() != null) entity.setLicenseNo(request.getLicenseNo());
    if (request.getIssuedAt() != null) entity.setIssuedAt(request.getIssuedAt());
    if (request.getValidUntil() != null) entity.setValidUntil(request.getValidUntil());
    if (request.getDocumentRef() != null) entity.setDocumentRef(request.getDocumentRef());

    OrganizationCertification saved = certificationRepository.save(entity);
    log.info("Updated organization certification: org={}, cert={}", org.getUid(), certificationId);

    return OrganizationCertificationDto.from(saved);
  }

  @Transactional
  public void delete(UUID organizationId, UUID certificationId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    Organization org =
        organizationRepository
            .findByTenantIdAndId(tenantId, organizationId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Organization not found",
                        "ORG_NOT_FOUND",
                        404,
                        new Object[] {organizationId}));

    OrganizationCertification entity =
        certificationRepository
            .findById(certificationId)
            .filter(c -> c.getOrganization().getId().equals(org.getId()))
            .filter(c -> tenantId.equals(c.getTenantId()))
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Certification not found for organization",
                        "ORG_CERTIFICATION_NOT_FOUND",
                        404,
                        new Object[] {certificationId, organizationId}));

    entity.delete();
    certificationRepository.save(entity);
    log.info("Deleted organization certification: org={}, cert={}", org.getUid(), certificationId);
  }
}
