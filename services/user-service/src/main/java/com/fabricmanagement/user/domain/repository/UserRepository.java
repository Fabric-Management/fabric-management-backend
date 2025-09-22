package com.fabricmanagement.user.domain.repository;

import com.fabricmanagement.user.domain.model.User;
import com.fabricmanagement.user.domain.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain repository interface for User entity.
 * Defines contract for user data access operations.
 * Implementation will be in infrastructure layer.
 */
public interface UserRepository {

    /**
     * Saves a user entity.
     */
    User save(User user);

    /**
     * Finds a user by ID within the current tenant context.
     */
    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Finds a user by ID (without tenant filtering - for admin operations).
     */
    Optional<User> findById(UUID id);

    /**
     * Finds all active users for a tenant with pagination.
     */
    Page<User> findActiveUsersByTenantId(UUID tenantId, Pageable pageable);

    /**
     * Finds users by status for a tenant.
     */
    List<User> findByTenantIdAndStatus(UUID tenantId, UserStatus status);

    /**
     * Finds users by department within a tenant.
     */
    List<User> findByTenantIdAndDepartment(UUID tenantId, String department);

    /**
     * Searches users by name or job title within a tenant.
     */
    Page<User> searchUsers(UUID tenantId, String searchQuery, Pageable pageable);

    /**
     * Checks if a user exists by ID and tenant.
     */
    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Counts active users in a tenant.
     */
    long countActiveUsersByTenantId(UUID tenantId);

    /**
     * Soft deletes a user (sets deleted flag to true).
     */
    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Finds all users in a tenant (including inactive).
     */
    Page<User> findByTenantId(UUID tenantId, Pageable pageable);
}

