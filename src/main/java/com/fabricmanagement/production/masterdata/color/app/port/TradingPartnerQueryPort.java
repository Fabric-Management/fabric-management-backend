package com.fabricmanagement.production.masterdata.color.app.port;

import com.fabricmanagement.production.masterdata.color.domain.PartnerRole;
import java.util.UUID;

/** Production-owned boundary for role-compatible, active trading-partner validation. */
public interface TradingPartnerQueryPort {

  boolean isActiveAndCompatible(UUID tenantId, UUID partnerId, PartnerRole role);
}
