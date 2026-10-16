package com.fabricmanagement.platform.tenant.api.controller;

import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.tenant.app.TenantService;
import com.fabricmanagement.platform.tenant.domain.TenantSettings;
import com.fabricmanagement.platform.tenant.dto.StockProposalSettingsDto;
import com.fabricmanagement.platform.tenant.dto.TenantSettingsDto;
import com.fabricmanagement.platform.tenant.dto.UpdateStockProposalSettingsRequest;
import com.fabricmanagement.platform.tenant.dto.UpdateTenantSettingsRequest;
import com.fabricmanagement.platform.tenant.mapper.TenantSettingsMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for Tenant Settings management.
 *
 * <p>Every authenticated tenant user reads the settings; only administrators, users who may manage
 * members, change them (Fatih, 2026-10-14). Settings cover localization (timezone, locale,
 * currency), security (MFA, session timeout, IP restrictions), branding (logo, primary color) and,
 * on their own endpoint, the stock proposal (tolerance defaults, lot-health and product-speed
 * thresholds).
 */
@RestController
@RequestMapping("/api/v1/common/tenant/settings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tenant Settings", description = "Tenant Settings operations")
public class TenantSettingsController {

  private final TenantService tenantService;
  private final TenantSettingsMapper tenantSettingsMapper;
  private final SpELPermissionEvaluator auth;

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
   * Update settings for the current tenant. Administrators only.
   *
   * @param request New settings
   * @return Updated TenantSettings
   */
  @PutMapping
  public ResponseEntity<ApiResponse<TenantSettingsDto>> updateSettings(
      @Valid @RequestBody UpdateTenantSettingsRequest request, Authentication authentication) {
    requireAdministrator(authentication);
    log.info("Updating settings for current tenant");

    TenantSettings settings = tenantSettingsMapper.toEntity(request);
    TenantSettings updated = tenantService.updateMySettings(settings);
    TenantSettingsDto dto = tenantSettingsMapper.toDto(updated);

    return ResponseEntity.ok(ApiResponse.success(dto, "Settings updated successfully"));
  }

  /**
   * The stock-proposal settings: every user reads them, a new order line starts with their
   * tolerance.
   */
  @Operation(summary = "Get the stock-proposal settings")
  @GetMapping("/stock-proposal")
  public ResponseEntity<ApiResponse<StockProposalSettingsDto>> getStockProposalSettings() {
    return ResponseEntity.ok(
        ApiResponse.success(
            StockProposalSettingsDto.from(tenantService.getMyStockProposalSettings())));
  }

  /** Replace the stock-proposal settings. Administrators only. */
  @Operation(summary = "Update the stock-proposal settings")
  @PutMapping("/stock-proposal")
  public ResponseEntity<ApiResponse<StockProposalSettingsDto>> updateStockProposalSettings(
      @Valid @RequestBody UpdateStockProposalSettingsRequest request,
      Authentication authentication) {
    requireAdministrator(authentication);
    log.info("Updating stock-proposal settings for current tenant");

    var updated = tenantService.updateMyStockProposalSettings(request.toSettings());

    return ResponseEntity.ok(
        ApiResponse.success(
            StockProposalSettingsDto.from(updated), "Settings updated successfully"));
  }

  /**
   * Tenant settings decide MFA, sessions, IP ranges and stock rules for everyone: only users who
   * may manage members change them (Fatih, 2026-10-14).
   */
  private void requireAdministrator(Authentication authentication) {
    if (!auth.can(authentication, "members", "manage")) {
      throw new AccessDeniedException("Only administrators can change tenant settings.");
    }
  }
}
