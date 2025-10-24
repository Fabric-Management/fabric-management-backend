package com.fabricmanagement.user.infrastructure.repository;

import com.fabricmanagement.user.domain.aggregate.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Processed Event Repository
 * 
 * Handles idempotency checks for Kafka event consumers
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
    
    /**
     * Check if event has already been processed
     * 
     * Returns true if event_id exists → skip processing
     */
    boolean existsByEventId(UUID eventId);
}

