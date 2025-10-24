package com.fabricmanagement.user.infrastructure.repository;

import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
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
 * User Repository Interface
 * 
 * Provides data access methods for User aggregate
 * Note: Contact-related queries removed - use ContactServiceClient instead
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    /**
     * Find users by tenant ID
     */
    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND u.deleted = false")
    List<User> findByTenantId(@Param("tenantId") UUID tenantId);
    
    /**
     * Find users by status
     */
    @Query("SELECT u FROM User u WHERE u.status = :status AND u.deleted = false")
    List<User> findByStatus(@Param("status") String status);
    
    /**
     * Find users by registration type
     */
    @Query("SELECT u FROM User u WHERE u.registrationType = :registrationType AND u.deleted = false")
    List<User> findByRegistrationType(@Param("registrationType") String registrationType);
    
    /**
     * Search users by name (first name or last name contains)
     */
    @Query("SELECT u FROM User u WHERE (LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "AND u.deleted = false")
    List<User> searchByName(@Param("searchTerm") String searchTerm);
    
    /**
     * Count active users by tenant
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.tenantId = :tenantId AND u.status = 'ACTIVE' AND u.deleted = false")
    long countActiveUsersByTenant(@Param("tenantId") UUID tenantId);
    
    /**
     * Find user by ID and tenant ID
     */
    @Query("SELECT u FROM User u WHERE u.id = :userId AND u.tenantId = :tenantId AND u.deleted = false")
    Optional<User> findByIdAndTenantId(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);
    
    /**
     * Find active (non-deleted) user by ID and tenant ID
     * 
     * This is a convenience method that combines the most common query pattern.
     * Replaces the repeated code pattern:
     * findById(id).filter(u -> !u.isDeleted()).filter(u -> u.getTenantId().equals(tenantId))
     */
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.tenantId = :tenantId AND u.deleted = false")
    Optional<User> findActiveByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
    
    /**
     * Find users by tenant ID with pagination
     */
    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND u.deleted = false")
    Page<User> findByTenantIdPaginated(@Param("tenantId") UUID tenantId, Pageable pageable);
    
    /**
     * Search users by criteria with pagination
     * Dynamic query based on provided parameters (null parameters are ignored)
     */
    @Query("SELECT u FROM User u WHERE " +
           "u.tenantId = :tenantId AND u.deleted = false AND " +
           "(:firstName IS NULL OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :firstName, '%'))) AND " +
           "(:lastName IS NULL OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :lastName, '%'))) AND " +
           "(:status IS NULL OR u.status = CAST(:status AS com.fabricmanagement.user.domain.valueobject.UserStatus))")
    Page<User> searchUsersPaginated(
        @Param("tenantId") UUID tenantId,
        @Param("firstName") String firstName,
        @Param("lastName") String lastName,
        @Param("status") String status,
        Pageable pageable
    );
    
    /**
     * Batch get users by IDs
     * 
     * Prevents N+1 queries - uses IN (...) clause
     * 
     * ✅ Performance: 1 query vs N queries
     */
    @Query("SELECT u FROM User u WHERE u.id IN :userIds AND u.tenantId = :tenantId AND u.status = :status AND u.deleted = false")
    List<User> findAllByIdInAndTenantIdAndStatus(
        @Param("userIds") List<UUID> userIds,
        @Param("tenantId") UUID tenantId,
        @Param("status") UserStatus status
    );
}
