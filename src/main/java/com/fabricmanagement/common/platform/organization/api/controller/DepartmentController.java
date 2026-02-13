package com.fabricmanagement.common.platform.organization.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.organization.domain.Department;
import com.fabricmanagement.common.platform.organization.dto.DepartmentDto;
import com.fabricmanagement.common.platform.organization.infra.repository.DepartmentRepository;
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

  private final DepartmentRepository departmentRepository;

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
        departmentRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
            .map(DepartmentDto::from)
            .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(departments));
  }

  /** Get all departments for a specific company. */
  @GetMapping("/company/{companyId}")
  public ResponseEntity<ApiResponse<List<DepartmentDto>>> getDepartmentsByCompany(
      @PathVariable UUID companyId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting departments by company: tenantId={}, companyId={}", tenantId, companyId);

    List<DepartmentDto> departments =
        departmentRepository.findByTenantIdAndCompanyIdAndIsActiveTrue(tenantId, companyId).stream()
            .map(DepartmentDto::from)
            .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(departments));
  }

  /** Get department by ID. */
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<DepartmentDto>> getDepartment(@PathVariable UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting department: tenantId={}, id={}", tenantId, id);

    Department department =
        departmentRepository
            .findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new IllegalArgumentException("Department not found"));

    return ResponseEntity.ok(ApiResponse.success(DepartmentDto.from(department)));
  }
}
