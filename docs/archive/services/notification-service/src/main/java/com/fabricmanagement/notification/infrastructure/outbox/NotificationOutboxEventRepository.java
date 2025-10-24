package com.fabricmanagement.notification.infrastructure.outbox;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import com.fabricmanagement.shared.infrastructure.outbox.OutboxEventRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Notification Service OutboxEvent Repository
 * 
 * Implements the shared OutboxEventRepository interface for notification service.
 * Each service has its own outbox table but uses the same interface.
 */
@Repository
public interface NotificationOutboxEventRepository extends OutboxEventRepository {
    
    /**
     * Find notification events by notification ID
     */
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE e.aggregateType = 'NOTIFICATION' 
        AND e.aggregateId = :notificationId
        ORDER BY e.occurredAt ASC
        """)
    List<OutboxEvent> findByNotificationId(@Param("notificationId") UUID notificationId);
    
    /**
     * Find notification events by tenant
     */
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE e.aggregateType = 'NOTIFICATION' 
        AND e.tenantId = :tenantId
        AND e.status = 'NEW'
        ORDER BY e.occurredAt ASC
        """)
    List<OutboxEvent> findNotificationEventsByTenant(@Param("tenantId") UUID tenantId, Pageable pageable);
}
