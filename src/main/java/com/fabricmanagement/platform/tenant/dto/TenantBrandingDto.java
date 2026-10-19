package com.fabricmanagement.platform.tenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description =
        "Current tenant branding; absent slots are null. Mutation permission is backend-owned.")
public record TenantBrandingDto(
    @Schema(nullable = true) TenantBrandAssetDto logo,
    @Schema(nullable = true) TenantBrandAssetDto icon,
    @Schema(nullable = true) TenantBrandAssetDto logoDark,
    @Schema(nullable = true) TenantBrandAssetDto iconDark,
    @Schema(nullable = true) String logoUrl,
    @Schema(nullable = true) String iconUrl,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean canManage) {}
