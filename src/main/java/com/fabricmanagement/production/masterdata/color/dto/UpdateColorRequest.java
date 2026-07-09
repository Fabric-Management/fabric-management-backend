package com.fabricmanagement.production.masterdata.color.dto;

import com.fabricmanagement.production.masterdata.color.domain.ColorCardSpec;
import com.fabricmanagement.production.masterdata.color.domain.ColorFamily;
import com.fabricmanagement.production.masterdata.color.domain.ColorType;
import com.fabricmanagement.production.masterdata.color.domain.DeltaEFormula;
import com.fabricmanagement.production.masterdata.color.domain.LabIlluminant;
import com.fabricmanagement.production.masterdata.color.domain.LabObserver;
import com.fabricmanagement.production.masterdata.color.domain.PantoneSystem;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Full replacement of the card's mutable state: an absent field clears its column.
 *
 * <p>No {@code standardStatus}: sign-off moves through {@code /approve} and {@code
 * /revert-to-draft}, never as a side effect of an edit. While APPROVED, the standard-defining
 * fields are frozen.
 */
public record UpdateColorRequest(
    @NotBlank @Size(max = 50) String code,
    @NotBlank @Size(max = 255) String name,
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String colorHex,
    ColorType colorType,
    @Size(max = 20) String pantoneCode,
    PantoneSystem pantoneSystem,
    ColorFamily colorFamily,
    @DecimalMin("0") @DecimalMax("100") BigDecimal targetLabL,
    @DecimalMin("-128") @DecimalMax("127") BigDecimal targetLabA,
    @DecimalMin("-128") @DecimalMax("127") BigDecimal targetLabB,
    LabIlluminant targetLabIlluminant,
    LabObserver targetLabObserver,
    @Positive @DecimalMax("99.99") BigDecimal deltaETolerance,
    DeltaEFormula deltaEFormula,
    @Size(max = 1000) String notes) {

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
