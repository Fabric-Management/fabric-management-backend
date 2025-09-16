package com.fabricmanagement.common.core.infrastructure.persistence;

import com.fabricmanagement.common.core.domain.base.BaseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;

/**
 * Base repository interface providing common database operations.
 * All entity repositories should extend this interface.
 *
 * @param <T> Entity type
 * @param <ID> ID type
 */
@NoRepositoryBean
public interface BaseRepository<T extends BaseEntity, ID> extends
    JpaRepository<T, ID>, JpaSpecificationExecutor<T> {

    /**
     * Finds all non-deleted entities.
     *
     * @return list of active entities
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.deleted = false")
    List<T> findAllActive();

    /**
     * Finds all non-deleted entities with pagination.
     *
     * @param pageable pagination information
     * @return page of active entities
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.deleted = false")
    Page<T> findAllActive(Pageable pageable);

    /**
     * Finds an active entity by ID.
     *
     * @param id the entity ID
     * @return optional entity if found and not deleted
     */
    @Query("SELECT e FROM #{#entityName} e WHERE e.id = :id AND e.deleted = false")
    Optional<T> findActiveById(ID id);

    /**
     * Checks if an active entity exists by ID.
     *
     * @param id the entity ID
     * @return true if exists and not deleted
     */
    @Query("SELECT COUNT(e) > 0 FROM #{#entityName} e WHERE e.id = :id AND e.deleted = false")
    boolean existsActiveById(ID id);

    /**
     * Counts all active entities.
     *
     * @return count of non-deleted entities
     */
    @Query("SELECT COUNT(e) FROM #{#entityName} e WHERE e.deleted = false")
    long countActive();

    /**
     * Soft deletes an entity by ID.
     *
     * @param id the entity ID
     */
    default void softDeleteById(ID id) {
        findById(id).ifPresent(entity -> {
            entity.markAsDeleted();
            save(entity);
        });
    }
}
