package com.fabricmanagement.user.domain.aggregate;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Processed Event Entity
 * 
 * Idempotency check for Kafka event consumers
 * 
 * Pattern: Event Deduplication (Google/Amazon/Netflix standard)
 * Prevents duplicate event processing (at-least-once → exactly-once)
 */
@Entity
@Table(name = "processed_events", indexes = {
    @Index(name = "idx_processed_events_event_id", columnList = "event_id", unique = true),
    @Index(name = "idx_processed_events_processed_at", columnList = "processed_at"),
    @Index(name = "idx_processed_events_tenant", columnList = "tenant_id"),
    @Index(name = "idx_processed_events_aggregate", columnList = "event_type,aggregate_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {
    
    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;  // Unique event ID from source service
    
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;  // e.g., "CompanyCreatedEvent"
    
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;  // Entity ID (companyId, contactId)
    
    @Column(name = "processed_at", nullable = false)
    @Builder.Default
    private LocalDateTime processedAt = LocalDateTime.now();
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Column(name = "source_service", nullable = false, length = 50)
    private String sourceService;  // e.g., "company-service"
    
    @Column(name = "kafka_topic", nullable = false, length = 100)
    private String kafkaTopic;
    
    @Column(name = "kafka_partition")
    private Integer kafkaPartition;
    
    @Column(name = "kafka_offset")
    private Long kafkaOffset;
}

