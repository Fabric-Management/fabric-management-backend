package com.fabricmanagement.notification.infrastructure.repository;

import com.fabricmanagement.notification.domain.entity.NotificationLog;
import com.fabricmanagement.notification.domain.valueobject.NotificationChannel;
import com.fabricmanagement.notification.domain.valueobject.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Notification Log Repository
 * 
 * Tracks all notification delivery attempts.
 * Used for monitoring, analytics, and debugging.
 * 
 * Performance:
 * - Indexes on tenant_id, status, created_at
 * - Pagination support for large datasets
 * - Time-range queries for analytics
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
    
    /**
     * Find log by event ID (idempotency check)
     */
    @Query("SELECT nl FROM NotificationLog nl WHERE nl.eventId = :eventId AND nl.deleted = false")
    Optional<NotificationLog> findByEventId(@Param("eventId") String eventId);
    
    /**
     * Check if event already processed (idempotency)
     */
    @Query("SELECT COUNT(nl) > 0 FROM NotificationLog nl WHERE nl.eventId = :eventId AND nl.deleted = false")
    boolean existsByEventId(@Param("eventId") String eventId);
    
    /**
     * Find logs by tenant (paginated)
     */
    @Query("""
        SELECT nl FROM NotificationLog nl 
        WHERE nl.tenantId = :tenantId 
        AND nl.deleted = false 
        ORDER BY nl.createdAt DESC
        """)
    Page<NotificationLog> findByTenant(@Param("tenantId") UUID tenantId, Pageable pageable);
    
    /**
     * Find logs by tenant and status (paginated)
     */
    @Query("""
        SELECT nl FROM NotificationLog nl 
        WHERE nl.tenantId = :tenantId 
        AND nl.status = :status 
        AND nl.deleted = false 
        ORDER BY nl.createdAt DESC
        """)
    Page<NotificationLog> findByTenantAndStatus(
        @Param("tenantId") UUID tenantId,
        @Param("status") NotificationStatus status,
        Pageable pageable
    );
    
    /**
     * Find logs by tenant and channel (paginated)
     */
    @Query("""
        SELECT nl FROM NotificationLog nl 
        WHERE nl.tenantId = :tenantId 
        AND nl.channel = :channel 
        AND nl.deleted = false 
        ORDER BY nl.createdAt DESC
        """)
    Page<NotificationLog> findByTenantAndChannel(
        @Param("tenantId") UUID tenantId,
        @Param("channel") NotificationChannel channel,
        Pageable pageable
    );
    
    /**
     * Find logs by recipient (cross-tenant admin view)
     */
    @Query("""
        SELECT nl FROM NotificationLog nl 
        WHERE nl.recipient = :recipient 
        AND nl.deleted = false 
        ORDER BY nl.createdAt DESC
        """)
    Page<NotificationLog> findByRecipient(@Param("recipient") String recipient, Pageable pageable);
    
    /**
     * Find failed notifications for retry
     */
    @Query("""
        SELECT nl FROM NotificationLog nl 
        WHERE nl.status = 'FAILED' 
        AND nl.attempts < :maxAttempts 
        AND nl.deleted = false 
        ORDER BY nl.createdAt ASC
        """)
    List<NotificationLog> findFailedForRetry(@Param("maxAttempts") int maxAttempts);
    
    /**
     * Find logs in time range (analytics)
     */
    @Query("""
        SELECT nl FROM NotificationLog nl 
        WHERE nl.tenantId = :tenantId 
        AND nl.createdAt BETWEEN :startDate AND :endDate 
        AND nl.deleted = false 
        ORDER BY nl.createdAt DESC
        """)
    List<NotificationLog> findByTenantAndDateRange(
        @Param("tenantId") UUID tenantId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    /**
     * Count notifications by status (dashboard metrics)
     */
    @Query("""
        SELECT COUNT(nl) FROM NotificationLog nl 
        WHERE nl.tenantId = :tenantId 
        AND nl.status = :status 
        AND nl.deleted = false
        """)
    Long countByTenantAndStatus(
        @Param("tenantId") UUID tenantId,
        @Param("status") NotificationStatus status
    );
    
    /**
     * Count notifications by channel (analytics)
     */
    @Query("""
        SELECT COUNT(nl) FROM NotificationLog nl 
        WHERE nl.tenantId = :tenantId 
        AND nl.channel = :channel 
        AND nl.deleted = false
        """)
    Long countByTenantAndChannel(
        @Param("tenantId") UUID tenantId,
        @Param("channel") NotificationChannel channel
    );
}

