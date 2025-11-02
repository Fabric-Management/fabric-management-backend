package com.fabricmanagement.production.masterdata.fiber.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseJunctionEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fiber ↔ Fiber Composition (Blended fibers) - Junction Entity.
 * 
 * <p>For blended fibers: links base fibers with their percentage in blend.</p>
 * <p>Total % must sum to 100% (enforced by application logic).</p>
 * 
 * <h2>Example:</h2>
 * <pre>{@code
 * // 60% Cotton + 40% Viscose blend
 * FiberComposition comp1 = FiberComposition.builder()
 *     .blendedFiberId(blendedFiber.getId())
 *     .baseFiberId(cottonFiber.getId())
 *     .percentage(new BigDecimal("60.00"))
 *     .build();
 * 
 * FiberComposition comp2 = FiberComposition.builder()
 *     .blendedFiberId(blendedFiber.getId())
 *     .baseFiberId(viscoseFiber.getId())
 *     .percentage(new BigDecimal("40.00"))
 *     .build();
 * }</pre>
 */
@Entity
@Table(name = "prod_fiber_composition", schema = "production",
    indexes = {
        @Index(name = "idx_fiber_comp_blend", columnList = "blended_fiber_id"),
        @Index(name = "idx_fiber_comp_base", columnList = "base_fiber_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(FiberCompositionId.class)
public class FiberComposition extends BaseJunctionEntity {

    @Id
    @Column(name = "blended_fiber_id", nullable = false)
    private UUID blendedFiberId;

    @Id
    @Column(name = "base_fiber_id", nullable = false)
    private UUID baseFiberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blended_fiber_id", insertable = false, updatable = false)
    private Fiber blendedFiber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_fiber_id", insertable = false, updatable = false)
    private Fiber baseFiber;

    @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    @Override
    protected String getModuleCode() {
        return "FCOMP";
    }
}


