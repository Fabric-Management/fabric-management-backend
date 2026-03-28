package com.fabricmanagement.sales.sample.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.sales.sample.domain.SampleDelivery;
import com.fabricmanagement.sales.sample.domain.SampleRequest;
import com.fabricmanagement.sales.sample.dto.CreateSampleRequestDto;
import com.fabricmanagement.sales.sample.dto.SampleDeliveryDto;
import com.fabricmanagement.sales.sample.dto.SampleRequestDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface SampleMapper {

  SampleRequestDto toDto(SampleRequest entity);

  SampleDeliveryDto toDto(SampleDelivery entity);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "uid", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "isActive", ignore = true)
  @Mapping(target = "active", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "offlineMetadata", ignore = true)
  SampleRequest toEntity(CreateSampleRequestDto request);
}
