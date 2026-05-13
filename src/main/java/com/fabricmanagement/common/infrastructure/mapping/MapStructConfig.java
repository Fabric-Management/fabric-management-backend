package com.fabricmanagement.common.infrastructure.mapping;

import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

/**
 * Global MapStruct configuration for all mappers.
 *
 * <p>Defines common mapping behavior across the entire application. All mappers should reference
 * this configuration via @Mapper(config = MapStructConfig.class)
 *
 * <h2>Configuration Details:</h2>
 *
 * <ul>
 *   <li><b>componentModel = "spring":</b> Generate Spring beans (@Component)
 *   <li><b>unmappedTargetPolicy = ERROR:</b> Fail on unmapped fields (safety)
 *   <li><b>unmappedSourcePolicy = IGNORE:</b> Ignore unmapped source fields to prevent warning spam
 * </ul>
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * @Mapper(config = MapStructConfig.class)
 * public interface ProductMapper {
 *     ProductDto toDto(Product entity);
 *     Product toEntity(CreateProductRequest request);
 *
 *     @Mapping(target = "id", ignore = true)
 *     @Mapping(target = "tenantId", ignore = true)
 *     void updateEntity(@MappingTarget Product entity, UpdateProductRequest request);
 * }
 * }</pre>
 *
 * <h2>Best Practices:</h2>
 *
 * <ul>
 *   <li>Always map to DTOs, never expose entities directly
 *   <li>Use @Mapping to handle special cases explicitly
 *   <li>Ignore infrastructure fields (id, tenantId, audit fields) when updating
 *   <li>Use @AfterMapping for complex post-processing
 * </ul>
 */
@MapperConfig(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface MapStructConfig {}
