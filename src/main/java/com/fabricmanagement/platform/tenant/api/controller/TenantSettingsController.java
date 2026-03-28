package com.fabricmanagement.platform.tenant.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.tenant.app.TenantService;
import com.fabricmanagement.platform.tenant.domain.TenantSettings;
import com.fabricmanagement.platform.tenant.dto.TenantSettingsDto;
import com.fabricmanagement.platform.tenant.dto.UpdateTenantSettingsRequest;
import com.fabricmanagement.platform.tenant.mapper.TenantSettingsMapper;
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
  private final TenantSettingsMapper tenantSettingsMapper;

  /**
   * Get settings for the current tenant.
   *
   * @return TenantSettings
   */
  @GetMapping
  public ResponseEntity<ApiResponse<TenantSettingsDto>> getSettings() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting settings for tenant: id={}", tenantId);

    TenantSettings settings = tenantService.getSettings(tenantId);
    TenantSettingsDto dto = tenantSettingsMapper.toDto(settings);

    return ResponseEntity.ok(ApiResponse.success(dto));
  }

  /**
   * Update settings for the current tenant.
   *
   * @param settings New settings
   * @return Updated TenantSettings
   */
  @PutMapping
  public ResponseEntity<ApiResponse<TenantSettingsDto>> updateSettings(
      @Valid @RequestBody UpdateTenantSettingsRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Updating settings for tenant: id={}", tenantId);

    TenantSettings settings = tenantSettingsMapper.toEntity(request);
    TenantSettings updated = tenantService.updateSettings(tenantId, settings);
    TenantSettingsDto dto = tenantSettingsMapper.toDto(updated);

    return ResponseEntity.ok(ApiResponse.success(dto, "Settings updated successfully"));
  }
}
