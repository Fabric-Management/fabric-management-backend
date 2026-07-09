package com.fabricmanagement.production.masterdata.color.domain;

import java.math.BigDecimal;
import lombok.Builder;

/**
 * The full mutable state of a colour card, passed as one value so create and update cannot drift
 * apart. Normalisation and cross-field rules live in {@link Color}, not here.
 *
 * <p>{@code standardStatus} is deliberately absent: a card is born {@code DRAFT} and changes state
 * only through {@link Color#approve()} / {@link Color#revertToDraft()}. Sign-off is a transition,
 * not a field you can set while editing something else.
 *
 * <p>The Lab fields are the <em>target</em> standard. Measured Lab belongs to a batch or a lab dip.
 */
@Builder
public record ColorCardSpec(
    String code,
    String name,
    String colorHex,
    ColorType colorType,
    String pantoneCode,
    PantoneSystem pantoneSystem,
    ColorFamily colorFamily,
    BigDecimal targetLabL,
    BigDecimal targetLabA,
    BigDecimal targetLabB,
    LabIlluminant targetLabIlluminant,
    LabObserver targetLabObserver,
    BigDecimal deltaETolerance,
    DeltaEFormula deltaEFormula,
    String notes) {

  /** Minimal card: the three fields that existed before shade standards were modelled. */
  public static ColorCardSpec basic(String code, String name, String colorHex) {
    return ColorCardSpec.builder().code(code).name(name).colorHex(colorHex).build();
  }
}
