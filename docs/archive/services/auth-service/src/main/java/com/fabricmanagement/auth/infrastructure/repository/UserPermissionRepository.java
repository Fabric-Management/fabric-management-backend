package com.fabricmanagement.auth.infrastructure.repository;

import com.fabricmanagement.auth.domain.aggregate.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * User Permission Repository
 * 
 * Repository for UserPermission aggregate
 */
@Repository
public interface UserPermissionRepository extends JpaRepository<UserPermission, UUID> {
    
    /**
     * Find permissions by user ID
     */
    List<UserPermission> findByUserId(UUID userId);
    
    /**
     * Find permissions by user ID and tenant
     */
    List<UserPermission> findByUserIdAndTenantId(UUID userId, UUID tenantId);
    
    /**
     * Find permissions by user ID and resource type
     */
    List<UserPermission> findByUserIdAndResourceType(UUID userId, String resourceType);
    
    /**
     * Find permissions by user ID, resource type and resource ID
     */
    List<UserPermission> findByUserIdAndResourceTypeAndResourceId(UUID userId, String resourceType, UUID resourceId);
    
    /**
     * Find global permissions by user ID (no resource type)
     */
    @Query("SELECT up FROM UserPermission up WHERE up.userId = :userId AND up.resourceType IS NULL")
    List<UserPermission> findGlobalPermissionsByUserId(@Param("userId") UUID userId);
    
    /**
     * Check if user has permission
     */
    boolean existsByUserIdAndPermissionName(UUID userId, String permissionName);
    
    /**
     * Check if user has permission for specific resource
     */
    boolean existsByUserIdAndPermissionNameAndResourceTypeAndResourceId(
        UUID userId, String permissionName, String resourceType, UUID resourceId);
    
    /**
     * Delete permission from user
     */
    void deleteByUserIdAndPermissionNameAndResourceTypeAndResourceIdAndTenantId(
        UUID userId, String permissionName, String resourceType, UUID resourceId, UUID tenantId);
    
    /**
     * Find permissions by tenant ID
     */
    List<UserPermission> findByTenantId(UUID tenantId);
    
    /**
     * Find permission by user ID and permission name
     */
    java.util.Optional<UserPermission> findByUserIdAndPermissionName(UUID userId, String permissionName);
    
    /**
     * Check if user has permission in tenant
     */
    boolean existsByUserIdAndPermissionNameAndTenantId(UUID userId, String permissionName, UUID tenantId);
    
    /**
     * Delete permission by user ID and permission name
     */
    void deleteByUserIdAndPermissionName(UUID userId, String permissionName);
    
    /**
     * Delete all permissions by user ID
     */
    void deleteByUserId(UUID userId);
}
