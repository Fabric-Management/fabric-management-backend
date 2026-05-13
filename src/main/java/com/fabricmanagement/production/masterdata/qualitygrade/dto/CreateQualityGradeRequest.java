package com.fabricmanagement.production.masterdata.qualitygrade.dto;

import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateQualityGradeRequest(
    @NotNull ProductType productType,
    @NotBlank @Size(max = 10) String code,
    @NotBlank @Size(max = 255) String name,
    @Min(1) int rank,
    @NotNull @DecimalMin("0.000") @DecimalMax("9.999") BigDecimal priceFactor,
    boolean saleable,
    boolean requiresApproval,
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String colorHex,
    boolean isDefault) {}
