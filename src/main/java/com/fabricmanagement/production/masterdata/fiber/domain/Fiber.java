package com.fabricmanagement.production.masterdata.fiber.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
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
    private com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCategory fiberCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiber_iso_code_id")
    private com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberIsoCode fiberIsoCode;

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
            com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCategory fiberCategory,
            com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberIsoCode fiberIsoCode,
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
            com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCategory fiberCategory,
            com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberIsoCode fiberIsoCode,
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
