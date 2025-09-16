package com.fabricmanagement.common.core.application.mapper;

import com.fabricmanagement.common.core.application.dto.BaseDto;
import com.fabricmanagement.common.core.domain.base.BaseEntity;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * Base mapper interface providing common mapping operations.
 * All entity mappers should extend this interface.
 *
 * @param <E> Entity type
 * @param <D> DTO type
 */
public interface BaseMapper<E extends BaseEntity, D extends BaseDto> {

    /**
     * Maps entity to DTO.
     *
     * @param entity the entity to map
     * @return the mapped DTO
     */
    D toDto(E entity);

    /**
     * Maps DTO to entity.
     *
     * @param dto the DTO to map
     * @return the mapped entity
     */
    E toEntity(D dto);

    /**
     * Maps list of entities to list of DTOs.
     *
     * @param entities the entities to map
     * @return the mapped DTOs
     */
    List<D> toDtoList(List<E> entities);

    /**
     * Maps list of DTOs to list of entities.
     *
     * @param dtos the DTOs to map
     * @return the mapped entities
     */
    List<E> toEntityList(List<D> dtos);

    /**
     * Updates existing entity with values from DTO.
     *
     * @param dto the DTO with new values
     * @param entity the entity to update
     */
    void updateEntityFromDto(D dto, @MappingTarget E entity);
}
