package com.fabricmanagement.production.masterdata.material.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Material entity - Master data for production materials.
 *
 * <p>Represents all materials used in production:</p>
 * <ul>
 *   <li>FIBER - Raw materials (cotton, polyester)</li>
 *   <li>YARN - Spun yarns</li>
 *   <li>FABRIC - Finished fabrics</li>
 *   <li>CHEMICAL - Dyes and auxiliaries</li>
 *   <li>CONSUMABLE - Supplies and spare parts</li>
 * </ul>
 *
 * <h2>Multi-Tenancy:</h2>
 * <p>Each tenant has their own material catalog.</p>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * Material cotton = Material.create(
 *     "Cotton Fiber - Grade A",
 *     MaterialType.FIBER,
 *     "Cotton",
 *     "kg",
 *     new BigDecimal("5.50")
 * );
 * }</pre>
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

    /**
     * Update material details.
     */
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

