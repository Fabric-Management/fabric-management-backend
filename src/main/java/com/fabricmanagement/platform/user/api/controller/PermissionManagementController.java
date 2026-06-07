package com.fabricmanagement.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.user.app.PermissionManagementService;
import com.fabricmanagement.platform.user.dto.CreatePermissionOverrideRequest;
import com.fabricmanagement.platform.user.dto.CreatePermissionTemplateRequest;
import com.fabricmanagement.platform.user.dto.PermissionOverrideDto;
import com.fabricmanagement.platform.user.dto.PermissionTemplateDto;
import com.fabricmanagement.platform.user.dto.UpdatePermissionTemplateRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/common/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'PLATFORM_ADMIN')")
public class PermissionManagementController {

  private final PermissionManagementService permissionManagementService;

  @GetMapping("/templates")
  public ResponseEntity<ApiResponse<List<PermissionTemplateDto>>> getTemplates(
      @RequestParam(required = false) String roleCode,
      @RequestParam(required = false) String departmentCode) {

    UUID tenantId = TenantContext.requireTenantId();
    List<PermissionTemplateDto> templates =
        permissionManagementService.getTemplates(tenantId, roleCode, departmentCode);
    return ResponseEntity.ok(ApiResponse.success(templates));
  }

  @PostMapping("/templates")
  public ResponseEntity<ApiResponse<PermissionTemplateDto>> createTemplate(
      @Valid @RequestBody CreatePermissionTemplateRequest request) {

    UUID tenantId = TenantContext.requireTenantId();
    PermissionTemplateDto template = permissionManagementService.createTemplate(tenantId, request);
    return ResponseEntity.ok(ApiResponse.success(template));
  }

  @PutMapping("/templates/{id}")
  public ResponseEntity<ApiResponse<PermissionTemplateDto>> updateTemplate(
      @PathVariable UUID id, @Valid @RequestBody UpdatePermissionTemplateRequest request) {

    UUID tenantId = TenantContext.requireTenantId();
    PermissionTemplateDto template =
        permissionManagementService.updateTemplate(tenantId, id, request);
    return ResponseEntity.ok(ApiResponse.success(template));
  }

  @DeleteMapping("/templates/{id}")
  public ResponseEntity<ApiResponse<Void>> deleteTemplate(@PathVariable UUID id) {
    UUID tenantId = TenantContext.requireTenantId();
    permissionManagementService.deleteTemplate(tenantId, id);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @GetMapping("/overrides")
  public ResponseEntity<ApiResponse<List<PermissionOverrideDto>>> getOverrides(
      @RequestParam(required = false) UUID userId) {

    UUID tenantId = TenantContext.requireTenantId();
    List<PermissionOverrideDto> overrides =
        permissionManagementService.getActiveOverrides(tenantId, userId);
    return ResponseEntity.ok(ApiResponse.success(overrides));
  }

  @PostMapping("/overrides")
  public ResponseEntity<ApiResponse<PermissionOverrideDto>> createOverride(
      @Valid @RequestBody CreatePermissionOverrideRequest request) {

    UUID tenantId = TenantContext.requireTenantId();
    UUID grantedBy = TenantContext.getCurrentUserId();
    PermissionOverrideDto override =
        permissionManagementService.createOverride(tenantId, grantedBy, request);
    return ResponseEntity.ok(ApiResponse.success(override));
  }

  @DeleteMapping("/overrides/{id}")
  public ResponseEntity<ApiResponse<Void>> deleteOverride(@PathVariable UUID id) {
    UUID tenantId = TenantContext.requireTenantId();
    permissionManagementService.deleteOverride(tenantId, id);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @GetMapping("/simulate/{userId}")
  public ResponseEntity<ApiResponse<Map<String, Map<String, String>>>> simulateEvaluator(
      @PathVariable UUID userId) {

    UUID tenantId = TenantContext.requireTenantId();
    Map<String, Map<String, String>> result =
        permissionManagementService.simulateEvaluator(tenantId, userId);
    return ResponseEntity.ok(ApiResponse.success(result));
  }
}
