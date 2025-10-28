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
    private MaterialType materialType;
    private String unit;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public static MaterialDto from(Material entity) {
        return MaterialDto.builder()
            .id(entity.getId())
            .tenantId(entity.getTenantId())
            .uid(entity.getUid())
            .materialType(entity.getMaterialType())
            .unit(entity.getUnit())
            .isActive(entity.getIsActive())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}

