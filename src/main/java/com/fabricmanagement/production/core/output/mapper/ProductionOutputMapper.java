package com.fabricmanagement.production.core.output.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.production.core.output.domain.ProductionOutputItem;
import com.fabricmanagement.production.core.output.domain.ProductionOutputRecord;
import com.fabricmanagement.production.core.output.dto.ProductionOutputDto;
import com.fabricmanagement.production.core.output.dto.ProductionOutputItemDto;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface ProductionOutputMapper {

  ProductionOutputDto toDto(ProductionOutputRecord record);

  ProductionOutputItemDto toItemDto(ProductionOutputItem item);
}
