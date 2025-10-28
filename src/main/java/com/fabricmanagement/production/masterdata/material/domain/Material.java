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

    @Column(name = "material_code", unique = true, length = 50)
    private String materialCode;

    @Column(name = "material_name", nullable = false, length = 255)
    private String materialName;

    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false, length = 20)
    private MaterialType materialType;

    @Column(name = "category_id")
    private UUID categoryId;  // FK → MaterialCategory

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    public static Material create(String name, MaterialType type, UUID categoryId, String unit) {
        return Material.builder()
            .materialName(name)
            .materialType(type)
            .categoryId(categoryId)
            .unit(unit)
            .build();
    }

    public void update(String name, UUID categoryId, String description) {
        this.materialName = name;
        this.categoryId = categoryId;
        this.description = description;
    }

    @Override
    protected String getModuleCode() {
        return "MAT";
    }
}

