package com.fabricmanagement.production.masterdata.color.dto;

import com.fabricmanagement.production.masterdata.color.domain.Color;
import java.util.UUID;

public record ColorDto(UUID id, String code, String name, String colorHex, boolean active) {

  public static ColorDto from(Color color) {
    return new ColorDto(
        color.getId(),
        color.getCode(),
        color.getName(),
        color.getColorHex(),
        Boolean.TRUE.equals(color.getIsActive()));
  }
}
