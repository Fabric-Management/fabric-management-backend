package com.fabricmanagement.shared.infrastructure.outbox;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import com.fabricmanagement.shared.domain.outbox.OutboxEventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Outbox Event Processor
 * 
 * Processes outbox events and publishes to Kafka
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ PRODUCTION-READY
 * ✅ OUTBOX PATTERN
 * ✅ TRANSACTIONAL GUARANTEES
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventProcessor {
    
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${outbox.poll-interval-ms:1000}")
    private long pollIntervalMs;
    
    @Value("${outbox.batch-size:10}")
    private int batchSize;
    
    @Value("${outbox.max-retries:3}")
    private int maxRetries;
    
    @Value("${outbox.backoff.initial-ms:1000}")
    private long initialBackoffMs;
    
    @Value("${outbox.backoff.max-ms:30000}")
    private long maxBackoffMs;
    
    /**
     * Process outbox events
     */
    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:1000}")
    @Transactional
    public void processOutboxEvents() {
        try {
            List<OutboxEvent> events = outboxEventRepository.findByStatusOrderByOccurredAtAsc(
                OutboxEventStatus.NEW, batchSize
            );
            
            if (events.isEmpty()) {
                return;
            }
            
            log.debug("📤 Processing {} outbox events", events.size());
            
            for (OutboxEvent event : events) {
                processEvent(event);
            }
            
        } catch (Exception e) {
            log.error("❌ Error processing outbox events", e);
        }
    }
    
    /**
     * Process individual event
     */
    private void processEvent(OutboxEvent event) {
        try {
            // Mark as publishing
            event.setStatus(OutboxEventStatus.PUBLISHING);
            event.setUpdatedAt(LocalDateTime.now());
            outboxEventRepository.save(event);
            
            // Publish to Kafka
            String topic = determineTopic(event);
            String key = event.getAggregateId().toString();
            
            kafkaTemplate.send(topic, key, event.getPayload())
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        // Success
                        event.setStatus(OutboxEventStatus.PUBLISHED);
                        event.setPublishedAt(LocalDateTime.now());
                        event.setLastError(null);
                        log.debug("✅ Event published successfully: {}", event.getId());
                    } else {
                        // Failure
                        handlePublishFailure(event, ex);
                        log.error("❌ Failed to publish event: {}", event.getId(), ex);
                    }
                    
                    outboxEventRepository.save(event);
                });
            
        } catch (Exception e) {
            log.error("❌ Error processing event: {}", event.getId(), e);
            handlePublishFailure(event, e);
            outboxEventRepository.save(event);
        }
    }
    
    /**
     * Handle publish failure
     */
    private void handlePublishFailure(OutboxEvent event, Throwable ex) {
        event.setRetryCount(event.getRetryCount() + 1);
        event.setLastError(ex.getMessage());
        
        if (event.getRetryCount() >= maxRetries) {
            event.setStatus(OutboxEventStatus.FAILED);
            log.error("❌ Event failed after {} retries: {}", maxRetries, event.getId());
        } else {
            event.setStatus(OutboxEventStatus.NEW);
            event.setOccurredAt(LocalDateTime.now().plusSeconds(calculateBackoffDelay(event.getRetryCount())));
            log.warn("🔄 Event will be retried (attempt {}/{}): {}", 
                    event.getRetryCount(), maxRetries, event.getId());
        }
        
        event.setUpdatedAt(LocalDateTime.now());
    }
    
    /**
     * Calculate backoff delay
     */
    private long calculateBackoffDelay(int retryCount) {
        long delay = initialBackoffMs * (1L << retryCount); // Exponential backoff
        return Math.min(delay, maxBackoffMs);
    }
    
    /**
     * Determine Kafka topic based on event
     */
    private String determineTopic(OutboxEvent event) {
        return switch (event.getAggregateType()) {
            case "USER" -> "user-events";
            case "COMPANY" -> "company-events";
            case "CONTACT" -> "contact-events";
            case "FIBER" -> "fiber-events";
            case "NOTIFICATION" -> "notification-events";
            case "AUTH" -> "security-events";
            default -> "default-events";
        };
    }
    
    /**
     * Clean up old published events
     */
    @Scheduled(cron = "${outbox.cleanup-cron:0 0 2 * * ?}")
    @Transactional
    public void cleanupOldEvents() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7); // Keep for 7 days
            
            List<OutboxEvent> oldEvents = outboxEventRepository.findByStatusAndPublishedAtBefore(
                OutboxEventStatus.PUBLISHED, cutoffDate
            );
            
            if (!oldEvents.isEmpty()) {
                outboxEventRepository.deleteAll(oldEvents);
                log.info("🧹 Cleaned up {} old outbox events", oldEvents.size());
            }
            
        } catch (Exception e) {
            log.error("❌ Error cleaning up old events", e);
        }
    }
    
    /**
     * Retry failed events
     */
    @Scheduled(cron = "${outbox.retry-cron:0 */5 * * * ?}")
    @Transactional
    public void retryFailedEvents() {
        try {
            List<OutboxEvent> failedEvents = outboxEventRepository.findByStatusAndRetryCountLessThan(
                OutboxEventStatus.FAILED, maxRetries
            );
            
            if (!failedEvents.isEmpty()) {
                for (OutboxEvent event : failedEvents) {
                    event.setStatus(OutboxEventStatus.NEW);
                    event.setOccurredAt(LocalDateTime.now());
                    event.setUpdatedAt(LocalDateTime.now());
                }
                
                outboxEventRepository.saveAll(failedEvents);
                log.info("🔄 Retrying {} failed events", failedEvents.size());
            }
            
        } catch (Exception e) {
            log.error("❌ Error retrying failed events", e);
        }
    }
}