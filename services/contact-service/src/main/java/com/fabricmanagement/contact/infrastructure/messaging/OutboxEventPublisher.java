package com.fabricmanagement.contact.infrastructure.messaging;

import com.fabricmanagement.contact.domain.aggregate.OutboxEvent;
import com.fabricmanagement.contact.domain.valueobject.OutboxEventStatus;
import com.fabricmanagement.contact.infrastructure.repository.OutboxEventRepository;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {
    
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Scheduled(fixedDelayString = "${outbox.publisher.poll-interval:5000}")
    @Transactional
    public void publishPendingEvents() {
        int batchSize = 100;
        
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents(
            OutboxEventStatus.PENDING,
            PageRequest.of(0, batchSize)
        );
        
        if (pendingEvents.isEmpty()) {
            return;
        }
        
        log.info("📤 Outbox Publisher: Found {} pending events", pendingEvents.size());
        
        for (OutboxEvent event : pendingEvents) {
            try {
                publishEvent(event);
            } catch (Exception e) {
                log.error("❌ Failed to publish outbox event: {}", event.getId(), e);
                handlePublishFailure(event, e.getMessage());
            }
        }
    }
    
    private void publishEvent(OutboxEvent event) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
            event.getTopic(),
            event.getAggregateId().toString(),
            event.getPayload()
        );
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                event.markAsPublished();
                outboxEventRepository.save(event);
                log.info("✅ Outbox event published: {} (topic: {})", event.getId(), event.getTopic());
            } else {
                handlePublishFailure(event, ex.getMessage());
            }
        });
    }
    
    private void handlePublishFailure(OutboxEvent event, String errorMessage) {
        if (event.canRetry()) {
            log.warn("⚠️ Outbox event failed, will retry: {} (retry: {})", event.getId(), event.getRetryCount() + 1);
            event.setRetryCount(event.getRetryCount() + 1);
            event.setErrorMessage(errorMessage);
        } else {
            log.error("❌ Outbox event FAILED after max retries: {}", event.getId());
            event.markAsFailed(errorMessage);
        }
        outboxEventRepository.save(event);
    }
    
    @Scheduled(fixedDelayString = "${outbox.publisher.monitor-interval:3600000}")
    public void monitorFailedEvents() {
        long failedCount = outboxEventRepository.countByStatus(OutboxEventStatus.FAILED);
        if (failedCount > 0) {
            log.error("🚨 ALERT: {} outbox events in FAILED state!", failedCount);
        }
    }
}

