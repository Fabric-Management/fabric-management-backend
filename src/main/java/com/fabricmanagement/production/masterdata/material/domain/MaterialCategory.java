package com.fabricmanagement.production.masterdata.material.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Material Category - Reference table for material categories.
 *
 * <p>Two-level system:</p>
 * <ul>
 *   <li><b>System categories:</b> tenant_id = SYSTEM (Cotton, Polyester, Wool)</li>
 *   <li><b>Tenant categories:</b> tenant_id = tenant UUID (custom categories)</li>
 * </ul>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * // System category
 * MaterialCategory cotton = MaterialCategory.createSystem(
 *     MaterialType.FIBER,
 *     "Cotton",
 *     "Natural cotton fiber"
 * );
 *
 * // Tenant custom category
 * MaterialCategory custom = MaterialCategory.createTenant(
 *     tenantId,
 *     MaterialType.FIBER,
 *     "Akkayalar Special Blend",
 *     "Custom blend for specific customer"
 * );
 * }</pre>
 */
@Entity
@Table(name = "prod_material_category", schema = "production",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenant_type_name", 
            columnNames = {"tenant_id", "material_type", "category_name"})
    },
    indexes = {
        @Index(name = "idx_category_tenant_type", columnList = "tenant_id,material_type"),
        @Index(name = "idx_category_system", columnList = "is_system_category")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialCategory extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false, length = 20)
    private MaterialType materialType;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_system_category", nullable = false)
    @Builder.Default
    private Boolean isSystemCategory = false;

    @Column(name = "display_order")
    private Integer displayOrder;

    /**
     * Create system-level category.
     */
    public static MaterialCategory createSystem(MaterialType type, String name, String description) {
        return MaterialCategory.builder()
            .materialType(type)
            .categoryName(name)
            .description(description)
            .isSystemCategory(true)
            .build();
    }

    /**
     * Create tenant-level custom category.
     */
    public static MaterialCategory createTenant(UUID tenantId, MaterialType type, 
                                               String name, String description) {
        MaterialCategory category = MaterialCategory.builder()
            .materialType(type)
            .categoryName(name)
            .description(description)
            .isSystemCategory(false)
            .build();
        category.setTenantId(tenantId);
        return category;
    }

    @Override
    protected String getModuleCode() {
        return "MCAT";
    }
}

