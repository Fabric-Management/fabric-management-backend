package com.fabricmanagement.company.infrastructure.messaging;

import com.fabricmanagement.company.domain.aggregate.OutboxEvent;
import com.fabricmanagement.company.domain.valueobject.OutboxEventStatus;
import com.fabricmanagement.company.infrastructure.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Outbox Event Publisher
 * 
 * Background worker that polls outbox table and sends events to Kafka
 * 
 * Pattern: Transactional Outbox (Google/Amazon/Netflix standard)
 * Guarantees: At-least-once delivery to Kafka
 * 
 * How it works:
 * 1. Poll outbox table for PENDING events
 * 2. Send to Kafka
 * 3. Mark as PUBLISHED if successful
 * 4. Mark as FAILED if failed after retries
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {
    
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * Poll and publish pending events every 5 seconds
     * 
     * Scheduled task runs in background to guarantee event delivery
     */
    @Scheduled(fixedDelayString = "${outbox.publisher.poll-interval:5000}") // 5 seconds
    @Transactional
    public void publishPendingEvents() {
        int batchSize = 100; // Process 100 events per batch
        
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents(
            OutboxEventStatus.PENDING,
            PageRequest.of(0, batchSize)
        );
        
        if (pendingEvents.isEmpty()) {
            return; // No pending events
        }
        
        log.info("📤 Outbox Publisher: Found {} pending events to publish", pendingEvents.size());
        
        for (OutboxEvent event : pendingEvents) {
            try {
                publishEvent(event);
            } catch (Exception e) {
                log.error("❌ Failed to publish outbox event: {}", event.getId(), e);
                handlePublishFailure(event, e.getMessage());
            }
        }
    }
    
    /**
     * Publish single event to Kafka
     */
    private void publishEvent(OutboxEvent event) {
        log.debug("Publishing outbox event: {} to topic: {}", event.getId(), event.getTopic());
        
        // Send to Kafka (async)
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
            event.getTopic(),
            event.getAggregateId().toString(),
            event.getPayload()
        );
        
        // Handle result
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                // Success → mark as PUBLISHED
                event.markAsPublished();
                outboxEventRepository.save(event);
                log.info("✅ Outbox event published successfully: {} (topic: {})", event.getId(), event.getTopic());
            } else {
                // Failure → mark as FAILED or retry
                handlePublishFailure(event, ex.getMessage());
            }
        });
    }
    
    /**
     * Handle publish failure
     */
    private void handlePublishFailure(OutboxEvent event, String errorMessage) {
        if (event.canRetry()) {
            log.warn("⚠️ Outbox event publish failed, will retry: {} (retry count: {})", 
                event.getId(), event.getRetryCount() + 1);
            event.setRetryCount(event.getRetryCount() + 1);
            event.setErrorMessage(errorMessage);
        } else {
            log.error("❌ Outbox event publish FAILED after max retries: {}", event.getId());
            event.markAsFailed(errorMessage);
        }
        outboxEventRepository.save(event);
    }
    
    /**
     * Monitor failed events (for alerting)
     * Runs every hour
     */
    @Scheduled(fixedDelayString = "${outbox.publisher.monitor-interval:3600000}") // 1 hour
    public void monitorFailedEvents() {
        long failedCount = outboxEventRepository.countByStatus(OutboxEventStatus.FAILED);
        if (failedCount > 0) {
            log.error("🚨 ALERT: {} outbox events in FAILED state - manual intervention required!", failedCount);
        }
    }
}

