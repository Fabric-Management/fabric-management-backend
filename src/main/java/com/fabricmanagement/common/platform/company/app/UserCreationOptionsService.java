package com.fabricmanagement.common.platform.company.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.company.api.facade.CompanyFacade;
import com.fabricmanagement.common.platform.company.domain.Department;
import com.fabricmanagement.common.platform.company.domain.DepartmentCategory;
import com.fabricmanagement.common.platform.company.domain.Position;
import com.fabricmanagement.common.platform.company.dto.DepartmentCategoryDto;
import com.fabricmanagement.common.platform.company.dto.DepartmentDto;
import com.fabricmanagement.common.platform.company.dto.PositionDto;
import com.fabricmanagement.common.platform.company.dto.UserCreationOptionsDto;
import com.fabricmanagement.common.platform.company.infra.repository.DepartmentCategoryRepository;
import com.fabricmanagement.common.platform.company.infra.repository.DepartmentRepository;
import com.fabricmanagement.common.platform.company.infra.repository.PositionRepository;
import com.fabricmanagement.common.platform.user.app.RoleService;
import com.fabricmanagement.common.platform.user.domain.Role;
import com.fabricmanagement.common.platform.user.dto.RoleDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for providing user creation form options in a single response.
 * 
 * <p><b>Purpose:</b> Optimize frontend form loading by providing all required data
 * (department categories, departments, positions) in a single API call.</p>
 * 
 * <p><b>Performance Benefits:</b>
 * <ul>
 *   <li>Single HTTP request instead of 3 separate requests</li>
 *   <li>Reduced network round-trips</li>
 *   <li>Faster page load time</li>
 *   <li>Single database transaction</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserCreationOptionsService {

    private final RoleService roleService;
    private final DepartmentCategoryRepository departmentCategoryRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final TenantSeedService tenantSeedService;
    private final CompanyFacade companyFacade;

    /**
     * Get all options needed for user creation form.
     * 
     * <p>Returns roles, department categories, departments, and positions in a single response.
     * All data is tenant-scoped and filtered to active records only.</p>
     * 
     * <p><b>Cached:</b> 10 minutes (tenant-scoped cache key)</p>
     * 
     * @return User creation options (roles, categories, departments, positions)
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "userCreationOptions", key = "T(com.fabricmanagement.common.infrastructure.persistence.TenantContext).getCurrentTenantId()")
    public UserCreationOptionsDto getUserCreationOptions() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Getting user creation options: tenantId={}", tenantId);

        // Get company ID (tenant_id = company_id for root tenant)
        UUID companyId = companyFacade.findById(tenantId, tenantId)
            .map(c -> c.getId())
            .orElse(tenantId); // Fallback to tenantId if company not found

        // Auto-seed if tenant has no departments (lazy initialization)
        if (!tenantSeedService.isTenantSeeded(tenantId, companyId)) {
            log.info("Tenant not seeded, auto-seeding departments and positions: tenantId={}", tenantId);
            tenantSeedService.seedDepartmentsAndPositions(tenantId, companyId);
        }

        // Fetch all data in single transaction (efficient)
        List<Role> roles = roleService.findAll();
        
        // Get department categories: both system-wide (reference data) and tenant-specific
        UUID systemTenantId = TenantContext.SYSTEM_TENANT_ID;
        List<DepartmentCategory> systemCategories = departmentCategoryRepository
            .findByTenantIdAndIsActiveTrueOrderByDisplayOrderAsc(systemTenantId);
        List<DepartmentCategory> tenantCategories = departmentCategoryRepository
            .findByTenantIdAndIsActiveTrueOrderByDisplayOrderAsc(tenantId);
        
        // Combine: system categories (reference) + tenant-specific categories
        List<DepartmentCategory> allCategories = new java.util.ArrayList<>();
        allCategories.addAll(systemCategories);
        allCategories.addAll(tenantCategories);
        
        // Get departments by tenant (optimized query)
        List<Department> departments = departmentRepository.findByTenantIdAndIsActiveTrue(tenantId);
        
        List<Position> positions = positionRepository.findByTenantId(tenantId);

        // Convert to DTOs
        List<RoleDto> roleDtos = roles.stream()
            .map(RoleDto::from)
            .collect(Collectors.toList());
        
        List<DepartmentCategoryDto> categoryDtos = allCategories.stream()
            .map(DepartmentCategoryDto::from)
            .collect(Collectors.toList());
        
        List<DepartmentDto> departmentDtos = departments.stream()
            .map(DepartmentDto::from)
            .collect(Collectors.toList());
        
        List<PositionDto> positionDtos = positions.stream()
            .map(PositionDto::from)
            .collect(Collectors.toList());

        log.debug("User creation options: roles={}, categories={}, departments={}, positions={}", 
            roleDtos.size(), categoryDtos.size(), departmentDtos.size(), positionDtos.size());

        return UserCreationOptionsDto.builder()
            .roles(roleDtos)
            .departmentCategories(categoryDtos)
            .departments(departmentDtos)
            .positions(positionDtos)
            .build();
    }
}

