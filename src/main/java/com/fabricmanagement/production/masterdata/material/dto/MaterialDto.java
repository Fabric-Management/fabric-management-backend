package com.fabricmanagement.production.masterdata.material.dto;

import com.fabricmanagement.production.masterdata.material.domain.Material;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Material DTO - Data transfer object for Material entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialDto {

    private UUID id;
    private UUID tenantId;
    private String uid;
    private String materialCode;
    private String materialName;
    private MaterialType materialType;
    private UUID categoryId;
    private String categoryName; // Populated via JOIN
    private String unit;
    private String description;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Map entity to DTO.
     *
     * <p><b>STANDARD:</b> All DTOs use this pattern</p>
     */
    public static MaterialDto from(Material entity) {
        return MaterialDto.builder()
            .id(entity.getId())
            .tenantId(entity.getTenantId())
            .uid(entity.getUid())
            .materialCode(entity.getMaterialCode())
            .materialName(entity.getMaterialName())
            .materialType(entity.getMaterialType())
            .categoryId(entity.getCategoryId())
            .unit(entity.getUnit())
            .description(entity.getDescription())
            .isActive(entity.getIsActive())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}

