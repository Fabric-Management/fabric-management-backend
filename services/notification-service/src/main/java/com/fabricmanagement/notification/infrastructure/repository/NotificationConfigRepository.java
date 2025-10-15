package com.fabricmanagement.notification.infrastructure.repository;

import com.fabricmanagement.notification.domain.aggregate.NotificationConfig;
import com.fabricmanagement.notification.domain.valueobject.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Notification Config Repository
 * 
 * Database access for tenant notification configurations.
 * 
 * Query Optimization:
 * - findAllNonDeleted() → DB-level filtering (no stream)
 * - Tenant-specific queries with indexes
 * - Enabled-only queries for active configs
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Repository
public interface NotificationConfigRepository extends JpaRepository<NotificationConfig, UUID> {
    
    /**
     * Find all non-deleted configs (performance optimized)
     */
    @Query("SELECT nc FROM NotificationConfig nc WHERE nc.deleted = false")
    List<NotificationConfig> findAllNonDeleted();
    
    /**
     * Find active config for tenant and channel
     * Returns highest priority (lowest number) enabled config
     */
    @Query("""
        SELECT nc FROM NotificationConfig nc 
        WHERE nc.tenantId = :tenantId 
        AND nc.channel = :channel 
        AND nc.isEnabled = true 
        AND nc.deleted = false 
        ORDER BY nc.priority ASC
        """)
    Optional<NotificationConfig> findActiveConfigByTenantAndChannel(
        @Param("tenantId") UUID tenantId,
        @Param("channel") NotificationChannel channel
    );
    
    /**
     * Find all enabled configs for tenant (all channels)
     */
    @Query("""
        SELECT nc FROM NotificationConfig nc 
        WHERE nc.tenantId = :tenantId 
        AND nc.isEnabled = true 
        AND nc.deleted = false 
        ORDER BY nc.priority ASC
        """)
    List<NotificationConfig> findEnabledConfigsByTenant(@Param("tenantId") UUID tenantId);
    
    /**
     * Find all configs for tenant (including disabled)
     */
    @Query("""
        SELECT nc FROM NotificationConfig nc 
        WHERE nc.tenantId = :tenantId 
        AND nc.deleted = false 
        ORDER BY nc.channel, nc.priority ASC
        """)
    List<NotificationConfig> findAllByTenant(@Param("tenantId") UUID tenantId);
    
    /**
     * Check if tenant has any enabled config
     */
    @Query("""
        SELECT COUNT(nc) > 0 FROM NotificationConfig nc 
        WHERE nc.tenantId = :tenantId 
        AND nc.isEnabled = true 
        AND nc.deleted = false
        """)
    boolean hasEnabledConfig(@Param("tenantId") UUID tenantId);
    
    /**
     * Check if tenant has config for specific channel
     */
    @Query("""
        SELECT COUNT(nc) > 0 FROM NotificationConfig nc 
        WHERE nc.tenantId = :tenantId 
        AND nc.channel = :channel 
        AND nc.deleted = false
        """)
    boolean hasConfigForChannel(
        @Param("tenantId") UUID tenantId,
        @Param("channel") NotificationChannel channel
    );
    
    /**
     * Find by ID and tenant (tenant-isolation check)
     */
    @Query("""
        SELECT nc FROM NotificationConfig nc 
        WHERE nc.id = :id 
        AND nc.tenantId = :tenantId 
        AND nc.deleted = false
        """)
    Optional<NotificationConfig> findByIdAndTenant(
        @Param("id") UUID id,
        @Param("tenantId") UUID tenantId
    );
}

