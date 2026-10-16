package com.fabricmanagement.inventory.lothealth.app.port;

import com.fabricmanagement.inventory.lothealth.domain.LotHealthSettings;
import java.util.UUID;

/** A tenant's lot-health thresholds; implemented where tenant settings live. */
public interface LotHealthSettingsPort {

  /**
   * Never null: a tenant without its own thresholds gets {@link LotHealthSettings#STANDARD}.
   *
   * @throws LotHealthSettingsUnavailableException when the tenant's settings cannot be read: the
   *     caller must not fall back to the standard, which may be looser than what the tenant set
   */
  LotHealthSettings forTenant(UUID tenantId);
}
