package com.fabricmanagement.user.infrastructure.messaging;

import com.fabricmanagement.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.user.domain.event.UserDeletedEvent;
import com.fabricmanagement.user.domain.event.UserUpdatedEvent;
import com.fabricmanagement.user.domain.event.PasswordResetRequestedEvent;
import com.fabricmanagement.user.domain.event.PasswordResetCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * User Event Publisher
 * 
 * Publishes user domain events to Kafka
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher {
    
    private static final String USER_EVENTS_TOPIC = "user-events";
    private static final String USER_CREATED_TOPIC = "user.created";
    private static final String USER_UPDATED_TOPIC = "user.updated";
    private static final String USER_DELETED_TOPIC = "user.deleted";
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * Publishes UserCreatedEvent
     */
    public void publishUserCreated(UserCreatedEvent event) {
        log.info("Publishing UserCreatedEvent for user: {}", event.getUserId());
        
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(USER_CREATED_TOPIC, event.getUserId().toString(), event);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("✅ UserCreatedEvent published successfully: {}", event.getUserId());
            } else {
                log.error("❌ Failed to publish UserCreatedEvent: {}", event.getUserId(), ex);
            }
        });
    }
    
    /**
     * Publishes UserUpdatedEvent
     */
    public void publishUserUpdated(UserUpdatedEvent event) {
        log.info("Publishing UserUpdatedEvent for user: {}", event.getUserId());
        
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(USER_UPDATED_TOPIC, event.getUserId().toString(), event);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("✅ UserUpdatedEvent published successfully: {}", event.getUserId());
            } else {
                log.error("❌ Failed to publish UserUpdatedEvent: {}", event.getUserId(), ex);
            }
        });
    }
    
    /**
     * Publishes UserDeletedEvent
     */
    public void publishUserDeleted(UserDeletedEvent event) {
        log.info("Publishing UserDeletedEvent for user: {}", event.getUserId());
        
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(USER_DELETED_TOPIC, event.getUserId().toString(), event);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("✅ UserDeletedEvent published successfully: {}", event.getUserId());
            } else {
                log.error("❌ Failed to publish UserDeletedEvent: {}", event.getUserId(), ex);
            }
        });
    }
    
    /**
     * Publishes PasswordResetRequestedEvent
     */
    public void publishPasswordResetRequested(PasswordResetRequestedEvent event) {
        log.info("Publishing PasswordResetRequestedEvent for user: {}", event.getUserId());
        
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(USER_EVENTS_TOPIC, event.getUserId().toString(), event);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("✅ PasswordResetRequestedEvent published successfully");
            } else {
                log.error("❌ Failed to publish PasswordResetRequestedEvent", ex);
            }
        });
    }
    
    /**
     * Publishes PasswordResetCompletedEvent
     */
    public void publishPasswordResetCompleted(PasswordResetCompletedEvent event) {
        log.info("Publishing PasswordResetCompletedEvent for user: {}", event.getUserId());
        
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(USER_EVENTS_TOPIC, event.getUserId().toString(), event);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("✅ PasswordResetCompletedEvent published successfully");
            } else {
                log.error("❌ Failed to publish PasswordResetCompletedEvent", ex);
            }
        });
    }
    
    /**
     * Publishes any domain event
     */
    public void publishEvent(Object event) {
        log.debug("Publishing event: {}", event.getClass().getSimpleName());
        
        if (event instanceof UserCreatedEvent) {
            publishUserCreated((UserCreatedEvent) event);
        } else if (event instanceof UserUpdatedEvent) {
            publishUserUpdated((UserUpdatedEvent) event);
        } else if (event instanceof UserDeletedEvent) {
            publishUserDeleted((UserDeletedEvent) event);
        } else if (event instanceof PasswordResetRequestedEvent) {
            publishPasswordResetRequested((PasswordResetRequestedEvent) event);
        } else if (event instanceof PasswordResetCompletedEvent) {
            publishPasswordResetCompleted((PasswordResetCompletedEvent) event);
        } else {
            log.warn("Unknown event type: {}", event.getClass().getName());
        }
    }
}

