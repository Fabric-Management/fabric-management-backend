package com.fabricmanagement.common.platform.company.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.company.domain.Department;
import com.fabricmanagement.common.platform.company.dto.DepartmentDto;
import com.fabricmanagement.common.platform.company.infra.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/common/departments")
@RequiredArgsConstructor
@Slf4j
public class DepartmentController {

    private final DepartmentRepository departmentRepository;

    /**
     * Get all departments for current tenant.
     * 
     * <p>Returns active departments only, ordered by department name.</p>
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartmentDto>>> getAllDepartments() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Getting all departments: tenantId={}", tenantId);

        List<DepartmentDto> departments = departmentRepository.findAll().stream()
            .filter(dept -> dept.getTenantId().equals(tenantId))
            .filter(dept -> Boolean.TRUE.equals(dept.getIsActive()))
            .map(DepartmentDto::from)
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(departments));
    }

    /**
     * Get all departments for a specific company.
     */
    @GetMapping("/company/{companyId}")
    public ResponseEntity<ApiResponse<List<DepartmentDto>>> getDepartmentsByCompany(@PathVariable UUID companyId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Getting departments by company: tenantId={}, companyId={}", tenantId, companyId);

        List<DepartmentDto> departments = departmentRepository
            .findByTenantIdAndCompanyIdAndIsActiveTrue(tenantId, companyId)
            .stream()
            .map(DepartmentDto::from)
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(departments));
    }

    /**
     * Get department by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentDto>> getDepartment(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Getting department: tenantId={}, id={}", tenantId, id);

        Department department = departmentRepository.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new IllegalArgumentException("Department not found"));

        return ResponseEntity.ok(ApiResponse.success(DepartmentDto.from(department)));
    }
}

