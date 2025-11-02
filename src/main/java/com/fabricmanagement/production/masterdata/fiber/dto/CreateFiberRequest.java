package com.fabricmanagement.production.masterdata.fiber.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request for creating new fiber.
 * 
 * <p><b>User-Friendly Design:</b> Material can be auto-created automatically.</p>
 * <p>If materialId is provided, existing Material will be used.</p>
 * <p>If materialId is null, Material will be auto-created with type=FIBER and provided unit.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFiberRequest {

    /**
     * Material ID (optional).
     * 
     * <p>If null, Material will be auto-created with type=FIBER and unit.</p>
     */
    private UUID materialId;
    
    /**
     * Unit for Material (required if materialId is null).
     * 
     * <p>Used when auto-creating Material. Examples: "kg", "ton", "m", etc.</p>
     */
    private String unit;

    @NotNull(message = "Fiber Category ID is required")
    private UUID fiberCategoryId;

    private UUID fiberIsoCodeId;

    @NotBlank(message = "Fiber name is required")
    private String fiberName;

    private String fiberGrade;

    private Double fineness;

    private Double lengthMm;

    private Double strengthCndTex;

    private Double elongationPercent;

    private String remarks;
}
