package com.fabricmanagement.company.infrastructure.repository;

import com.fabricmanagement.company.domain.policy.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * User Permission Repository
 * 
 * Manages user-specific permission grants
 */
@Repository
public interface UserPermissionRepository extends JpaRepository<UserPermission, UUID> {
    
    @Query("SELECT p FROM UserPermission p WHERE p.userId = :userId AND p.status = 'ACTIVE'")
    List<UserPermission> findActiveByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT p FROM UserPermission p WHERE p.userId = :userId AND p.endpoint = :endpoint AND p.status = 'ACTIVE'")
    List<UserPermission> findActiveByUserIdAndEndpoint(@Param("userId") UUID userId, 
                                                        @Param("endpoint") String endpoint);
    
    @Query("SELECT p FROM UserPermission p WHERE p.validUntil < :now AND p.status = 'ACTIVE'")
    List<UserPermission> findExpired(@Param("now") LocalDateTime now);
    
    @Query("SELECT COUNT(p) FROM UserPermission p WHERE p.status = :status")
    long countByStatus(@Param("status") String status);
}

