package com.fabricmanagement.platform.organization.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.organization.app.DepartmentService;
import com.fabricmanagement.platform.organization.dto.DepartmentDto;
import com.fabricmanagement.platform.organization.mapper.DepartmentMapper;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/departments")
@RequiredArgsConstructor
@Slf4j
public class DepartmentController {

  private final DepartmentService departmentService;
  private final DepartmentMapper departmentMapper;

  /**
   * Get all departments for current tenant.
   *
   * <p>Returns active departments only, ordered by department name.
   *
   * <p><b>Hybrid Model:</b> Returns tenant-level departments only. Platform-level departments are
   * reference catalog and are copied to tenants during seeding. Tenant-specific departments are
   * what users interact with.
   */
  @GetMapping
  public ResponseEntity<ApiResponse<List<DepartmentDto>>> getAllDepartments() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting all departments: tenantId={}", tenantId);

    // Tenant-level departments only (platform-level departments are reference, not shown)
    List<DepartmentDto> departments =
        departmentService.getActiveDepartmentsByTenant(tenantId).stream()
            .map(departmentMapper::toDto)
            .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(departments));
  }

  /** Get all departments for a specific organization. */
  @GetMapping("/organization/{organizationId}")
  public ResponseEntity<ApiResponse<List<DepartmentDto>>> getDepartmentsByOrganization(
      @PathVariable UUID organizationId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug(
        "Getting departments by organization: tenantId={}, organizationId={}",
        tenantId,
        organizationId);

    List<DepartmentDto> departments =
        departmentService.getActiveDepartmentsByOrganization(tenantId, organizationId).stream()
            .map(departmentMapper::toDto)
            .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(departments));
  }

  /** Get department by ID. */
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<DepartmentDto>> getDepartment(@PathVariable UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting department: tenantId={}, id={}", tenantId, id);

    DepartmentDto department =
        departmentService
            .getDepartmentByTenantAndId(tenantId, id)
            .map(departmentMapper::toDto)
            .orElseThrow(() -> new IllegalArgumentException("Department not found"));

    return ResponseEntity.ok(ApiResponse.success(department));
  }
}
