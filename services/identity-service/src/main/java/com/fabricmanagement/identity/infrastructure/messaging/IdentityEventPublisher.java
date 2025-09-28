package com.fabricmanagement.identity.infrastructure.messaging;

import com.fabricmanagement.identity.domain.event.DomainEvent;
import com.fabricmanagement.identity.domain.event.UserCreatedEvent;
import com.fabricmanagement.identity.domain.event.UserProfileUpdatedEvent;
import com.fabricmanagement.identity.domain.event.UserRoleChangedEvent;
import com.fabricmanagement.identity.domain.event.UserSuspendedEvent;
import com.fabricmanagement.identity.domain.event.UserReactivatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Event publisher for sending domain events to Kafka.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdentityEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Topic names
    private static final String USER_CREATED_TOPIC = "user.created";
    private static final String USER_PROFILE_UPDATED_TOPIC = "user.profile.updated";
    private static final String USER_ROLE_CHANGED_TOPIC = "user.role.changed";
    private static final String USER_SUSPENDED_TOPIC = "user.suspended";
    private static final String USER_REACTIVATED_TOPIC = "user.reactivated";

    /**
     * Publishes user created event.
     */
    public void publishUserCreatedEvent(UserCreatedEvent event) {
        log.info("Publishing user created event for user: {}", event.getUserId());
        publishEvent(USER_CREATED_TOPIC, event.getUserId().toString(), event);
    }

    /**
     * Publishes user profile updated event.
     */
    public void publishUserProfileUpdatedEvent(UserProfileUpdatedEvent event) {
        log.info("Publishing user profile updated event for user: {}", event.getUserId());
        publishEvent(USER_PROFILE_UPDATED_TOPIC, event.getUserId().toString(), event);
    }

    /**
     * Publishes user role changed event.
     */
    public void publishUserRoleChangedEvent(UserRoleChangedEvent event) {
        log.info("Publishing user role changed event for user: {}", event.getUserId());
        publishEvent(USER_ROLE_CHANGED_TOPIC, event.getUserId().toString(), event);
    }

    /**
     * Publishes user suspended event.
     */
    public void publishUserSuspendedEvent(UserSuspendedEvent event) {
        log.info("Publishing user suspended event for user: {}", event.getUserId());
        publishEvent(USER_SUSPENDED_TOPIC, event.getUserId().toString(), event);
    }

    /**
     * Publishes user reactivated event.
     */
    public void publishUserReactivatedEvent(UserReactivatedEvent event) {
        log.info("Publishing user reactivated event for user: {}", event.getUserId());
        publishEvent(USER_REACTIVATED_TOPIC, event.getUserId().toString(), event);
    }

    /**
     * Generic method to publish events.
     */
    private void publishEvent(String topic, String key, Object event) {
        try {
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("Event published successfully to topic: {} with key: {}", topic, key);
                } else {
                    log.error("Failed to publish event to topic: {} with key: {}", topic, key, ex);
                }
            });
        } catch (Exception e) {
            log.error("Error publishing event to topic: {} with key: {}", topic, key, e);
        }
    }
}
