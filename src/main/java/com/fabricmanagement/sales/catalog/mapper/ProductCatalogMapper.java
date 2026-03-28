package com.fabricmanagement.sales.catalog.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.sales.catalog.domain.ProductCatalog;
import com.fabricmanagement.sales.catalog.dto.CreateProductCatalogRequest;
import com.fabricmanagement.sales.catalog.dto.ProductCatalogDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface ProductCatalogMapper {

  ProductCatalogDto toDto(ProductCatalog entity);

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
  ProductCatalog toEntity(CreateProductCatalogRequest request);
}
