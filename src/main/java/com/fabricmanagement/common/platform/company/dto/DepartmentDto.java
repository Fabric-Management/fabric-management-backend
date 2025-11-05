package com.fabricmanagement.common.platform.company.dto;

import com.fabricmanagement.common.platform.company.domain.Department;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentDto {

    private UUID id;
    private UUID tenantId;
    private String uid;
    private UUID companyId;
    private String departmentName;
    private String description;
    private UUID managerId;
    private UUID departmentCategoryId;
    private String departmentCategoryName;
    private Boolean isActive;

    public static DepartmentDto from(Department department) {
        return DepartmentDto.builder()
            .id(department.getId())
            .tenantId(department.getTenantId())
            .uid(department.getUid())
            .companyId(department.getCompanyId())
            .departmentName(department.getDepartmentName())
            .description(department.getDescription())
            .managerId(department.getManagerId())
            .departmentCategoryId(department.getDepartmentCategory() != null ? department.getDepartmentCategory().getId() : null)
            .departmentCategoryName(department.getDepartmentCategory() != null ? department.getDepartmentCategory().getCategoryName() : null)
            .isActive(department.getIsActive())
            .build();
    }
}

