package com.fabricmanagement.auth.infrastructure.repository;

import com.fabricmanagement.auth.domain.aggregate.AuthUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Auth User Repository
 * 
 * Repository for AuthUser aggregate
 */
@Repository
public interface AuthUserRepository extends JpaRepository<AuthUser, UUID> {
    
    /**
     * Find user by contact value
     */
    Optional<AuthUser> findByContactValue(String contactValue);
    
    /**
     * Find user by contact value and tenant
     */
    Optional<AuthUser> findByContactValueAndTenantId(String contactValue, UUID tenantId);
    
    /**
     * Check if contact value exists
     */
    boolean existsByContactValue(String contactValue);
    
    /**
     * Check if contact value exists in tenant
     */
    boolean existsByContactValueAndTenantId(String contactValue, UUID tenantId);
    
    /**
     * Find active users by tenant
     */
    @Query("SELECT u FROM AuthUser u WHERE u.tenantId = :tenantId AND u.isActive = true")
    java.util.List<AuthUser> findActiveUsersByTenant(@Param("tenantId") UUID tenantId);
    
    /**
     * Find locked users by tenant
     */
    @Query("SELECT u FROM AuthUser u WHERE u.tenantId = :tenantId AND u.isLocked = true")
    java.util.List<AuthUser> findLockedUsersByTenant(@Param("tenantId") UUID tenantId);
    
    /**
     * Find users by tenant ID
     */
    java.util.List<AuthUser> findByTenantId(UUID tenantId);
}
