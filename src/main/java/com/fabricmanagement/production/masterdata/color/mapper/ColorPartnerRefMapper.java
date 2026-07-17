package com.fabricmanagement.production.masterdata.color.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.production.masterdata.color.domain.ColorPartnerCode;
import com.fabricmanagement.production.masterdata.color.domain.ColorPartnerRef;
import com.fabricmanagement.production.masterdata.color.dto.ColorPartnerCodeDto;
import com.fabricmanagement.production.masterdata.color.dto.ColorPartnerRefDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface ColorPartnerRefMapper {

  @Mapping(target = "active", expression = "java(Boolean.TRUE.equals(ref.getIsActive()))")
  @Mapping(
      target = "version",
      expression = "java(ref.getVersion() == null ? 0L : ref.getVersion())")
  ColorPartnerRefDto toDto(ColorPartnerRef ref);

  @Mapping(target = "active", expression = "java(Boolean.TRUE.equals(code.getIsActive()))")
  ColorPartnerCodeDto toDto(ColorPartnerCode code);
}
