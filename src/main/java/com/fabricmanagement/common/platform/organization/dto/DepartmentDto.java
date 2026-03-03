package com.fabricmanagement.common.platform.organization.dto;

import com.fabricmanagement.common.platform.organization.domain.Department;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentDto {

  private UUID id;
  private UUID tenantId;
  private String uid;
  private UUID organizationId;
  private String departmentName;
  private String departmentCode;
  private String description;
  private UUID managerId;
  private UUID parentDepartmentId;
  private String parentDepartmentName;
  private Boolean isSystemDepartment;
  private Integer displayOrder;
  private Boolean isActive;

  public static DepartmentDto from(Department department) {
    return DepartmentDto.builder()
        .id(department.getId())
        .tenantId(department.getTenantId())
        .uid(department.getUid())
        .organizationId(department.getOrganizationId())
        .departmentName(department.getDepartmentName())
        .departmentCode(department.getDepartmentCode())
        .description(department.getDescription())
        .managerId(department.getManagerId())
        .parentDepartmentId(
            department.getParentDepartment() != null
                ? department.getParentDepartment().getId()
                : null)
        .parentDepartmentName(
            department.getParentDepartment() != null
                ? department.getParentDepartment().getDepartmentName()
                : null)
        .isSystemDepartment(department.getIsSystemDepartment())
        .displayOrder(department.getDisplayOrder())
        .isActive(department.getIsActive())
        .build();
  }
}
