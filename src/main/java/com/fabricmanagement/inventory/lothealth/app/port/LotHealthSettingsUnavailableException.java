package com.fabricmanagement.inventory.lothealth.app.port;

import java.util.UUID;

/** The tenant's lot-health thresholds exist but cannot be read now (Fatih, 2026-10-14). */
public class LotHealthSettingsUnavailableException extends RuntimeException {

  public LotHealthSettingsUnavailableException(UUID tenantId, Throwable cause) {
    super("Lot-health settings of tenant " + tenantId + " cannot be read", cause);
  }
}
