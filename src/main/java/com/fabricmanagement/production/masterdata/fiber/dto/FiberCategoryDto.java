package com.fabricmanagement.production.masterdata.fiber.dto;

import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberCategoryDto {
    private UUID id;
    private UUID tenantId;
    private String uid;
    private String categoryCode;
    private String categoryName;
    private String description;
    private Integer displayOrder;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public static FiberCategoryDto from(FiberCategory entity) {
        return FiberCategoryDto.builder()
            .id(entity.getId())
            .tenantId(entity.getTenantId())
            .uid(entity.getUid())
            .categoryCode(entity.getCategoryCode())
            .categoryName(entity.getCategoryName())
            .description(entity.getDescription())
            .displayOrder(entity.getDisplayOrder())
            .isActive(entity.getIsActive())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}

