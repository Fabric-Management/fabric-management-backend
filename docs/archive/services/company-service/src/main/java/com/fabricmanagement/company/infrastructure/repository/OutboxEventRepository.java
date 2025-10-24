package com.fabricmanagement.company.infrastructure.repository;

import com.fabricmanagement.company.domain.aggregate.OutboxEvent;
import com.fabricmanagement.company.domain.valueobject.OutboxEventStatus;
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
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    
    /**
     * Find pending events ordered by creation time (FIFO)
     * Used by background publisher to send events to Kafka
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = :status ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingEvents(OutboxEventStatus status, Pageable pageable);
    
    /**
     * Find failed events for monitoring/alerting
     */
    List<OutboxEvent> findByStatus(OutboxEventStatus status);
    
    /**
     * Count pending events (for monitoring)
     */
    long countByStatus(OutboxEventStatus status);
}

