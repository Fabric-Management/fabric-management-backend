package com.fabricmanagement.common.platform.organization.dto;

import com.fabricmanagement.common.platform.organization.domain.DepartmentCategory;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
