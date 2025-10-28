package com.fabricmanagement.production.masterdata.fiber.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Fiber - technical specifications for fiber instances.
 * Pure (100%) or blended. Composition must sum to 100%.
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

    @Column(name = "material_id", nullable = false, unique = true)
    private UUID materialId;

    @Column(name = "category_id")
    private UUID categoryId;  // FK → FiberCategory

    @Column(name = "iso_code_id")
    private UUID isoCodeId;  // FK → FiberIsoCode

    @Column(name = "fiber_code", nullable = false, length = 50)
    private String fiberCode;

   
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
