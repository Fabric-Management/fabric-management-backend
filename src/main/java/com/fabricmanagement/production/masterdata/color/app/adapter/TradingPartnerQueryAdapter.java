package com.fabricmanagement.production.masterdata.color.app.adapter;

import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerQueryService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerStatus;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.production.masterdata.color.app.port.TradingPartnerQueryPort;
import com.fabricmanagement.production.masterdata.color.domain.PartnerRole;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Anti-corruption adapter translating platform partner types into Production's color roles. */
@Component
@RequiredArgsConstructor
public class TradingPartnerQueryAdapter implements TradingPartnerQueryPort {

  private static final Set<PartnerType> CUSTOMER_TYPES =
      Set.of(PartnerType.CUSTOMER, PartnerType.BOTH);
  private static final Set<PartnerType> SUPPLIER_TYPES =
      Set.of(PartnerType.SUPPLIER, PartnerType.BOTH, PartnerType.SUBCONTRACTOR);

  private final TradingPartnerQueryService tradingPartnerQueryService;

  @Override
  public boolean isActiveAndCompatible(UUID tenantId, UUID partnerId, PartnerRole role) {
    return tradingPartnerQueryService
        .findClassification(tenantId, partnerId)
        .filter(TradingPartnerQueryService.PartnerClassification::active)
        .filter(classification -> classification.status() == PartnerStatus.ACTIVE)
        .map(classification -> acceptedTypes(role).contains(classification.type()))
        .orElse(false);
  }

  private Set<PartnerType> acceptedTypes(PartnerRole role) {
    return switch (role) {
      case CUSTOMER -> CUSTOMER_TYPES;
      case SUPPLIER -> SUPPLIER_TYPES;
    };
  }
}
