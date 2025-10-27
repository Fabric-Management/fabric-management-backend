package com.fabricmanagement.production.masterdata.material.dto;

import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request for creating new material.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMaterialRequest {

    private String materialCode;

    @NotBlank(message = "Material name is required")
    private String materialName;

    @NotNull(message = "Material type is required")
    private MaterialType materialType;

    @NotNull(message = "Category ID is required")
    private UUID categoryId;

    @NotBlank(message = "Unit is required")
    private String unit;

    private String description;
}

