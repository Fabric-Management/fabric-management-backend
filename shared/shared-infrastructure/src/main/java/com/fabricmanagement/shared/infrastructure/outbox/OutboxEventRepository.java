package com.fabricmanagement.shared.infrastructure.outbox;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Outbox Event Repository
 * 
 * Provides data access for outbox events
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    
    /**
     * Finds unprocessed events ordered by creation time
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.processed = false ORDER BY e.createdAt ASC")
    List<OutboxEvent> findUnprocessedEvents();
    
    /**
     * Finds events that failed but can be retried
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.processed = false AND e.attempts < 5 ORDER BY e.createdAt ASC")
    List<OutboxEvent> findRetryableEvents();
    
    /**
     * Finds old processed events for cleanup
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.processed = true AND e.processedAt < :before")
    List<OutboxEvent> findProcessedEventsBefore(LocalDateTime before);
    
    /**
     * Counts unprocessed events
     */
    long countByProcessedFalse();
    
    /**
     * Finds events by aggregate
     */
    List<OutboxEvent> findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
        String aggregateType, 
        String aggregateId
    );
}

