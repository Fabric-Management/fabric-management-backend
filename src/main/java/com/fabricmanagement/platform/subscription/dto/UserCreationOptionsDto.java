package com.fabricmanagement.platform.subscription.dto;

import com.fabricmanagement.platform.organization.dto.DepartmentDto;
import com.fabricmanagement.platform.organization.dto.OrganizationAddressDto;
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
 *   <li>Addresses (organization locations for work location assignment)
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreationOptionsDto {

  /** List of roles available for selection. */
  private List<com.fabricmanagement.platform.user.dto.RoleDto> roles;

  /** List of departments (hierarchical — top-level groups + child departments). */
  private List<DepartmentDto> departments;

  /** Organization addresses (company locations) for work location dropdown. */
  private List<OrganizationAddressDto> addresses;
}
