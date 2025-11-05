package com.fabricmanagement.common.platform.company.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for user creation form options.
 * 
 * <p>Provides all necessary data for user creation form in a single response:
 * <ul>
 *   <li>Roles</li>
 *   <li>Department Categories</li>
 *   <li>Departments</li>
 *   <li>Positions</li>
 * </ul>
 * 
 * <p><b>Performance Benefits:</b>
 * <ul>
 *   <li>✅ Single HTTP request instead of 4 separate requests</li>
 *   <li>✅ Reduced network overhead</li>
 *   <li>✅ Faster page load (parallel loading not needed)</li>
 *   <li>✅ Single database transaction</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreationOptionsDto {

    /**
     * List of roles available for selection.
     */
    private List<com.fabricmanagement.common.platform.user.dto.RoleDto> roles;

    /**
     * List of department categories available for selection.
     */
    private List<DepartmentCategoryDto> departmentCategories;

    /**
     * List of departments available for selection.
     */
    private List<DepartmentDto> departments;

    /**
     * List of positions available for selection.
     */
    private List<PositionDto> positions;
}

