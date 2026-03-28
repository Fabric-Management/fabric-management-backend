package com.fabricmanagement.platform.user.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.user.api.facade.RoleFacade;
import com.fabricmanagement.platform.user.dto.CreateRoleRequest;
import com.fabricmanagement.platform.user.dto.RoleDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/roles")
@RequiredArgsConstructor
@Slf4j
public class RoleController {

  private final RoleFacade roleFacade;

  /**
   * Get all roles for current tenant.
   *
   * <p><b>Cached:</b> 5 minutes (tenant-scoped cache key)
   */
  @GetMapping
  @Cacheable(
      value = "roles",
      key =
          "T(com.fabricmanagement.common.infrastructure.persistence.TenantContext).getCurrentTenantId()")
  public ResponseEntity<ApiResponse<List<RoleDto>>> getAllRoles() {
    log.debug("Getting all roles");

    List<RoleDto> roles = roleFacade.findAll();

    return ResponseEntity.ok(ApiResponse.success(roles));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<RoleDto>> getRole(@PathVariable UUID id) {
    log.debug("Getting role: id={}", id);

    RoleDto role =
        roleFacade.findById(id).orElseThrow(() -> new IllegalArgumentException("Role not found"));

    return ResponseEntity.ok(ApiResponse.success(role));
  }

  @GetMapping("/code/{code}")
  public ResponseEntity<ApiResponse<RoleDto>> getRoleByCode(@PathVariable String code) {
    log.debug("Getting role by code: code={}", code);

    RoleDto role =
        roleFacade
            .findByCode(code)
            .orElseThrow(() -> new IllegalArgumentException("Role not found"));

    return ResponseEntity.ok(ApiResponse.success(role));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<RoleDto>> createRole(
      @Valid @RequestBody CreateRoleRequest request) {
    log.info(
        "Creating role: roleName={}, roleCode={}", request.getRoleName(), request.getRoleCode());

    RoleDto created =
        roleFacade.create(request.getRoleName(), request.getRoleCode(), request.getDescription());

    return ResponseEntity.ok(ApiResponse.success(created, "Role created successfully"));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<RoleDto>> updateRole(
      @PathVariable UUID id, @Valid @RequestBody CreateRoleRequest request) {
    log.info("Updating role: id={}", id);

    RoleDto updated = roleFacade.update(id, request.getRoleName(), request.getDescription());

    return ResponseEntity.ok(ApiResponse.success(updated, "Role updated successfully"));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> deactivateRole(@PathVariable UUID id) {
    log.info("Deactivating role: id={}", id);

    roleFacade.deactivate(id);

    return ResponseEntity.ok(ApiResponse.success(null, "Role deactivated successfully"));
  }
}
