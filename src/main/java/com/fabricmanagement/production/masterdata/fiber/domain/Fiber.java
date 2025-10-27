package com.fabricmanagement.production.masterdata.fiber.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Fiber entity - Concrete fiber instances with technical specifications.
 *
 * <p>Represents actual fiber products that can be:</p>
 * <ul>
 *   <li>Pure fibers (100% Cotton)</li>
 *   <li>Blended fibers (60% Cotton + 40% Polyester)</li>
 * </ul>
 *
 * <p>Composition must always sum to 100% (enforced by business logic).</p>
 */
@Entity
@Table(name = "prod_fiber", schema = "production",
    indexes = {
        @Index(name = "idx_fiber_tenant", columnList = "tenant_id"),
        @Index(name = "idx_fiber_material", columnList = "material_id"),
        @Index(name = "idx_fiber_code", columnList = "fiber_code"),
        @Index(name = "idx_fiber_category", columnList = "category_id"),
        @Index(name = "idx_fiber_status", columnList = "status")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_fiber_material", columnNames = {"material_id"}),
        @UniqueConstraint(name = "uk_fiber_tenant_code", columnNames = {"tenant_id", "fiber_code"})
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fiber extends BaseEntity {

    // =====================================================
    // RELATIONSHIPS
    // =====================================================

    /**
     * Reference to parent Material entity.
     * One-to-One: Each fiber has one material, each material (FIBER type) has one fiber detail.
     */
    @Column(name = "material_id", nullable = false, unique = true)
    private UUID materialId;

    /**
     * Reference to FiberCategory.
     * NATURAL_PLANT, NATURAL_ANIMAL, REGENERATED_CELLULOSIC, etc.
     */
    @Column(name = "category_id")
    private UUID categoryId;  // FK → FiberCategory

    /**
     * Reference to FiberIsoCode.
     * CO (Cotton), PES (Polyester), etc. for textile labeling.
     */
    @Column(name = "iso_code_id")
    private UUID isoCodeId;  // FK → FiberIsoCode

    // =====================================================
    // IDENTITY FIELDS
    // =====================================================

    /**
     * Fiber code - tenant-specific identifier.
     * Example: "COT-001", "POLY-GRADE-A", "BLEND-60-40"
     */
    @Column(name = "fiber_code", nullable = false, length = 50)
    private String fiberCode;

    /**
     * Fiber name - display name.
     * Example: "Cotton Fiber Grade A", "Polyester Recycled", "Cotton-Poly Blend 60/40"
     */
    @Column(name = "fiber_name", nullable = false, length = 255)
    private String fiberName;

    /**
     * Fiber grade classification.
     * Examples: "A", "B", "C", "Grade 1", "Grade 2", "Premium"
     */
    @Column(name = "fiber_grade", length = 50)
    private String fiberGrade;

    // =====================================================
    // TECHNICAL SPECIFICATIONS (Pure fibers only)
    // =====================================================

    /**
     * Fineness - fiber thickness measurement.
     * Unit: Micronaire (for cotton) or dtex (for synthetics)
     * Typical ranges: Cotton 2.5-5.5, Polyester 1.0-2.0 dtex
     */
    @Column(name = "fineness")
    private Double fineness;

    /**
     * Average fiber length in millimeters.
     * Key quality indicator for spinnability.
     * Typical ranges: Cotton 20-35mm, Polyester 38-102mm
     */
    @Column(name = "length_mm")
    private Double lengthMm;

    /**
     * Tenacity / Strength in centinewtons per dtex (cN/dtex).
     * Measures fiber strength under load.
     * Typical ranges: Cotton 2.5-4.5, Polyester 4.5-6.5, Nylon 4.5-9.5
     */
    @Column(name = "strength_cn_dtex")
    private Double strengthCndTex;

    /**
     * Elongation at break in percentage.
     * Measures fiber stretchability.
     * Typical ranges: Cotton 6-8%, Polyester 40-50%, Nylon 25-45%
     */
    @Column(name = "elongation_percent")
    private Double elongationPercent;

    // =====================================================
    // STATUS & METADATA
    // =====================================================

    /**
     * Status - fiber lifecycle state.
     * NEW: Created, not yet used
     * IN_USE: Actively used in production
     * EXHAUSTED: Stock depleted
     * OBSOLETE: No longer used
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private FiberStatus status = FiberStatus.NEW;

    /**
     * Additional remarks or notes.
     */
    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    /**
     * Create new pure fiber (100% single fiber type).
     */
    public static Fiber createPureFiber(
            UUID materialId,
            UUID categoryId,
            UUID isoCodeId,
            String fiberCode,
            String fiberName,
            String fiberGrade,
            Double fineness,
            Double lengthMm,
            Double strengthCndTex,
            Double elongationPercent) {
        
        return Fiber.builder()
            .materialId(materialId)
            .categoryId(categoryId)
            .isoCodeId(isoCodeId)
            .fiberCode(fiberCode)
            .fiberName(fiberName)
            .fiberGrade(fiberGrade)
            .fineness(fineness)
            .lengthMm(lengthMm)
            .strengthCndTex(strengthCndTex)
            .elongationPercent(elongationPercent)
            .status(FiberStatus.NEW)
            .build();
    }

    /**
     * Create new blended fiber (composition managed separately).
     */
    public static Fiber createBlendedFiber(
            UUID materialId,
            UUID categoryId,
            UUID isoCodeId,
            String fiberCode,
            String fiberName,
            String fiberGrade) {
        
        return Fiber.builder()
            .materialId(materialId)
            .categoryId(categoryId)
            .isoCodeId(isoCodeId)
            .fiberCode(fiberCode)
            .fiberName(fiberName)
            .fiberGrade(fiberGrade)
            .status(FiberStatus.NEW)
            .build();
    }

    /**
     * Update fiber details.
     */
    public void update(String fiberName, String fiberGrade, Double fineness, Double lengthMm,
                       Double strengthCndTex, Double elongationPercent, String remarks, FiberStatus status) {
        this.fiberName = fiberName;
        this.fiberGrade = fiberGrade;
        this.fineness = fineness;
        this.lengthMm = lengthMm;
        this.strengthCndTex = strengthCndTex;
        this.elongationPercent = elongationPercent;
        this.remarks = remarks;
        this.status = status;
    }

    @Override
    protected String getModuleCode() {
        return "FIB";
    }
}
