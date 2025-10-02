package com.fabricmanagement.shared.domain.outbox;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outbox Event Entity
 * 
 * Implements the Outbox Pattern for reliable event publishing.
 * Events are stored in the database within the same transaction as business data,
 * then published to Kafka by a separate process.
 * 
 * This ensures exactly-once delivery semantics.
 */
@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_outbox_processed", columnList = "processed, createdAt"),
    @Index(name = "idx_outbox_aggregate", columnList = "aggregateType, aggregateId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * Type of aggregate (User, Company, Contact, etc.)
     */
    @Column(nullable = false, length = 100)
    private String aggregateType;
    
    /**
     * ID of the aggregate
     */
    @Column(nullable = false)
    private String aggregateId;
    
    /**
     * Event type (UserCreated, CompanyUpdated, etc.)
     */
    @Column(nullable = false, length = 100)
    private String eventType;
    
    /**
     * Event payload as JSON
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;
    
    /**
     * When the event was created
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * Whether the event has been processed (published to Kafka)
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean processed = false;
    
    /**
     * When the event was processed
     */
    private LocalDateTime processedAt;
    
    /**
     * Number of processing attempts
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;
    
    /**
     * Last error message if processing failed
     */
    @Column(columnDefinition = "TEXT")
    private String lastError;
    
    /**
     * Tenant ID for multi-tenancy
     */
    @Column(length = 100)
    private String tenantId;
    
    /**
     * Kafka topic to publish to
     */
    @Column(nullable = false, length = 100)
    private String topic;
    
    /**
     * Marks event as processed
     */
    public void markAsProcessed() {
        this.processed = true;
        this.processedAt = LocalDateTime.now();
    }
    
    /**
     * Increments attempt counter and records error
     */
    public void recordFailure(String error) {
        this.attempts++;
        this.lastError = error;
    }
}

