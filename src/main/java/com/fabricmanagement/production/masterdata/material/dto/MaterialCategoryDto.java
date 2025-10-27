package com.fabricmanagement.production.masterdata.material.dto;

import com.fabricmanagement.production.masterdata.material.domain.MaterialCategory;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Material Category DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialCategoryDto {

    private UUID id;
    private UUID tenantId;
    private String uid;
    private MaterialType materialType;
    private String categoryName;
    private String description;
    private Boolean isSystemCategory;
    private Integer displayOrder;
    private Boolean isActive;
    private Instant createdAt;

    /**
     * Map entity to DTO.
     */
    public static MaterialCategoryDto from(MaterialCategory entity) {
        return MaterialCategoryDto.builder()
            .id(entity.getId())
            .tenantId(entity.getTenantId())
            .uid(entity.getUid())
            .materialType(entity.getMaterialType())
            .categoryName(entity.getCategoryName())
            .description(entity.getDescription())
            .isSystemCategory(entity.getIsSystemCategory())
            .displayOrder(entity.getDisplayOrder())
            .isActive(entity.getIsActive())
            .createdAt(entity.getCreatedAt())
            .build();
    }
}
