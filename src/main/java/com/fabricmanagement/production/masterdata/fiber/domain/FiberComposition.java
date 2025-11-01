package com.fabricmanagement.production.masterdata.fiber.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fiber ↔ Fiber Composition (Blended fibers).
 * 
 * <p>For blended fibers: links base fibers with their percentage in blend.</p>
 * <p>Total % must sum to 100% (enforced by application logic).</p>
 */
@Entity
@Table(name = "prod_fiber_composition", schema = "production",
    indexes = {
        @Index(name = "idx_fiber_comp_blend", columnList = "blended_fiber_id"),
        @Index(name = "idx_fiber_comp_base", columnList = "base_fiber_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_composition_blend_base", columnNames = {"blended_fiber_id", "base_fiber_id"})
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberComposition extends BaseEntity {

    @Column(name = "blended_fiber_id", nullable = false)
    private UUID blendedFiberId;  // FK → Fiber (blended)

    @Column(name = "base_fiber_id", nullable = false)
    private UUID baseFiberId;  // FK → Fiber (base)

    @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    @Override
    protected String getModuleCode() {
        return "FCOMP";
    }
}


