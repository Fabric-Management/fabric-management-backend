package com.fabricmanagement.common.platform.tenant.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.tenant.app.TenantService;
import com.fabricmanagement.common.platform.tenant.domain.TenantSettings;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for Tenant Settings management.
 *
 * <p>Allows authenticated tenant users to read and update their tenant-wide settings such as
 * localization (timezone, locale, currency), security (MFA, session timeout, IP restrictions), and
 * branding (logo, primary color).
 */
@RestController
@RequestMapping("/api/common/tenant/settings")
@RequiredArgsConstructor
@Slf4j
public class TenantSettingsController {

  private final TenantService tenantService;

  /**
   * Get settings for the current tenant.
   *
   * @return TenantSettings
   */
  @GetMapping
  public ResponseEntity<ApiResponse<TenantSettings>> getSettings() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting settings for tenant: id={}", tenantId);

    TenantSettings settings = tenantService.getSettings(tenantId);

    return ResponseEntity.ok(ApiResponse.success(settings));
  }

  /**
   * Update settings for the current tenant.
   *
   * @param settings New settings
   * @return Updated TenantSettings
   */
  @PutMapping
  public ResponseEntity<ApiResponse<TenantSettings>> updateSettings(
      @Valid @RequestBody TenantSettings settings) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Updating settings for tenant: id={}", tenantId);

    TenantSettings updated = tenantService.updateSettings(tenantId, settings);

    return ResponseEntity.ok(ApiResponse.success(updated, "Settings updated successfully"));
  }
}
