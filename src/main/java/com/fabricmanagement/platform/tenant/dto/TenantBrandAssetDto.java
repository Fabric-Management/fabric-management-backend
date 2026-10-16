package com.fabricmanagement.platform.tenant.dto;

import com.fabricmanagement.platform.tenant.domain.BrandAssetKind;
import com.fabricmanagement.platform.tenant.domain.BrandColourMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "A tenant-owned, validated SVG. URLs require the current tenant session.")
public record TenantBrandAssetDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BrandAssetKind kind,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BrandColourMode colourMode,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255) String fileName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String url) {}
