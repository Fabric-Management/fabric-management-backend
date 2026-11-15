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

  /** Minimal tenant-scoped name for already authorised document/work composition; no contacts. */
  public Optional<String> findDisplayName(UUID tenantId, UUID partnerId) {
    return tradingPartnerRepository
        .findByTenantIdAndId(tenantId, partnerId)
        .map(com.fabricmanagement.platform.tradingpartner.domain.TradingPartner::getDisplayName);
  }

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

  /**
   * The customer's stored default own-cheque share (PAYMENT-PLAN-7 R2). Empty when it was never set
   * or the partner is not this tenant's; the reader resolves both as 100.
   */
  public Optional<Integer> findDefaultOwnChequePercent(UUID tenantId, UUID partnerId) {
    return tradingPartnerRepository
        .findByTenantIdAndId(tenantId, partnerId)
        .map(
            com.fabricmanagement.platform.tradingpartner.domain.TradingPartner
                ::getDefaultOwnChequePercent);
  }

  public record PartnerClassification(PartnerType type, PartnerStatus status, boolean active) {}
}
