package com.fabricmanagement.product.fiber.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "RenameFiberTestStandardRequest")
public record RenameFiberTestStandardRequest(@NotBlank @Size(max = 255) String name) {}
