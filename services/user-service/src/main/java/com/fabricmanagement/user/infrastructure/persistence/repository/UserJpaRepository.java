package com.fabricmanagement.user.infrastructure.persistence.repository;

import com.fabricmanagement.user.domain.model.UserStatus;
import com.fabricmanagement.user.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository interface for UserEntity.
 * Provides database access methods for user profile data.
 */
@Repository
public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    /**
     * Finds a user by ID and tenant ID.
     */
    Optional<UserEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Finds all active users for a tenant with pagination.
     */
    @Query("SELECT u FROM UserEntity u WHERE u.tenantId = :tenantId AND u.status = 'ACTIVE' AND u.deleted = false")
    Page<UserEntity> findActiveUsersByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    /**
     * Finds users by status for a tenant.
     */
    List<UserEntity> findByTenantIdAndStatusAndDeletedFalse(UUID tenantId, UserStatus status);

    /**
     * Finds users by department within a tenant.
     */
    List<UserEntity> findByTenantIdAndDepartmentAndDeletedFalse(UUID tenantId, String department);

    /**
     * Searches users by name or job title within a tenant.
     */
    @Query("""
        SELECT u FROM UserEntity u
        WHERE u.tenantId = :tenantId
        AND u.deleted = false
        AND (
            LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchQuery, '%')) OR
            LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchQuery, '%')) OR
            LOWER(u.displayName) LIKE LOWER(CONCAT('%', :searchQuery, '%')) OR
            LOWER(u.jobTitle) LIKE LOWER(CONCAT('%', :searchQuery, '%'))
        )
        """)
    Page<UserEntity> searchUsers(@Param("tenantId") UUID tenantId,
                                @Param("searchQuery") String searchQuery,
                                Pageable pageable);

    /**
     * Checks if a user exists by ID and tenant.
     */
    boolean existsByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    /**
     * Counts active users in a tenant.
     */
    @Query("SELECT COUNT(u) FROM UserEntity u WHERE u.tenantId = :tenantId AND u.status = 'ACTIVE' AND u.deleted = false")
    long countActiveUsersByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * Finds all users in a tenant (including inactive) with pagination.
     */
    Page<UserEntity> findByTenantIdAndDeletedFalse(UUID tenantId, Pageable pageable);
}

