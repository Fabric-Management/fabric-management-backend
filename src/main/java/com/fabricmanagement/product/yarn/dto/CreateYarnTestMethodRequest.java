package com.fabricmanagement.product.yarn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Create a tenant-owned yarn test-method catalogue row")
public record CreateYarnTestMethodRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 50) String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String name,
    String description,
    Integer displayOrder,
    @Size(max = 100) String standardRef,
    @Size(max = 100) String instrument,
    @Size(max = 100) String applicablePropertyKey) {}
