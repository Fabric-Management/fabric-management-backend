package com.fabricmanagement.shared.domain.outbox;

/**
 * OutboxEvent Status Enum
 * 
 * Represents the lifecycle states of an outbox event.
 */
public enum OutboxEventStatus {
    
    /**
     * Event created but not yet processed
     */
    NEW,
    
    /**
     * Event is being published to Kafka
     */
    PUBLISHING,
    
    /**
     * Event successfully published to Kafka
     */
    PUBLISHED,
    
    /**
     * Event failed to publish after retries
     */
    FAILED
}
