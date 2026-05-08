package com.fabricmanagement.platform.tenant.domain;

/**
 * Tenant type for the platform.
 *
 * <ul>
 *   <li>{@code REGULAR}: A standard tenant with full access and standard lifecycle.
 *   <li>{@code PLAYGROUND}: An ephemeral tenant created for simulation, subject to quotas and TTL.
 *   <li>{@code TEMPLATE}: A golden template tenant used exclusively as a source for cloning
 *       playgrounds.
 * </ul>
 */
public enum TenantType {
  REGULAR,
  PLAYGROUND,
  TEMPLATE;

  public boolean isPlayground() {
    return this == PLAYGROUND;
  }

  public boolean isTemplate() {
    return this == TEMPLATE;
  }

  public boolean isRegular() {
    return this == REGULAR;
  }
}
