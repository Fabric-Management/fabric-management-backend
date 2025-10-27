package com.fabricmanagement.production.masterdata.fiber.domain.reference;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Fiber Category - Reference table for fiber classification.
 *
 * <p>Defines the structural class of fiber based on chemical/biological origin.</p>
 * <p><b>READ-ONLY:</b> System-defined, cannot be created/modified by tenants.</p>
 * <p>Can only be activated/deactivated.</p>
 */
@Entity
@Table(name = "prod_fiber_category", schema = "production",
    indexes = {
        @Index(name = "idx_fiber_category_code", columnList = "category_code"),
        @Index(name = "idx_fiber_category_active", columnList = "is_active")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberCategory extends BaseEntity {

    @Column(name = "category_code", unique = true, nullable = false, length = 50, updatable = false)
    private String categoryCode;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Override
    protected String getModuleCode() {
        return "FCAT";
    }
}

