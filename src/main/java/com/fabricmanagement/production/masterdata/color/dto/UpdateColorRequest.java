package com.fabricmanagement.production.masterdata.color.dto;

import com.fabricmanagement.production.masterdata.color.domain.ColorCardSpec;
import com.fabricmanagement.production.masterdata.color.domain.ColorFamily;
import com.fabricmanagement.production.masterdata.color.domain.ColorType;
import com.fabricmanagement.production.masterdata.color.domain.DeltaEFormula;
import com.fabricmanagement.production.masterdata.color.domain.LabIlluminant;
import com.fabricmanagement.production.masterdata.color.domain.LabObserver;
import com.fabricmanagement.production.masterdata.color.domain.PantoneSystem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Full replacement of the card's mutable state. Code and name are required; omitted nullable fields
 * clear their values, while omitted type and family resolve to their documented domain defaults.
 *
 * <p>No {@code standardStatus}: sign-off moves through {@code /approve} and {@code
 * /revert-to-draft}, never as a side effect of an edit. While APPROVED, the standard-defining
 * fields are frozen.
 */
@Schema(
    description =
        "Full replacement of mutable color-card state. Code and name are required; omitted nullable values are cleared, colorType defaults to DYED, and colorFamily defaults to UNDEFINED. standardStatus is changed only through transition endpoints.")
public record UpdateColorRequest(
    @Schema(
            description = "Tenant-unique code; trimmed and normalized to uppercase",
            requiredMode = Schema.RequiredMode.REQUIRED,
            maxLength = 50)
        @NotBlank
        @Size(max = 50)
        String code,
    @Schema(
            description = "Human-readable color-card name; stored trimmed",
            requiredMode = Schema.RequiredMode.REQUIRED,
            maxLength = 255)
        @NotBlank
        @Size(max = 255)
        String name,
    @Schema(
            description =
                "Screen approximation only; omission or null clears the stored approximation",
            nullable = true)
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
        String colorHex,
    @Schema(
            description = "Process classification; omission or null resolves to DYED",
            nullable = true)
        ColorType colorType,
    @Schema(
            description =
                "External Pantone reference; omission, null, or blank clears it; stored trimmed and uppercase",
            nullable = true,
            maxLength = 20)
        @Size(max = 20)
        String pantoneCode,
    @Schema(
            description =
                "Pantone carrier system; omission or null defaults to TCX when pantoneCode is supplied, otherwise it is cleared",
            nullable = true)
        PantoneSystem pantoneSystem,
    @Schema(description = "Visual family; omission or null resolves to UNDEFINED", nullable = true)
        ColorFamily colorFamily,
    @Schema(
            description =
                "Target CIE Lab lightness L*, from 0 to 100; omit or null all five target fields to clear the target",
            nullable = true)
        @DecimalMin("0")
        @DecimalMax("100")
        BigDecimal targetLabL,
    @Schema(
            description =
                "Target CIE Lab green-red axis a*, from -128 to 127; supplied with all target fields",
            nullable = true)
        @DecimalMin("-128")
        @DecimalMax("127")
        BigDecimal targetLabA,
    @Schema(
            description =
                "Target CIE Lab blue-yellow axis b*, from -128 to 127; supplied with all target fields",
            nullable = true)
        @DecimalMin("-128")
        @DecimalMax("127")
        BigDecimal targetLabB,
    @Schema(
            description =
                "Illuminant defining the target Lab values; supplied with all target fields",
            nullable = true)
        LabIlluminant targetLabIlluminant,
    @Schema(
            description =
                "Observer angle defining the target Lab values; supplied with all target fields",
            nullable = true)
        LabObserver targetLabObserver,
    @Schema(
            description =
                "Maximum accepted Delta-E; omission or null clears both tolerance and formula",
            nullable = true)
        @Positive
        @DecimalMax("99.99")
        BigDecimal deltaETolerance,
    @Schema(
            description =
                "Delta-E formula; omission or null defaults to CMC_2_1 when a tolerance is supplied and is cleared otherwise",
            nullable = true)
        DeltaEFormula deltaEFormula,
    @Schema(
            description = "Optional internal notes; omission, null, or blank clears notes",
            nullable = true,
            maxLength = 1000)
        @Size(max = 1000)
        String notes) {

  public ColorCardSpec toSpec() {
    return new ColorCardSpec(
        code,
        name,
        colorHex,
        colorType,
        pantoneCode,
        pantoneSystem,
        colorFamily,
        targetLabL,
        targetLabA,
        targetLabB,
        targetLabIlluminant,
        targetLabObserver,
        deltaETolerance,
        deltaEFormula,
        notes);
  }
}
