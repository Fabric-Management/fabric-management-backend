package com.fabricmanagement.common.platform.subscription.dto;

import com.fabricmanagement.common.platform.organization.dto.DepartmentDto;
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
 *   <li>Departments (hierarchical via parentDepartmentId)
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreationOptionsDto {

  /** List of roles available for selection. */
  private List<com.fabricmanagement.common.platform.user.dto.RoleDto> roles;

  /** List of departments (hierarchical — top-level groups + child departments). */
  private List<DepartmentDto> departments;
}
