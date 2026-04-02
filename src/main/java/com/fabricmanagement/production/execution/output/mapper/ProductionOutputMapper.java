package com.fabricmanagement.production.execution.output.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.production.execution.output.domain.ProductionOutputItem;
import com.fabricmanagement.production.execution.output.domain.ProductionOutputRecord;
import com.fabricmanagement.production.execution.output.dto.ProductionOutputDto;
import com.fabricmanagement.production.execution.output.dto.ProductionOutputItemDto;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface ProductionOutputMapper {

  ProductionOutputDto toDto(ProductionOutputRecord record);

  ProductionOutputItemDto toItemDto(ProductionOutputItem item);
}
