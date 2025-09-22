package com.fabricmanagement.user.infrastructure.messaging.publisher;

import com.fabricmanagement.user.domain.model.User;
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
    public void publishUserCreatedEvent(User user) {
        log.info("Publishing user created event for user: {}", user.getId());
        // TODO: Implement event publishing (Kafka, RabbitMQ, etc.)
        // For now, just log the event
        log.debug("User created: {}", user);
    }

    /**
     * Publishes user updated event.
     */
    public void publishUserUpdatedEvent(User user) {
        log.info("Publishing user updated event for user: {}", user.getId());
        // TODO: Implement event publishing
        log.debug("User updated: {}", user);
    }

    /**
     * Publishes user deleted event.
     */
    public void publishUserDeletedEvent(User user) {
        log.info("Publishing user deleted event for user: {}", user.getId());
        // TODO: Implement event publishing
        log.debug("User deleted: {}", user);
    }
}

