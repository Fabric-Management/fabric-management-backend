package com.fabricmanagement.contact.infrastructure.outbox;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import com.fabricmanagement.shared.infrastructure.outbox.OutboxEventRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Contact Service OutboxEvent Repository
 * 
 * Implements the shared OutboxEventRepository interface for contact service.
 * Each service has its own outbox table but uses the same interface.
 */
@Repository
public interface ContactOutboxEventRepository extends OutboxEventRepository {
    
    /**
     * Find contact events by contact ID
     */
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE e.aggregateType = 'CONTACT' 
        AND e.aggregateId = :contactId
        ORDER BY e.occurredAt ASC
        """)
    List<OutboxEvent> findByContactId(@Param("contactId") UUID contactId);
    
    /**
     * Find contact events by tenant
     */
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE e.aggregateType = 'CONTACT' 
        AND e.tenantId = :tenantId
        AND e.status = 'NEW'
        ORDER BY e.occurredAt ASC
        """)
    List<OutboxEvent> findContactEventsByTenant(@Param("tenantId") UUID tenantId, Pageable pageable);
}
