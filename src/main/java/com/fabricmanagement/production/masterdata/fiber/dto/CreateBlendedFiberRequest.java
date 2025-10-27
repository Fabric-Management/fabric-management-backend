package com.fabricmanagement.production.masterdata.fiber.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Request for creating a blended fiber.
 * 
 * <p>Example: 60% Cotton + 40% Viscose</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBlendedFiberRequest {

    @NotNull(message = "Material ID is required")
    private UUID materialId;

    @NotNull(message = "Category ID is required")
    private UUID categoryId;

    private UUID isoCodeId;

    @NotBlank(message = "Fiber code is required")
    private String fiberCode;

    /**
     * Fiber name (optional - will be auto-generated if not provided).
     * 
     * <p>Example: "Cotton 60% Viscose 40% Blend"</p>
     */
    private String fiberName;

    private String fiberGrade;

    private String remarks;

    /**
     * Composition map: baseFiberId → percentage
     * 
     * <p>Example: {cottonId: 60.0, viscoseId: 40.0}</p>
     * <p><b>Total must be exactly 100%!</b></p>
     */
    @NotNull(message = "Composition is required")
    private Map<UUID, BigDecimal> composition;
}

