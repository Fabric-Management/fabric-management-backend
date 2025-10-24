package com.fabricmanagement.user.unit.messaging;

import com.fabricmanagement.shared.domain.outbox.OutboxEvent;
import com.fabricmanagement.shared.domain.outbox.OutboxEventStatus;
import com.fabricmanagement.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.user.domain.event.UserDeletedEvent;
import com.fabricmanagement.user.domain.event.UserUpdatedEvent;
import com.fabricmanagement.user.infrastructure.messaging.UserEventPublisher;
import com.fabricmanagement.user.infrastructure.outbox.UserOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for UserEventPublisher
 * 
 * Tests Transactional Outbox Pattern implementation
 * 
 * Strategy:
 * - Mock OutboxEventRepository
 * - Verify outbox events are correctly created
 * - Test JSON serialization
 * - Test error handling
 * 
 * Coverage Goal: 90%+
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserEventPublisher - Unit Tests")
class UserEventPublisherTest {

    @Mock
    private UserOutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private UserEventPublisher userEventPublisher;

    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final UUID TEST_TENANT_ID = UUID.randomUUID();

    // ═══════════════════════════════════════════════════════
    // PUBLISH USER CREATED EVENT TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Publish User Created Event Tests")
    class PublishUserCreatedTests {

        @Test
        @DisplayName("Should write UserCreatedEvent to outbox")
        void shouldWriteUserCreatedEventToOutbox() throws JsonProcessingException {
            // Given
            ReflectionTestUtils.setField(userEventPublisher, "userCreatedTopic", "user.created");
            
            UserCreatedEvent event = UserCreatedEvent.builder()
                    .userId(TEST_USER_ID)
                    .tenantId(TEST_TENANT_ID.toString())
                    .firstName("John")
                    .lastName("Doe")
                    .email("john@test.com")
                    .timestamp(LocalDateTime.now())
                    .build();

            when(objectMapper.writeValueAsString(any())).thenReturn("{\"userId\":\"" + TEST_USER_ID + "\"}");
            when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

            // When
            userEventPublisher.publishUserCreated(event);

            // Then
            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());

            OutboxEvent saved = captor.getValue();
            assertThat(saved.getAggregateType()).isEqualTo("USER");
            assertThat(saved.getAggregateId()).isEqualTo(TEST_USER_ID);
            assertThat(saved.getEventType()).isEqualTo("UserCreatedEvent");
            assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.NEW);
            assertThat(saved.getRetryCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should handle JSON serialization error")
        void shouldHandleJsonSerializationError() throws JsonProcessingException {
            // Given
            ReflectionTestUtils.setField(userEventPublisher, "userCreatedTopic", "user.created");
            
            UserCreatedEvent event = UserCreatedEvent.builder()
                    .userId(TEST_USER_ID)
                    .tenantId(TEST_TENANT_ID.toString())
                    .build();

            when(objectMapper.writeValueAsString(any()))
                    .thenThrow(new JsonProcessingException("Serialization failed") {});

            // When & Then
            assertThatThrownBy(() -> userEventPublisher.publishUserCreated(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to write event to outbox");

            verify(outboxEventRepository, never()).save(any());
        }
    }

    // ═══════════════════════════════════════════════════════
    // PUBLISH USER UPDATED EVENT TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Publish User Updated Event Tests")
    class PublishUserUpdatedTests {

        @Test
        @DisplayName("Should write UserUpdatedEvent to outbox")
        void shouldWriteUserUpdatedEventToOutbox() throws JsonProcessingException {
            // Given
            ReflectionTestUtils.setField(userEventPublisher, "userUpdatedTopic", "user.updated");
            
            UserUpdatedEvent event = UserUpdatedEvent.builder()
                    .userId(TEST_USER_ID)
                    .tenantId(TEST_TENANT_ID.toString())
                    .firstName("Jane")
                    .lastName("Doe")
                    .timestamp(LocalDateTime.now())
                    .build();

            when(objectMapper.writeValueAsString(any())).thenReturn("{\"userId\":\"" + TEST_USER_ID + "\"}");
            when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

            // When
            userEventPublisher.publishUserUpdated(event);

            // Then
            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());

            OutboxEvent saved = captor.getValue();
            assertThat(saved.getEventType()).isEqualTo("UserUpdatedEvent");
            assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.NEW);
        }
    }

    // ═══════════════════════════════════════════════════════
    // PUBLISH USER DELETED EVENT TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Publish User Deleted Event Tests")
    class PublishUserDeletedTests {

        @Test
        @DisplayName("Should write UserDeletedEvent to outbox")
        void shouldWriteUserDeletedEventToOutbox() throws JsonProcessingException {
            // Given
            ReflectionTestUtils.setField(userEventPublisher, "userDeletedTopic", "user.deleted");
            
            UserDeletedEvent event = UserDeletedEvent.builder()
                    .userId(TEST_USER_ID)
                    .tenantId(TEST_TENANT_ID.toString())
                    .timestamp(LocalDateTime.now())
                    .build();

            when(objectMapper.writeValueAsString(any())).thenReturn("{\"userId\":\"" + TEST_USER_ID + "\"}");
            when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

            // When
            userEventPublisher.publishUserDeleted(event);

            // Then
            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());

            OutboxEvent saved = captor.getValue();
            assertThat(saved.getEventType()).isEqualTo("UserDeletedEvent");
            assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.NEW);
        }
    }
}

