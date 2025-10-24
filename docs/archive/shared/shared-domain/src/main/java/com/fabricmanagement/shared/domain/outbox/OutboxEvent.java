package com.fabricmanagement.shared.domain.outbox;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * OutboxEvent Entity
 * 
 * Implements the Outbox Pattern for reliable event publishing.
 * Events are stored in the database within the same transaction as business data,
 * then published to Kafka by a separate process.
 * 
 * This ensures exactly-once delivery semantics.
 * 
 * Pattern: "Schema local, standard global."
 * - Each service has its own outbox_events table
 * - Shared model ensures consistency across services
 * - Zero hardcoded values, all config-driven
 */
@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_outbox_status_occurred", columnList = "status,occurredAt"),
    @Index(name = "idx_outbox_aggregate", columnList = "aggregateType,aggregateId"),
    @Index(name = "idx_outbox_tenant", columnList = "tenantId"),
    @Index(name = "idx_outbox_trace", columnList = "traceId")
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
     * Type of aggregate (USER, COMPANY, ORDER, TASK, FIBER)
     */
    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;
    
    /**
     * ID of the aggregate
     */
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;
    
    /**
     * Event type (UserCreated, CompanyUpdated, OrderCompleted)
     */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;
    
    /**
     * Event version (semantic versioning)
     */
    @Column(name = "event_version", nullable = false, length = 20)
    @Builder.Default
    private String eventVersion = "1.0";
    
    /**
     * Event payload as JSON (DTO)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;
    
    /**
     * When the event occurred (UTC)
     */
    @Column(name = "occurred_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
    
    /**
     * Tenant ID for multi-tenant isolation
     */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    /**
     * Trace ID for distributed tracing
     */
    @Column(name = "trace_id", length = 100)
    private String traceId;
    
    /**
     * Correlation ID for request tracking
     */
    @Column(name = "correlation_id", length = 100)
    private String correlationId;
    
    /**
     * Event headers (key/value pairs)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", columnDefinition = "jsonb")
    private Map<String, String> headers;
    
    /**
     * Event status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OutboxEventStatus status = OutboxEventStatus.NEW;
    
    /**
     * Number of publish attempts
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;
    
    /**
     * Last error message if publishing failed
     */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
    
    /**
     * When the event was published to Kafka
     */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
    
    /**
     * Marks event as being published
     */
    public void markAsPublishing() {
        this.status = OutboxEventStatus.PUBLISHING;
    }
    
    /**
     * Marks event as published successfully
     */
    public void markAsPublished() {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }
    
    /**
     * Marks event as failed and increments retry count
     */
    public void markAsFailed(String errorMessage) {
        this.status = OutboxEventStatus.FAILED;
        this.retryCount++;
        this.lastError = errorMessage;
    }
    
    /**
     * Check if event can be retried
     */
    public boolean canRetry(int maxRetries) {
        return this.retryCount < maxRetries && this.status != OutboxEventStatus.PUBLISHED;
    }
    
    /**
     * Reset status for retry
     */
    public void resetForRetry() {
        this.status = OutboxEventStatus.NEW;
        this.lastError = null;
    }
}
