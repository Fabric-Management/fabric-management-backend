package com.fabricmanagement.production.masterdata.color.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.production.masterdata.color.domain.Color;
import com.fabricmanagement.production.masterdata.color.dto.ColorDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface ColorMapper {

  @Mapping(target = "active", expression = "java(Boolean.TRUE.equals(color.getIsActive()))")
  @Mapping(target = "pantoneLabel", expression = "java(toPantoneLabel(color))")
  ColorDto toDto(Color color);

  default String toPantoneLabel(Color color) {
    if (color.getPantoneCode() == null) {
      return null;
    }
    return color.getPantoneSystem() == null
        ? color.getPantoneCode()
        : color.getPantoneCode() + " " + color.getPantoneSystem().name();
  }
}
