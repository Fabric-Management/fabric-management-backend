package com.fabricmanagement.platform.tenant.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.tenant.app.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin API for cross-tenant operations. */
@RestController
@RequestMapping("/api/admin/tenant")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tenant Admin", description = "Tenant yönetim işlemleri (Sadece PLATFORM_ADMIN)")
public class TenantAdminController {

  private final TenantService tenantService;

  @PostMapping("/sync-settings")
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  @Operation(
      summary =
          "Tüm tenant ayarlarını (timezone vb) dinleyicilere (i18n modülü) senkronize et (One-off migration)")
  public ResponseEntity<ApiResponse<Integer>> syncAllSettings() {
    log.info("Manual sync of all tenant settings initialized by admin.");
    int count = tenantService.syncAllTenantSettings();
    return ResponseEntity.ok(
        ApiResponse.success(count, "Synced settings for " + count + " tenants"));
  }
}
