package com.fabricmanagement.common.platform.user.dto;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.common.platform.user.domain.UserNavPreferences;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct mapper for UserNavPreferences entity and nav preferences DTOs.
 *
 * <p>Uses {@link MapStructConfig} (unmappedTargetPolicy = ERROR). When mapping from request to
 * entity, all BaseEntity audit/infrastructure fields and {@code user} are ignored; only {@code
 * sortOrder} and {@code hiddenItemIds} are updated.
 */
@Mapper(config = MapStructConfig.class)
public interface NavPreferencesMapper {

  NavPreferencesResponse toResponse(UserNavPreferences entity);

  /**
   * Updates entity from PATCH request. Only {@code sortOrder} and {@code hiddenItemIds} are mapped
   * when non-null; all BaseEntity fields and {@code user} are ignored (set by persistence/context).
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "uid", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "isActive", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "user", ignore = true)
  @Mapping(
      target = "sortOrder",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(
      target = "hiddenItemIds",
      nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void updateEntityFromRequest(
      @MappingTarget UserNavPreferences entity, NavPreferencesRequest request);
}
