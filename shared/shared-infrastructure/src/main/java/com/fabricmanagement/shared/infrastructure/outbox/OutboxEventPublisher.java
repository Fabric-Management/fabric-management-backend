package com.fabricmanagement.shared.infrastructure.outbox;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox Event Publisher
 * 
 * Polls outbox table every 5s and publishes to Kafka.
 * Simple and reliable event delivery.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {
    
    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxRepository.findRetryableEvents();
        
        if (events.isEmpty()) return;
        
        log.debug("📤 Publishing {} events", events.size());
        
        for (OutboxEvent event : events.subList(0, Math.min(events.size(), 100))) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload());
                event.markAsProcessed();
                outboxRepository.save(event);
            } catch (Exception e) {
                event.recordFailure(e.getMessage());
                outboxRepository.save(event);
                log.error("❌ Publish failed: {}", e.getMessage());
            }
        }
    }
    
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOldEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        List<OutboxEvent> oldEvents = outboxRepository.findProcessedEventsBefore(cutoff);
        
        if (!oldEvents.isEmpty()) {
            outboxRepository.deleteAll(oldEvents);
            log.info("🗑️ Cleaned {} old events", oldEvents.size());
        }
    }
}

