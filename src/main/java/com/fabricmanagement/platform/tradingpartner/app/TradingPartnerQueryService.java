package com.fabricmanagement.platform.tradingpartner.app;

import com.fabricmanagement.platform.tradingpartner.domain.PartnerStatus;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only bounded-context contract for consumers that must classify a trading partner. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradingPartnerQueryService {

  private final TradingPartnerRepository tradingPartnerRepository;

  public Optional<PartnerClassification> findClassification(UUID tenantId, UUID partnerId) {
    return tradingPartnerRepository
        .findByTenantIdAndId(tenantId, partnerId)
        .map(
            partner ->
                new PartnerClassification(
                    partner.getPartnerType(),
                    partner.getStatus(),
                    Boolean.TRUE.equals(partner.getIsActive())));
  }

  public record PartnerClassification(PartnerType type, PartnerStatus status, boolean active) {}
}
