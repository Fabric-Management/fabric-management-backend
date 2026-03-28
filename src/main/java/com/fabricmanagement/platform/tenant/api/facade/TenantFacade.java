package com.fabricmanagement.platform.tenant.api.facade;

import com.fabricmanagement.platform.tenant.app.TenantService;
import com.fabricmanagement.platform.tenant.dto.CreateTenantRequest;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Facade for cross-module Tenant access.
 *
 * <p>Provides a stable API for other modules to interact with Tenant without depending on internal
 * implementation details.
 *
 * <p>Used by:
 *
 * <ul>
 *   <li>Auth module (onboarding, JWT context)
 *   <li>Company/Organization module
 *   <li>Billing module
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class TenantFacade {

  private final TenantService tenantService;

  /**
   * Create a new tenant during onboarding.
   *
   * @param request Creation request
   * @return Created tenant
   */
  public TenantDto createTenant(CreateTenantRequest request) {
    return tenantService.createTenant(request);
  }

  /**
   * Find tenant by ID.
   *
   * @param tenantId Tenant UUID
   * @return Tenant if found
   */
  public Optional<TenantDto> findById(UUID tenantId) {
    return tenantService.findById(tenantId);
  }

  /**
   * Find active tenant by ID.
   *
   * @param tenantId Tenant UUID
   * @return Tenant if found and active
   */
  public Optional<TenantDto> findActiveById(UUID tenantId) {
    return tenantService.findActiveById(tenantId);
  }

  /**
   * Find tenant by UID.
   *
   * @param uid Human-readable UID
   * @return Tenant if found
   */
  public Optional<TenantDto> findByUid(String uid) {
    return tenantService.findByUid(uid);
  }

  /**
   * Check if tenant exists.
   *
   * @param tenantId Tenant UUID
   * @return true if tenant exists
   */
  public boolean exists(UUID tenantId) {
    return tenantService.exists(tenantId);
  }

  /**
   * Activate tenant subscription.
   *
   * @param tenantId Tenant UUID
   * @param plan Subscription plan
   * @return Updated tenant
   */
  public TenantDto activate(UUID tenantId, String plan) {
    return tenantService.activate(tenantId, plan);
  }

  /**
   * Suspend tenant.
   *
   * @param tenantId Tenant UUID
   * @param reason Suspension reason
   * @return Updated tenant
   */
  public TenantDto suspend(UUID tenantId, String reason) {
    return tenantService.suspend(tenantId, reason);
  }
}
