package com.fabricmanagement.platform.tradingpartner.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.common.dto.CertificationSummary;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerCertification;
import com.fabricmanagement.platform.tradingpartner.dto.AddTradingPartnerCertificationRequest;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerCertificationDto;
import com.fabricmanagement.platform.tradingpartner.dto.UpdateTradingPartnerCertificationRequest;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerCertificationRepository;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import com.fabricmanagement.production.masterdata.fiber.app.FiberCertificationQueryService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingPartnerCertificationService {

  private final TradingPartnerCertificationRepository certificationRepository;
  private final TradingPartnerRepository partnerRepository;
  private final FiberCertificationQueryService fiberCertificationQueryService;

  @Transactional(readOnly = true)
  public List<TradingPartnerCertificationDto> findByPartnerId(UUID partnerId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    TradingPartner partner =
        partnerRepository
            .findByTenantIdAndId(tenantId, partnerId)
            .orElseThrow(
                () -> new IllegalArgumentException("Trading partner not found: " + partnerId));

    List<TradingPartnerCertification> certs =
        certificationRepository.findByTradingPartnerIdAndIsActiveTrue(partner.getId());

    Set<UUID> certIds =
        certs.stream()
            .map(TradingPartnerCertification::getCertificationId)
            .collect(Collectors.toSet());

    var certMap =
        fiberCertificationQueryService.findAllActiveByIds(certIds).stream()
            .collect(Collectors.toMap(c -> c.getId(), Function.identity()));

    return certs.stream()
        .map(
            entity -> {
              var certDto = certMap.get(entity.getCertificationId());
              CertificationSummary summary =
                  certDto != null
                      ? new CertificationSummary(
                          certDto.getId(),
                          certDto.getCertificationCode(),
                          certDto.getCertificationName(),
                          certDto.getCertifyingBody())
                      : null;
              return TradingPartnerCertificationDto.from(entity, summary);
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public TradingPartnerCertificationDto findById(UUID partnerId, UUID certificationId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    TradingPartner partner =
        partnerRepository
            .findByTenantIdAndId(tenantId, partnerId)
            .orElseThrow(
                () -> new IllegalArgumentException("Trading partner not found: " + partnerId));

    return certificationRepository
        .findById(certificationId)
        .filter(c -> c.getTradingPartner().getId().equals(partner.getId()))
        .filter(c -> tenantId.equals(c.getTenantId()))
        .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
        .map(
            entity -> {
              var cert =
                  fiberCertificationQueryService.findActiveByIdOrThrow(entity.getCertificationId());
              CertificationSummary summary =
                  new CertificationSummary(
                      cert.getId(),
                      cert.getCertificationCode(),
                      cert.getCertificationName(),
                      cert.getCertifyingBody());
              return TradingPartnerCertificationDto.from(entity, summary);
            })
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Certification not found: " + certificationId + " for partner " + partnerId));
  }

  @Transactional
  public TradingPartnerCertificationDto add(
      UUID partnerId, AddTradingPartnerCertificationRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    TradingPartner partner =
        partnerRepository
            .findByTenantIdAndId(tenantId, partnerId)
            .orElseThrow(
                () -> new IllegalArgumentException("Trading partner not found: " + partnerId));

    fiberCertificationQueryService.findActiveByIdOrThrow(request.getCertificationId());

    TradingPartnerCertification entity =
        TradingPartnerCertification.builder()
            .tradingPartner(partner)
            .certificationId(request.getCertificationId())
            .licenseNo(request.getLicenseNo())
            .issuedAt(request.getIssuedAt())
            .validUntil(request.getValidUntil())
            .documentRef(request.getDocumentRef())
            .build();

    TradingPartnerCertification saved = certificationRepository.save(entity);
    log.info(
        "Added certification ID {} to partner {}: {}",
        request.getCertificationId(),
        partner.getUid(),
        saved.getId());

    var cert = fiberCertificationQueryService.findActiveByIdOrThrow(saved.getCertificationId());
    CertificationSummary summary =
        new CertificationSummary(
            cert.getId(),
            cert.getCertificationCode(),
            cert.getCertificationName(),
            cert.getCertifyingBody());

    return TradingPartnerCertificationDto.from(saved, summary);
  }

  @Transactional
  public TradingPartnerCertificationDto update(
      UUID partnerId, UUID certificationId, UpdateTradingPartnerCertificationRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    TradingPartner partner =
        partnerRepository
            .findByTenantIdAndId(tenantId, partnerId)
            .orElseThrow(
                () -> new IllegalArgumentException("Trading partner not found: " + partnerId));

    TradingPartnerCertification entity =
        certificationRepository
            .findById(certificationId)
            .filter(c -> c.getTradingPartner().getId().equals(partner.getId()))
            .filter(c -> tenantId.equals(c.getTenantId()))
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Certification not found: "
                            + certificationId
                            + " for partner "
                            + partnerId));

    if (request.getVersion() != null && !request.getVersion().equals(entity.getVersion())) {
      throw new IllegalStateException("Version conflict - record was modified by another user");
    }

    if (request.getCertificationId() != null) {
      fiberCertificationQueryService.findActiveByIdOrThrow(request.getCertificationId());
      entity.setCertificationId(request.getCertificationId());
    }
    if (request.getLicenseNo() != null) entity.setLicenseNo(request.getLicenseNo());
    if (request.getIssuedAt() != null) entity.setIssuedAt(request.getIssuedAt());
    if (request.getValidUntil() != null) entity.setValidUntil(request.getValidUntil());
    if (request.getDocumentRef() != null) entity.setDocumentRef(request.getDocumentRef());

    TradingPartnerCertification saved = certificationRepository.save(entity);
    log.info(
        "Updated partner certification: partner={}, cert={}", partner.getUid(), certificationId);

    var cert = fiberCertificationQueryService.findActiveByIdOrThrow(saved.getCertificationId());
    CertificationSummary summary =
        new CertificationSummary(
            cert.getId(),
            cert.getCertificationCode(),
            cert.getCertificationName(),
            cert.getCertifyingBody());

    return TradingPartnerCertificationDto.from(saved, summary);
  }

  @Transactional
  public void delete(UUID partnerId, UUID certificationId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    TradingPartner partner =
        partnerRepository
            .findByTenantIdAndId(tenantId, partnerId)
            .orElseThrow(
                () -> new IllegalArgumentException("Trading partner not found: " + partnerId));

    TradingPartnerCertification entity =
        certificationRepository
            .findById(certificationId)
            .filter(c -> c.getTradingPartner().getId().equals(partner.getId()))
            .filter(c -> tenantId.equals(c.getTenantId()))
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Certification not found: "
                            + certificationId
                            + " for partner "
                            + partnerId));

    entity.delete();
    certificationRepository.save(entity);
    log.info(
        "Deleted partner certification: partner={}, cert={}", partner.getUid(), certificationId);
  }
}
