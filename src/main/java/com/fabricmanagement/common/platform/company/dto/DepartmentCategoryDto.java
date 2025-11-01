package com.fabricmanagement.common.platform.company.dto;

import com.fabricmanagement.common.platform.company.domain.DepartmentCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentCategoryDto {

    private UUID id;
    private UUID tenantId;
    private String uid;
    private String categoryName;
    private String description;
    private Integer displayOrder;
    private Boolean isActive;

    public static DepartmentCategoryDto from(DepartmentCategory category) {
        return DepartmentCategoryDto.builder()
            .id(category.getId())
            .tenantId(category.getTenantId())
            .uid(category.getUid())
            .categoryName(category.getCategoryName())
            .description(category.getDescription())
            .displayOrder(category.getDisplayOrder())
            .isActive(category.getIsActive())
            .build();
    }
}

