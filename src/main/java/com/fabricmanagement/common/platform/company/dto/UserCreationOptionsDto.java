package com.fabricmanagement.common.platform.company.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user creation form options.
 *
 * <p>Provides all necessary data for user creation form in a single response:
 *
 * <ul>
 *   <li>Roles
 *   <li>Department Categories
 *   <li>Departments
 *   <li>Positions
 * </ul>
 *
 * <p><b>Performance Benefits:</b>
 *
 * <ul>
 *   <li>✅ Single HTTP request instead of 4 separate requests
 *   <li>✅ Reduced network overhead
 *   <li>✅ Faster page load (parallel loading not needed)
 *   <li>✅ Single database transaction
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreationOptionsDto {

  /** List of roles available for selection. */
  private List<com.fabricmanagement.common.platform.user.dto.RoleDto> roles;

  /** List of department categories available for selection. */
  private List<DepartmentCategoryDto> departmentCategories;

  /** List of departments available for selection. */
  private List<DepartmentDto> departments;

  /** List of positions available for selection. */
  private List<PositionDto> positions;
}
