package com.fabricmanagement.platform.admin.dto;

/** The sections of the platform tenant screen, in display order. */
public enum PlatformTenantGroup {
  /** Companies using FabricOS, in demo mode or live. */
  CUSTOMER,
  /** Copies left by the retired anonymous Playground entry. */
  PLAYGROUND,
  /** Longward's own tenant and the platform's working tenants. */
  PLATFORM
}
