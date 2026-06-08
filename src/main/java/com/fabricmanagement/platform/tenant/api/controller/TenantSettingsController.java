package com.fabricmanagement.platform.tenant.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.tenant.app.TenantService;
import com.fabricmanagement.platform.tenant.domain.TenantSettings;
import com.fabricmanagement.platform.tenant.dto.TenantSettingsDto;
import com.fabricmanagement.platform.tenant.dto.UpdateTenantSettingsRequest;
import com.fabricmanagement.platform.tenant.mapper.TenantSettingsMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/common/tenant/settings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tenant Settings", description = "Tenant Settings operations")
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
    log.debug("Getting settings for current tenant");

    TenantSettings settings = tenantService.getMySettings();
    TenantSettingsDto dto = tenantSettingsMapper.toDto(settings);

    return ResponseEntity.ok(ApiResponse.success(dto));
  }

  /**
   * Update settings for the current tenant.
   *
   * @param request New settings
   * @return Updated TenantSettings
   */
  @PutMapping
  public ResponseEntity<ApiResponse<TenantSettingsDto>> updateSettings(
      @Valid @RequestBody UpdateTenantSettingsRequest request) {
    log.info("Updating settings for current tenant");

    TenantSettings settings = tenantSettingsMapper.toEntity(request);
    TenantSettings updated = tenantService.updateMySettings(settings);
    TenantSettingsDto dto = tenantSettingsMapper.toDto(updated);

    return ResponseEntity.ok(ApiResponse.success(dto, "Settings updated successfully"));
  }
}
