package com.fabricmanagement.platform.admin.dto;

/**
 * What a tenant is to the platform (PLATFORM-BOUNDARY-1). Decided by the backend so the platform
 * screen never guesses from names or slugs.
 */
public enum PlatformTenantKind {
  /** Longward, the owner of FabricOS: platform staff live here. */
  PLATFORM_OWNER,
  /** The golden template: roles, permissions and catalogues copied into every new company. */
  ONBOARDING_TEMPLATE,
  /** The development sample company (Nexus Fabrics); it never exists in a live installation. */
  SAMPLE_COMPANY,
  /** A copy made by the retired anonymous Playground entry; removed when it expires. */
  PLAYGROUND_COPY,
  /** A company that registered or was onboarded, in demo mode or live. */
  CUSTOMER;

  /** The section of the platform screen the tenant belongs to. */
  public PlatformTenantGroup group() {
    return switch (this) {
      case PLATFORM_OWNER, ONBOARDING_TEMPLATE, SAMPLE_COMPANY -> PlatformTenantGroup.PLATFORM;
      case PLAYGROUND_COPY -> PlatformTenantGroup.PLAYGROUND;
      case CUSTOMER -> PlatformTenantGroup.CUSTOMER;
    };
  }
}
