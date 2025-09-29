package com.fabricmanagement.user.infrastructure.messaging.publisher;

import com.fabricmanagement.user.domain.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Publisher for user domain events.
 * Publishes events to messaging infrastructure for other services to consume.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher {

    /**
     * Publishes user created event.
     */
    public void publishUserCreatedEvent(UserCreatedEvent event) {
        log.info("Publishing user created event for user: {}", event.getUserId());
        // TODO: Implement event publishing (Kafka, RabbitMQ, etc.)
        // For now, just log the event
        log.debug("User created event: {}", event);
    }

    /**
     * Publishes user updated event.
     */
    public void publishUserUpdatedEvent(UserUpdatedEvent event) {
        log.info("Publishing user updated event for user: {}", event.getUserId());
        // TODO: Implement event publishing (Kafka, RabbitMQ, etc.)
        // For now, just log the event
        log.debug("User updated event: {}", event);
    }

    /**
     * Publishes user activated event.
     */
    public void publishUserActivatedEvent(UserActivatedEvent event) {
        log.info("Publishing user activated event for user: {}", event.getUserId());
        // TODO: Implement event publishing (Kafka, RabbitMQ, etc.)
        // For now, just log the event
        log.debug("User activated event: {}", event);
    }

    /**
     * Publishes user deactivated event.
     */
    public void publishUserDeactivatedEvent(UserDeactivatedEvent event) {
        log.info("Publishing user deactivated event for user: {}", event.getUserId());
        // TODO: Implement event publishing (Kafka, RabbitMQ, etc.)
        // For now, just log the event
        log.debug("User deactivated event: {}", event);
    }

    /**
     * Publishes user suspended event.
     */
    public void publishUserSuspendedEvent(UserSuspendedEvent event) {
        log.info("Publishing user suspended event for user: {}", event.getUserId());
        // TODO: Implement event publishing (Kafka, RabbitMQ, etc.)
        // For now, just log the event
        log.debug("User suspended event: {}", event);
    }
}