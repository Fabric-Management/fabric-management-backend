package com.fabricmanagement.shared.infrastructure.policy.repository;

import com.fabricmanagement.shared.domain.policy.OperationType;
import com.fabricmanagement.shared.domain.policy.PermissionType;
import com.fabricmanagement.shared.domain.policy.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * UserPermission Repository
 * 
 * Repository for user-specific permission grants/denies.
 * 
 * @author Fabric Management Team
 * @since 2.0 (Policy Authorization)
 */
@Repository
public interface UserPermissionRepository extends JpaRepository<UserPermission, UUID> {
    
    /**
     * Find all effective (active and not expired) permissions for user
     * 
     * @param userId user ID
     * @param now current time
     * @return list of effective permissions
     */
    @Query("""
        SELECT p FROM UserPermission p 
        WHERE p.userId = :userId 
        AND p.status = 'ACTIVE'
        AND p.deleted = false
        AND (p.validFrom IS NULL OR p.validFrom <= :now)
        AND (p.validUntil IS NULL OR p.validUntil > :now)
        """)
    List<UserPermission> findEffectivePermissionsForUser(
        @Param("userId") UUID userId, 
        @Param("now") LocalDateTime now
    );
    
    /**
     * Find effective permissions for specific endpoint and operation
     * 
     * @param userId user ID
     * @param endpoint API endpoint
     * @param operation operation type
     * @param now current time
     * @return list of matching permissions
     */
    @Query("""
        SELECT p FROM UserPermission p 
        WHERE p.userId = :userId 
        AND p.endpoint = :endpoint
        AND p.operation = :operation
        AND p.status = 'ACTIVE'
        AND p.deleted = false
        AND (p.validFrom IS NULL OR p.validFrom <= :now)
        AND (p.validUntil IS NULL OR p.validUntil > :now)
        ORDER BY p.permissionType DESC
        """)
    List<UserPermission> findEffectivePermissionsForEndpoint(
        @Param("userId") UUID userId,
        @Param("endpoint") String endpoint,
        @Param("operation") OperationType operation,
        @Param("now") LocalDateTime now
    );
    
    /**
     * Find DENY permissions for user on endpoint
     * 
     * @param userId user ID
     * @param endpoint API endpoint
     * @param operation operation type
     * @param now current time
     * @return list of DENY permissions
     */
    @Query("""
        SELECT p FROM UserPermission p 
        WHERE p.userId = :userId 
        AND p.endpoint = :endpoint
        AND p.operation = :operation
        AND p.permissionType = 'DENY'
        AND p.status = 'ACTIVE'
        AND p.deleted = false
        AND (p.validFrom IS NULL OR p.validFrom <= :now)
        AND (p.validUntil IS NULL OR p.validUntil > :now)
        """)
    List<UserPermission> findDenyPermissions(
        @Param("userId") UUID userId,
        @Param("endpoint") String endpoint,
        @Param("operation") OperationType operation,
        @Param("now") LocalDateTime now
    );
    
    /**
     * Find ALLOW permissions for user on endpoint
     * 
     * @param userId user ID
     * @param endpoint API endpoint
     * @param operation operation type
     * @param now current time
     * @return list of ALLOW permissions
     */
    @Query("""
        SELECT p FROM UserPermission p 
        WHERE p.userId = :userId 
        AND p.endpoint = :endpoint
        AND p.operation = :operation
        AND p.permissionType = 'ALLOW'
        AND p.status = 'ACTIVE'
        AND p.deleted = false
        AND (p.validFrom IS NULL OR p.validFrom <= :now)
        AND (p.validUntil IS NULL OR p.validUntil > :now)
        """)
    List<UserPermission> findAllowPermissions(
        @Param("userId") UUID userId,
        @Param("endpoint") String endpoint,
        @Param("operation") OperationType operation,
        @Param("now") LocalDateTime now
    );
    
    /**
     * Find expired permissions that need cleanup
     * 
     * @param now current time
     * @return list of expired permissions
     */
    @Query("""
        SELECT p FROM UserPermission p 
        WHERE p.status = 'ACTIVE'
        AND p.validUntil IS NOT NULL
        AND p.validUntil < :now
        """)
    List<UserPermission> findExpiredPermissions(@Param("now") LocalDateTime now);
    
    /**
     * Find all permissions for user (for admin UI)
     * 
     * @param userId user ID
     * @return list of all permissions
     */
    @Query("""
        SELECT p FROM UserPermission p 
        WHERE p.userId = :userId 
        AND p.deleted = false
        """)
    List<UserPermission> findByUserId(@Param("userId") UUID userId);
    
    /**
     * Find permissions by type
     * 
     * @param userId user ID
     * @param permissionType ALLOW or DENY
     * @return list of permissions
     */
    @Query("""
        SELECT p FROM UserPermission p 
        WHERE p.userId = :userId 
        AND p.permissionType = :permissionType
        AND p.deleted = false
        """)
    List<UserPermission> findByUserIdAndPermissionType(
        @Param("userId") UUID userId, 
        @Param("permissionType") PermissionType permissionType
    );
    
    /**
     * Find all non-deleted permissions
     * Used for admin operations
     * 
     * @return list of all active permissions
     */
    @Query("SELECT p FROM UserPermission p WHERE p.deleted = false")
    List<UserPermission> findAllNonDeleted();
}

