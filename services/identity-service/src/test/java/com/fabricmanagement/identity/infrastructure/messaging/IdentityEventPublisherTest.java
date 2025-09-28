package com.fabricmanagement.identity.infrastructure.messaging;

import com.fabricmanagement.identity.domain.event.UserCreatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Integration test for Identity Event Publisher.
 */
@SpringBootTest
@DirtiesContext
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:9092"})
class IdentityEventPublisherTest {

    @Autowired
    private IdentityEventPublisher eventPublisher;

    @Test
    void shouldPublishUserCreatedEvent() {
        // Given
        UserCreatedEvent event = new UserCreatedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "testuser"
        );

        // When & Then
        assertDoesNotThrow(() -> {
            eventPublisher.publishUserCreatedEvent(event);
            // Give some time for the event to be published
            Thread.sleep(1000);
        });
    }
}
