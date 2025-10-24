package com.fabricmanagement.auth.infrastructure.repository;

import com.fabricmanagement.auth.domain.aggregate.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * User Role Repository
 * 
 * Repository for UserRole aggregate
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {
    
    /**
     * Find roles by user ID
     */
    List<UserRole> findByUserId(UUID userId);
    
    /**
     * Find roles by user ID and tenant
     */
    List<UserRole> findByUserIdAndTenantId(UUID userId, UUID tenantId);
    
    /**
     * Find users by role name
     */
    @Query("SELECT ur FROM UserRole ur WHERE ur.roleName = :roleName")
    List<UserRole> findByRoleName(@Param("roleName") String roleName);
    
    /**
     * Find users by role name and tenant
     */
    @Query("SELECT ur FROM UserRole ur WHERE ur.roleName = :roleName AND ur.tenantId = :tenantId")
    List<UserRole> findByRoleNameAndTenantId(@Param("roleName") String roleName, @Param("tenantId") UUID tenantId);
    
    /**
     * Check if user has role
     */
    boolean existsByUserIdAndRoleName(UUID userId, String roleName);
    
    /**
     * Check if user has role in tenant
     */
    boolean existsByUserIdAndRoleNameAndTenantId(UUID userId, String roleName, UUID tenantId);
    
    /**
     * Delete role from user
     */
    void deleteByUserIdAndRoleNameAndTenantId(UUID userId, String roleName, UUID tenantId);
    
    /**
     * Find roles by tenant ID
     */
    List<UserRole> findByTenantId(UUID tenantId);
    
    /**
     * Find role by user ID and role name
     */
    java.util.Optional<UserRole> findByUserIdAndRoleName(UUID userId, String roleName);
    
    /**
     * Delete role by user ID and role name
     */
    void deleteByUserIdAndRoleName(UUID userId, String roleName);
    
    /**
     * Delete all roles by user ID
     */
    void deleteByUserId(UUID userId);
}
