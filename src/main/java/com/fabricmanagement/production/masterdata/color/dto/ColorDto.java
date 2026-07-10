package com.fabricmanagement.production.masterdata.color.dto;

import com.fabricmanagement.production.masterdata.color.domain.Color;
import com.fabricmanagement.production.masterdata.color.domain.ColorFamily;
import com.fabricmanagement.production.masterdata.color.domain.ColorStandardStatus;
import com.fabricmanagement.production.masterdata.color.domain.ColorType;
import com.fabricmanagement.production.masterdata.color.domain.DeltaEFormula;
import com.fabricmanagement.production.masterdata.color.domain.LabIlluminant;
import com.fabricmanagement.production.masterdata.color.domain.LabObserver;
import com.fabricmanagement.production.masterdata.color.domain.PantoneSystem;
import java.math.BigDecimal;
import java.util.UUID;

public record ColorDto(
    UUID id,
    String code,
    String name,
    String colorHex,
    ColorType colorType,
    String pantoneCode,
    PantoneSystem pantoneSystem,
    /** Ready-to-display reference, e.g. "19-4024 TCX". Null when no Pantone is set. */
    String pantoneLabel,
    ColorFamily colorFamily,
    BigDecimal targetLabL,
    BigDecimal targetLabA,
    BigDecimal targetLabB,
    LabIlluminant targetLabIlluminant,
    LabObserver targetLabObserver,
    BigDecimal deltaETolerance,
    DeltaEFormula deltaEFormula,
    ColorStandardStatus standardStatus,
    String notes,
    boolean active) {

  public static ColorDto from(Color color) {
    return new ColorDto(
        color.getId(),
        color.getCode(),
        color.getName(),
        color.getColorHex(),
        color.getColorType(),
        color.getPantoneCode(),
        color.getPantoneSystem(),
        pantoneLabel(color),
        color.getColorFamily(),
        color.getTargetLabL(),
        color.getTargetLabA(),
        color.getTargetLabB(),
        color.getTargetLabIlluminant(),
        color.getTargetLabObserver(),
        color.getDeltaETolerance(),
        color.getDeltaEFormula(),
        color.getStandardStatus(),
        color.getNotes(),
        Boolean.TRUE.equals(color.getIsActive()));
  }

  private static String pantoneLabel(Color color) {
    if (color.getPantoneCode() == null) {
      return null;
    }
    return color.getPantoneSystem() == null
        ? color.getPantoneCode()
        : color.getPantoneCode() + " " + color.getPantoneSystem().name();
  }
}
