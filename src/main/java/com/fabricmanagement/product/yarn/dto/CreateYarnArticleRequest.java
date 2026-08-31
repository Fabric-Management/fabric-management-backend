package com.fabricmanagement.product.yarn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Create a draft yarn article bound to an existing YARN product")
public record CreateYarnArticleRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull UUID productId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 255) String name,
    String description,
    @Valid YarnArticleSpecRequest spec) {}
