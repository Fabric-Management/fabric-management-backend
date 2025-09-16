package com.fabricmanagement.common.core.application.service;

import com.fabricmanagement.common.core.application.dto.BaseDto;
import com.fabricmanagement.common.core.application.dto.PageRequest;
import com.fabricmanagement.common.core.application.dto.PageResponse;
import com.fabricmanagement.common.core.domain.base.BaseEntity;

import java.util.List;
import java.util.Optional;

/**
 * Base service interface providing common CRUD operations.
 * All service interfaces should extend this interface.
 *
 * @param <D> DTO type
 * @param <ID> ID type
 */
public interface BaseService<D extends BaseDto, ID> {

    /**
     * Creates a new entity.
     *
     * @param dto the DTO to create
     * @return the created DTO
     */
    D create(D dto);

    /**
     * Updates an existing entity.
     *
     * @param id the entity ID
     * @param dto the DTO with updated values
     * @return the updated DTO
     */
    D update(ID id, D dto);

    /**
     * Finds an entity by ID.
     *
     * @param id the entity ID
     * @return the DTO if found
     */
    Optional<D> findById(ID id);

    /**
     * Gets an entity by ID, throwing exception if not found.
     *
     * @param id the entity ID
     * @return the DTO
     * @throws com.fabricmanagement.common.core.domain.exception.EntityNotFoundException if not found
     */
    D getById(ID id);

    /**
     * Finds all entities.
     *
     * @return list of all DTOs
     */
    List<D> findAll();

    /**
     * Finds entities with pagination and filtering.
     *
     * @param pageRequest the page request with filters and sorting
     * @return paginated response
     */
    PageResponse<D> findAll(PageRequest pageRequest);

    /**
     * Soft deletes an entity by ID.
     *
     * @param id the entity ID
     */
    void deleteById(ID id);

    /**
     * Checks if entity exists by ID.
     *
     * @param id the entity ID
     * @return true if exists
     */
    boolean existsById(ID id);

    /**
     * Counts all entities.
     *
     * @return total count
     */
    long count();
}
