package com.fabricmanagement.platform.tenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateTenantSettingsRequest(
    @NotBlank @Size(max = 50) String timezone,
    @NotBlank @Size(max = 20) String locale,
    @NotBlank @Size(min = 3, max = 3) String currency,
    @Schema(description = "Country code (ISO 3166-1 alpha-2)", example = "TR")
        @Size(min = 2, max = 2)
        String country,
    @Schema(
            description = "Cost variance threshold (fractional, e.g. 0.10 for 10%)",
            example = "0.10",
            nullable = true)
        @jakarta.validation.constraints.DecimalMin(
            value = "0.01",
            message = "Variance threshold must be at least 1%")
        @jakarta.validation.constraints.DecimalMax(
            value = "1.00",
            message = "Variance threshold cannot exceed 100%")
        BigDecimal costVarianceThreshold,
    @Schema(description = "Enable beta features", example = "false") boolean betaFeaturesEnabled,
    boolean aiEnabled,
    boolean emailNotificationsEnabled,
    @Size(max = 255) String logoUrl,
    @Size(max = 7) String primaryColor,
    boolean mfaRequired,
    @Min(15) @Max(10080) int sessionTimeoutMinutes,
    String[] allowedIpRanges) {}
