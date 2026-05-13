package com.fabricmanagement.sales.salesproduct.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.sales.salesproduct.domain.SalesProduct;
import com.fabricmanagement.sales.salesproduct.dto.CreateSalesProductRequest;
import com.fabricmanagement.sales.salesproduct.dto.SalesProductDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface SalesProductMapper {

  SalesProductDto toDto(SalesProduct entity);

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
  SalesProduct toEntity(CreateSalesProductRequest request);
}
