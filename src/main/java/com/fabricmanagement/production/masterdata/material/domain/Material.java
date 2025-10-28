package com.fabricmanagement.production.masterdata.material.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Material - master data for production materials.
 * Types: FIBER, YARN, FABRIC, CHEMICAL, CONSUMABLE
 */
@Entity
@Table(name = "prod_material", schema = "production",
    indexes = {
        @Index(name = "idx_material_tenant_type", columnList = "tenant_id,material_type"),
        @Index(name = "idx_material_code", columnList = "material_code"),
        @Index(name = "idx_material_active", columnList = "is_active")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Material extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false, length = 20)
    private MaterialType materialType;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;
    
    public static Material create(MaterialType type, String unit) {
        return Material.builder()
            .materialType(type)
            .unit(unit)
            .build();
    }

    @Override
    protected String getModuleCode() {
        return "MAT";
    }
}

