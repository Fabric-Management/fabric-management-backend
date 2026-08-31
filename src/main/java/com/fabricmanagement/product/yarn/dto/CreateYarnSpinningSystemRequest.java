package com.fabricmanagement.product.yarn.dto;

import com.fabricmanagement.product.yarn.domain.vocabulary.SpinningTechnologyFamily;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Create a tenant-owned spinning-system catalogue row")
public record CreateYarnSpinningSystemRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 50) String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String name,
    String description,
    Integer displayOrder,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
        SpinningTechnologyFamily technologyFamily) {}
