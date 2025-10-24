package com.fabricmanagement.user.infrastructure.outbox;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import com.fabricmanagement.shared.infrastructure.outbox.OutboxEventRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * User Service OutboxEvent Repository
 * 
 * Implements the shared OutboxEventRepository interface for user service.
 * Each service has its own outbox table but uses the same interface.
 */
@Repository
public interface UserOutboxEventRepository extends OutboxEventRepository {
    
    /**
     * Find user events by user ID
     */
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE e.aggregateType = 'USER' 
        AND e.aggregateId = :userId
        ORDER BY e.occurredAt ASC
        """)
    List<OutboxEvent> findByUserId(@Param("userId") UUID userId);
    
    /**
     * Find user events by tenant
     */
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE e.aggregateType = 'USER' 
        AND e.tenantId = :tenantId
        AND e.status = 'NEW'
        ORDER BY e.occurredAt ASC
        """)
    List<OutboxEvent> findUserEventsByTenant(@Param("tenantId") UUID tenantId, Pageable pageable);
}