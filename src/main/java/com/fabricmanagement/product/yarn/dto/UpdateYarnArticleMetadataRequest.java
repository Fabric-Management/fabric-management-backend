package com.fabricmanagement.product.yarn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Replace mutable yarn article metadata without changing its spec version")
public record UpdateYarnArticleMetadataRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 255) String name,
    String description) {}
