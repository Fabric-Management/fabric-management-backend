package com.fabricmanagement.shared.infrastructure.outbox;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * OutboxEvent Repository
 * 
 * Repository for outbox events with optimized queries for the processor.
 * Each service implements this interface for its own outbox table.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    
    /**
     * Find new events ready for processing
     * Ordered by occurredAt for proper sequencing
     */
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE e.status = 'NEW' 
        ORDER BY e.occurredAt ASC
        """)
    List<OutboxEvent> findNewEvents(Pageable pageable);
    
    /**
     * Find new events ready for processing (with limit)
     */
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE e.status = 'NEW' 
        ORDER BY e.occurredAt ASC
        LIMIT :limit
        """)
    List<OutboxEvent> findNewEvents(@Param("limit") int limit);
    
    /**
     * Find events by aggregate for processing order
     */
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE e.aggregateType = :aggregateType 
        AND e.aggregateId = :aggregateId
        AND e.status IN ('NEW', 'PUBLISHING')
        ORDER BY e.occurredAt ASC
        """)
    List<OutboxEvent> findByAggregate(
        @Param("aggregateType") String aggregateType,
        @Param("aggregateId") UUID aggregateId
    );
    
    /**
     * Find published events before cutoff for cleanup
     */
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE e.status = 'PUBLISHED' 
        AND e.publishedAt < :cutoff
        """)
    List<OutboxEvent> findPublishedEventsBefore(@Param("cutoff") LocalDateTime cutoff);
    
    /**
     * Find failed events for monitoring
     */
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE e.status = 'FAILED'
        ORDER BY e.occurredAt DESC
        """)
    List<OutboxEvent> findFailedEvents(Pageable pageable);
    
    /**
     * Count events by status for metrics
     */
    @Query("""
        SELECT COUNT(e) FROM OutboxEvent e 
        WHERE e.status = :status
        """)
    long countByStatus(@Param("status") String status);
    
    /**
     * Find events by tenant for multi-tenant queries
     */
    @Query("""
        SELECT e FROM OutboxEvent e 
        WHERE e.tenantId = :tenantId
        AND e.status = 'NEW'
        ORDER BY e.occurredAt ASC
        """)
    List<OutboxEvent> findByTenantAndStatus(
        @Param("tenantId") UUID tenantId,
        Pageable pageable
    );
}
