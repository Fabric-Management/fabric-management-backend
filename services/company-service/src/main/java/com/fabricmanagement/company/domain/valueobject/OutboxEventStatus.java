package com.fabricmanagement.company.domain.valueobject;

/**
 * Outbox Event Status
 * 
 * Lifecycle:
 * PENDING → Event created, waiting to be sent to Kafka
 * PUBLISHED → Successfully sent to Kafka
 * FAILED → Failed after max retries (requires manual intervention)
 */
public enum OutboxEventStatus {
    PENDING,    // Not yet sent to Kafka
    PUBLISHED,  // Successfully sent to Kafka
    FAILED      // Failed after retries
}

