package com.fabricmanagement.product.color.app.port;

import com.fabricmanagement.product.color.domain.PartnerRole;
import java.util.UUID;

/** Production-owned boundary for role-compatible, active trading-partner validation. */
public interface TradingPartnerQueryPort {

  boolean isActiveAndCompatible(UUID tenantId, UUID partnerId, PartnerRole role);
}
