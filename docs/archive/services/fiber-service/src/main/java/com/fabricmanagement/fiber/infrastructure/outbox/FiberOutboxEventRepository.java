package com.fabricmanagement.fiber.infrastructure.outbox;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import com.fabricmanagement.shared.infrastructure.outbox.OutboxEventRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Fiber Service OutboxEvent Repository
 * 
 * Implements the shared OutboxEventRepository interface for fiber service.
 * Each service has its own outbox table but uses the same interface.
 */
@Repository
public interface FiberOutboxEventRepository extends OutboxEventRepository {
    
    /**
     * Find fiber events by fiber ID
     */
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE e.aggregateType = 'FIBER' 
        AND e.aggregateId = :fiberId
        ORDER BY e.occurredAt ASC
        """)
    List<OutboxEvent> findByFiberId(@Param("fiberId") UUID fiberId);
    
    /**
     * Find fiber events by tenant
     */
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE e.aggregateType = 'FIBER' 
        AND e.tenantId = :tenantId
        AND e.status = 'NEW'
        ORDER BY e.occurredAt ASC
        """)
    List<OutboxEvent> findFiberEventsByTenant(@Param("tenantId") UUID tenantId, Pageable pageable);
}
