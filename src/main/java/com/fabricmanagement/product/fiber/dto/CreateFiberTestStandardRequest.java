package com.fabricmanagement.product.fiber.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateFiberTestStandardRequest")
public record CreateFiberTestStandardRequest(
    @NotBlank @Size(max = 100) String code,
    @NotBlank @Size(max = 255) String name,
    @Size(max = 100) @Schema(nullable = true) String revision) {}
