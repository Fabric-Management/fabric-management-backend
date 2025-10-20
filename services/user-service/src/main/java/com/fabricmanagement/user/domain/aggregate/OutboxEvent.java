package com.fabricmanagement.user.domain.aggregate;

import com.fabricmanagement.user.domain.valueobject.OutboxEventStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outbox Event Entity
 * 
 * Transactional Outbox Pattern implementation for guaranteed event delivery
 * 
 * Pattern: Write event to DB in same transaction as business logic,
 * then async publisher sends to Kafka
 */
@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_outbox_events_status_created", columnList = "status,created_at"),
    @Index(name = "idx_outbox_events_aggregate", columnList = "aggregate_type,aggregate_id"),
    @Index(name = "idx_outbox_events_tenant", columnList = "tenant_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;  // "USER", "COMPANY", etc.
    
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;  // userId, companyId, etc.
    
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;  // "UserCreatedEvent", "UserUpdatedEvent", etc.
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;  // Event data as JSON
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OutboxEventStatus status = OutboxEventStatus.PENDING;
    
    @Column(name = "topic", nullable = false, length = 100)
    private String topic;  // Kafka topic name
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
    
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    /**
     * Mark event as published
     */
    public void markAsPublished() {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }
    
    /**
     * Mark event as failed
     */
    public void markAsFailed(String errorMessage) {
        this.status = OutboxEventStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryCount++;
    }
    
    /**
     * Check if event can be retried
     */
    public boolean canRetry() {
        return this.retryCount < 3 && this.status != OutboxEventStatus.PUBLISHED;
    }
}

