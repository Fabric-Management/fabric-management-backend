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
 * Pantone codes are deliberately not regex-validated: the numbering is not a single shape, and a
 * strict pattern would reject valid references. Cross-field rules live in the domain.
 *
 * <p>No {@code standardStatus}: a new card is always DRAFT.
 */
public record CreateColorRequest(
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
    @Schema(description = "Screen approximation only; never an authoritative colour standard")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
        String colorHex,
    @Schema(description = "Process classification; defaults to DYED when omitted")
        ColorType colorType,
    @Schema(
            description =
                "Optional external Pantone reference; trimmed and normalized to uppercase",
            maxLength = 20)
        @Size(max = 20)
        String pantoneCode,
    @Schema(description = "Pantone carrier system; defaults to TCX when a code is supplied")
        PantoneSystem pantoneSystem,
    @Schema(description = "Visual family; defaults to UNDEFINED when omitted")
        ColorFamily colorFamily,
    @Schema(description = "Target CIE Lab lightness L*, from 0 to 100; not a measured batch value")
        @DecimalMin("0")
        @DecimalMax("100")
        BigDecimal targetLabL,
    @Schema(description = "Target CIE Lab green-red axis a*, from -128 to 127")
        @DecimalMin("-128")
        @DecimalMax("127")
        BigDecimal targetLabA,
    @Schema(description = "Target CIE Lab blue-yellow axis b*, from -128 to 127")
        @DecimalMin("-128")
        @DecimalMax("127")
        BigDecimal targetLabB,
    @Schema(description = "Illuminant defining the target Lab values; supplied with all Lab fields")
        LabIlluminant targetLabIlluminant,
    @Schema(
            description =
                "Observer angle defining the target Lab values; supplied with all Lab fields")
        LabObserver targetLabObserver,
    @Schema(description = "Maximum accepted Delta-E; requires a target and a formula")
        @Positive
        @DecimalMax("99.99")
        BigDecimal deltaETolerance,
    @Schema(description = "Delta-E calculation formula; valid only with a tolerance")
        DeltaEFormula deltaEFormula,
    @Schema(description = "Optional internal notes", maxLength = 1000) @Size(max = 1000)
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
