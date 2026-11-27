package com.fabricmanagement.production.quality.result.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerQueryService;
import com.fabricmanagement.production.quality.result.domain.FiberTestLaboratory;
import com.fabricmanagement.production.quality.result.dto.FiberTestLaboratoryDto;
import com.fabricmanagement.production.quality.result.infra.repository.FiberTestLaboratoryRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FiberTestLaboratoryService {

  private final FiberTestLaboratoryRepository repository;
  private final TradingPartnerQueryService partners;

  public Page<FiberTestLaboratoryDto> search(String search, Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();
    return partners
        .searchActiveNames(
            tenantId, repository.findActivePartnerIds(tenantId), search.strip(), pageable)
        .map(partner -> new FiberTestLaboratoryDto(partner.id(), partner.name()));
  }

  public FiberTestLaboratoryDto requireActive(UUID partnerId) {
    UUID tenantId = TenantContext.requireTenantId();
    if (!repository.existsByTenantIdAndTradingPartnerIdAndIsActiveTrue(tenantId, partnerId)) {
      throw new NotFoundException("External laboratory is unavailable");
    }
    return activePartner(tenantId, partnerId);
  }

  @Transactional
  public FiberTestLaboratoryDto register(UUID partnerId) {
    UUID tenantId = TenantContext.requireTenantId();
    FiberTestLaboratoryDto partner = activePartner(tenantId, partnerId);
    var registration = repository.findByTenantIdAndTradingPartnerId(tenantId, partnerId);
    if (registration.isEmpty()) {
      repository.save(new FiberTestLaboratory(partnerId));
    } else if (!Boolean.TRUE.equals(registration.get().getIsActive())) {
      registration.get().activate();
    }
    return partner;
  }

  @Transactional
  public void deactivate(UUID partnerId) {
    FiberTestLaboratory registration =
        repository
            .findByTenantIdAndTradingPartnerId(TenantContext.requireTenantId(), partnerId)
            .orElseThrow(() -> new NotFoundException("External laboratory is unavailable"));
    if (Boolean.TRUE.equals(registration.getIsActive())) {
      registration.delete();
    }
  }

  private FiberTestLaboratoryDto activePartner(UUID tenantId, UUID partnerId) {
    String name =
        partners
            .findActiveDisplayName(tenantId, partnerId)
            .orElseThrow(() -> new NotFoundException("External laboratory is unavailable"));
    return new FiberTestLaboratoryDto(partnerId, name);
  }
}
