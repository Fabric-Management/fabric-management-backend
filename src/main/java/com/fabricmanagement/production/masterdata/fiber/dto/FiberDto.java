package com.fabricmanagement.production.masterdata.fiber.dto;

import com.fabricmanagement.production.masterdata.fiber.domain.Fiber;
import com.fabricmanagement.production.masterdata.fiber.domain.FiberStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Fiber DTO - Data transfer object for Fiber entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberDto {

    private UUID id;
    private UUID tenantId;
    private String uid;
    private UUID materialId;
    private UUID fiberCategoryId;
    private UUID fiberIsoCodeId;
    private String fiberName;
    private String fiberGrade;
    private Double fineness;
    private Double lengthMm;
    private Double strengthCndTex;
    private Double elongationPercent;
    private FiberStatus status;
    private String remarks;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Map entity to DTO.
     *
     * <p><b>STANDARD:</b> All DTOs use this pattern</p>
     */
    public static FiberDto from(Fiber entity) {
        return FiberDto.builder()
            .id(entity.getId())
            .tenantId(entity.getTenantId())
            .uid(entity.getUid())
            .materialId(entity.getMaterialId())
            .fiberCategoryId(entity.getFiberCategoryId())
            .fiberIsoCodeId(entity.getFiberIsoCodeId())
            .fiberName(entity.getFiberName())
            .fiberGrade(entity.getFiberGrade())
            .fineness(entity.getFineness())
            .lengthMm(entity.getLengthMm())
            .strengthCndTex(entity.getStrengthCndTex())
            .elongationPercent(entity.getElongationPercent())
            .status(entity.getStatus())
            .remarks(entity.getRemarks())
            .isActive(entity.getIsActive())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}
