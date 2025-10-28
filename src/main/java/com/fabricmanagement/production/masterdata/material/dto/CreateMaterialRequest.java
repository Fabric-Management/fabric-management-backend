package com.fabricmanagement.production.masterdata.material.dto;

import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request for creating new material.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMaterialRequest {

    @NotNull(message = "Material type is required")
    private MaterialType materialType;

    @NotBlank(message = "Unit is required")
    private String unit;
}

