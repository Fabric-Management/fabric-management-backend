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
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFiberRequest {

    @NotNull(message = "Material ID is required")
    private UUID materialId;

    @NotNull(message = "Category ID is required")
    private UUID categoryId;

    private UUID isoCodeId;

    @NotBlank(message = "Fiber code is required")
    private String fiberCode;

    @NotBlank(message = "Fiber name is required")
    private String fiberName;

    private String fiberGrade;

    private Double fineness;

    private Double lengthMm;

    private Double strengthCndTex;

    private Double elongationPercent;

    private String remarks;
}
