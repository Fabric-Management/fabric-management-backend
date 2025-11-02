package com.fabricmanagement.production.masterdata.fiber.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCategory;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberIsoCode;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "prod_fiber", schema = "production",
    indexes = {
        @Index(name = "idx_fiber_tenant", columnList = "tenant_id"),
        @Index(name = "idx_fiber_material", columnList = "material_id"),
        @Index(name = "idx_fiber_category", columnList = "fiber_category_id"),
        @Index(name = "idx_fiber_iso", columnList = "fiber_iso_code_id"),
        @Index(name = "idx_fiber_status", columnList = "status")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_fiber_material", columnNames = {"material_id"})
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fiber extends BaseEntity {

    @Column(name = "material_id", nullable = false, unique = true, updatable = false)
    private UUID materialId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiber_category_id")
    private FiberCategory fiberCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiber_iso_code_id")
    private FiberIsoCode fiberIsoCode;

    // Helper methods for accessing IDs without loading entities
    public UUID getFiberCategoryId() {
        return fiberCategory != null ? fiberCategory.getId() : null;
    }

    public UUID getFiberIsoCodeId() {
        return fiberIsoCode != null ? fiberIsoCode.getId() : null;
    }

    @Column(name = "fiber_name", nullable = false, length = 255)
    private String fiberName;

    @Column(name = "fiber_grade", length = 50)
    private String fiberGrade;

    @Column(name = "fineness")
    private Double fineness;

    @Column(name = "length_mm")
    private Double lengthMm;

    @Column(name = "strength_cn_dtex")
    private Double strengthCndTex;

    @Column(name = "elongation_percent")
    private Double elongationPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private FiberStatus status = FiberStatus.NEW;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    public static Fiber createPureFiber(
            UUID materialId,
            FiberCategory fiberCategory,
            FiberIsoCode fiberIsoCode,
            String fiberName,
            String fiberGrade,
            Double fineness,
            Double lengthMm,
            Double strengthCndTex,
            Double elongationPercent) {
        
        return Fiber.builder()
            .materialId(materialId)
            .fiberCategory(fiberCategory)
            .fiberIsoCode(fiberIsoCode)
            .fiberName(fiberName)
            .fiberGrade(fiberGrade)
            .fineness(fineness)
            .lengthMm(lengthMm)
            .strengthCndTex(strengthCndTex)
            .elongationPercent(elongationPercent)
            .status(FiberStatus.NEW)
            .build();
    }

    public static Fiber createBlendedFiber(
            UUID materialId,
            FiberCategory fiberCategory,
            FiberIsoCode fiberIsoCode,
            String fiberName,
            String fiberGrade) {
        
        return Fiber.builder()
            .materialId(materialId)
            .fiberCategory(fiberCategory)
            .fiberIsoCode(fiberIsoCode)
            .fiberName(fiberName)
            .fiberGrade(fiberGrade)
            .status(FiberStatus.NEW)
            .build();
    }

    /**
     * Update fiber properties (excluding status - use lifecycle methods instead).
     */
    public void update(String fiberName, String fiberGrade, Double fineness, Double lengthMm,
                       Double strengthCndTex, Double elongationPercent, String remarks) {
        this.fiberName = fiberName;
        this.fiberGrade = fiberGrade;
        this.fineness = fineness;
        this.lengthMm = lengthMm;
        this.strengthCndTex = strengthCndTex;
        this.elongationPercent = elongationPercent;
        this.remarks = remarks;
    }

    /**
     * Mark fiber as in use.
     * <p>Transition: NEW → IN_USE</p>
     */
    public void markInUse() {
        if (this.status == FiberStatus.NEW) {
            this.status = FiberStatus.IN_USE;
        } else {
            throw new IllegalStateException(
                String.format("Cannot mark fiber as IN_USE from status: %s. Only NEW fibers can be marked IN_USE.", this.status));
        }
    }

    /**
     * Mark fiber as exhausted.
     * <p>Transition: IN_USE → EXHAUSTED</p>
     */
    public void markExhausted() {
        if (this.status == FiberStatus.IN_USE) {
            this.status = FiberStatus.EXHAUSTED;
        } else {
            throw new IllegalStateException(
                String.format("Cannot mark fiber as EXHAUSTED from status: %s. Only IN_USE fibers can be marked EXHAUSTED.", this.status));
        }
    }

    /**
     * Mark fiber as obsolete.
     * <p>Transition: Any status → OBSOLETE</p>
     * <p>Can be called from any state when fiber becomes outdated.</p>
     */
    public void markObsolete() {
        this.status = FiberStatus.OBSOLETE;
    }

    /**
     * Check if fiber is available for use.
     *
     * @return true if status is NEW or IN_USE
     */
    public boolean isAvailable() {
        return this.status == FiberStatus.NEW || this.status == FiberStatus.IN_USE;
    }

    /**
     * Check if fiber is pure (not blended).
     * <p>Pure fibers have technical specifications (fineness, length, etc.).</p>
     *
     * @return true if fiber has technical specifications
     */
    public boolean isPure() {
        return this.fineness != null || this.lengthMm != null || 
               this.strengthCndTex != null || this.elongationPercent != null;
    }

    @Override
    protected String getModuleCode() {
        return "FIB";
    }
}
