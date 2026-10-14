package com.fabricmanagement.platform.tenant.domain;

import java.util.UUID;

/**
 * A tenant's stored settings could not be read or parsed. Distinct from "never set", which reads as
 * the defaults: a rule that depends on a setting must not run on defaults when the setting may well
 * be something else (Fatih, 2026-10-14).
 */
public class TenantSettingsUnreadableException extends RuntimeException {

  private final UUID tenantId;

  public TenantSettingsUnreadableException(UUID tenantId, Throwable cause) {
    super("Settings of tenant " + tenantId + " cannot be read: " + cause.getMessage(), cause);
    this.tenantId = tenantId;
  }

  public UUID tenantId() {
    return tenantId;
  }
}
