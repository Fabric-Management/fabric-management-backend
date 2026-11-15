package com.fabricmanagement.platform.tradingpartner.app;

import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PartnerOrganizationQueryService {

  private final TradingPartnerRepository partners;

  @Transactional(readOnly = true)
  public Optional<PartnerType> activePartnerType(UUID tenantId, UUID organizationId) {
    var active = partners.findByTenantIdAndOrganizationIdAndIsActiveTrue(tenantId, organizationId);
    return active.size() == 1
        ? Optional.ofNullable(active.getFirst().getPartnerType())
        : Optional.empty();
  }
}
