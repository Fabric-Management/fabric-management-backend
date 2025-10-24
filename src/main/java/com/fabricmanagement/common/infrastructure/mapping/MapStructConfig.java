package com.fabricmanagement.common.infrastructure.mapping;

import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

/**
 * Global MapStruct configuration for all mappers.
 *
 * <p>Defines common mapping behavior across the entire application.
 * All mappers should reference this configuration via @Mapper(config = MapStructConfig.class)</p>
 *
 * <h2>Configuration Details:</h2>
 * <ul>
 *   <li><b>componentModel = "spring":</b> Generate Spring beans (@Component)</li>
 *   <li><b>unmappedTargetPolicy = ERROR:</b> Fail on unmapped fields (safety)</li>
 *   <li><b>unmappedSourcePolicy = WARN:</b> Warn on unmapped source fields</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @Mapper(config = MapStructConfig.class)
 * public interface MaterialMapper {
 *     MaterialDto toDto(Material entity);
 *     Material toEntity(CreateMaterialRequest request);
 *     
 *     @Mapping(target = "id", ignore = true)
 *     @Mapping(target = "tenantId", ignore = true)
 *     void updateEntity(@MappingTarget Material entity, UpdateMaterialRequest request);
 * }
 * }</pre>
 *
 * <h2>Best Practices:</h2>
 * <ul>
 *   <li>Always map to DTOs, never expose entities directly</li>
 *   <li>Use @Mapping to handle special cases explicitly</li>
 *   <li>Ignore infrastructure fields (id, tenantId, audit fields) when updating</li>
 *   <li>Use @AfterMapping for complex post-processing</li>
 * </ul>
 */
@MapperConfig(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    unmappedSourcePolicy = ReportingPolicy.WARN
)
public interface MapStructConfig {
}

