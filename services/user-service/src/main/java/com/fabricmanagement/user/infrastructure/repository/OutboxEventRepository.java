package com.fabricmanagement.user.infrastructure.repository;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Outbox Event Repository
 * 
 * Handles persistence of outbox events for guaranteed delivery pattern
 * 
 * ✅ Uses shared OutboxEvent from shared-domain (ZERO DUPLICATION)
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    
    /**
     * Find unprocessed events ordered by creation time (FIFO)
     * Used by background publisher to send events to Kafka
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.processed = false ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingEvents(Pageable pageable);
    
    /**
     * Find events by processed status
     */
    List<OutboxEvent> findByProcessed(Boolean processed);
    
    /**
     * Count pending events (for monitoring)
     */
    long countByProcessed(Boolean processed);
}

