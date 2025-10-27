package com.fabricmanagement.production.masterdata.fiber.dto;

import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberIsoCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for fiber type (ISO code reference).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberTypeDto {

    private UUID id;
    private UUID tenantId;
    private String uid;
    private String isoCode;
    private String fiberName;
    private String fiberType;
    private String description;
    private Boolean isOfficialIso;
    private Integer displayOrder;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public static FiberTypeDto from(FiberIsoCode fiberIsoCode) {
        return FiberTypeDto.builder()
            .id(fiberIsoCode.getId())
            .tenantId(fiberIsoCode.getTenantId())
            .uid(fiberIsoCode.getUid())
            .isoCode(fiberIsoCode.getIsoCode())
            .fiberName(fiberIsoCode.getFiberName())
            .fiberType(fiberIsoCode.getFiberType())
            .description(fiberIsoCode.getDescription())
            .isOfficialIso(fiberIsoCode.getIsOfficialIso())
            .displayOrder(fiberIsoCode.getDisplayOrder())
            .isActive(fiberIsoCode.getIsActive())
            .createdAt(fiberIsoCode.getCreatedAt())
            .updatedAt(fiberIsoCode.getUpdatedAt())
            .build();
    }
}

