package com.fabricmanagement.user.unit.messaging;

import com.fabricmanagement.user.domain.aggregate.ProcessedEvent;
import com.fabricmanagement.user.infrastructure.messaging.CompanyEventListener;
import com.fabricmanagement.user.infrastructure.repository.ProcessedEventRepository;
import com.fabricmanagement.user.infrastructure.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for CompanyEventListener
 * 
 * Testing Strategy:
 * - Event routing and parsing
 * - Idempotency check
 * - Error handling
 * - User updates based on company events
 * 
 * Coverage Goal: 90%+
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CompanyEventListener Unit Tests")
class CompanyEventListenerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CompanyEventListener listener;

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    // ═════════════════════════════════════════════════════
    // IDEMPOTENCY TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Idempotency Tests")
    class IdempotencyTests {

        @Test
        @DisplayName("Should skip duplicate event")
        void shouldSkipDuplicateEvent() throws Exception {
            // Given
            String eventJson = String.format(
                "{\"eventType\":\"CompanyCreatedEvent\",\"eventId\":\"%s\",\"companyId\":\"%s\"}",
                EVENT_ID, COMPANY_ID
            );

            when(objectMapper.readValue(anyString(), eq(java.util.Map.class)))
                .thenReturn(java.util.Map.of(
                    "eventType", "CompanyCreatedEvent",
                    "eventId", EVENT_ID.toString(),
                    "companyId", COMPANY_ID.toString()
                ));
            when(processedEventRepository.existsByEventId(EVENT_ID)).thenReturn(true);

            // When
            listener.handleCompanyEvent(eventJson, "company-events", 0, 0L);

            // Then
            verify(processedEventRepository).existsByEventId(EVENT_ID);
            verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should process new event")
        void shouldProcessNewEvent() throws Exception {
            // Given
            String eventJson = String.format(
                "{\"eventType\":\"CompanyCreatedEvent\",\"eventId\":\"%s\",\"companyId\":\"%s\",\"tenantId\":\"%s\"}",
                EVENT_ID, COMPANY_ID, TENANT_ID
            );

            when(objectMapper.readValue(anyString(), eq(java.util.Map.class)))
                .thenReturn(java.util.Map.of(
                    "eventType", "CompanyCreatedEvent",
                    "eventId", EVENT_ID.toString(),
                    "companyId", COMPANY_ID.toString(),
                    "tenantId", TENANT_ID.toString()
                ));
            when(processedEventRepository.existsByEventId(EVENT_ID)).thenReturn(false);

            // When
            listener.handleCompanyEvent(eventJson, "company-events", 0, 0L);

            // Then
            verify(processedEventRepository).existsByEventId(EVENT_ID);
            verify(processedEventRepository).save(any(ProcessedEvent.class));
        }
    }

    // ═════════════════════════════════════════════════════
    // ERROR HANDLING TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle missing event type gracefully")
        void shouldHandleMissingEventType() throws Exception {
            // Given
            String eventJson = "{\"companyId\":\"" + COMPANY_ID + "\"}";

            when(objectMapper.readValue(anyString(), eq(java.util.Map.class)))
                .thenReturn(java.util.Map.of("companyId", COMPANY_ID.toString()));

            // When
            listener.handleCompanyEvent(eventJson, "company-events", 0, 0L);

            // Then
            verify(processedEventRepository, never()).save(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should handle JSON parse error gracefully")
        void shouldHandleJsonParseError() throws Exception {
            // Given
            String invalidJson = "invalid-json";

            when(objectMapper.readValue(anyString(), eq(java.util.Map.class)))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("Parse error") {});

            // When
            // CompanyEventListener catches exceptions and logs them (graceful degradation)
            listener.handleCompanyEvent(invalidJson, "company-events", 0, 0L);

            // Then
            // Should NOT save any events when parsing fails
            verify(processedEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should handle event without eventId")
        void shouldHandleEventWithoutEventId() throws Exception {
            // Given
            String eventJson = String.format(
                "{\"eventType\":\"CompanyCreatedEvent\",\"companyId\":\"%s\"}",
                COMPANY_ID
            );

            when(objectMapper.readValue(anyString(), eq(java.util.Map.class)))
                .thenReturn(java.util.Map.of(
                    "eventType", "CompanyCreatedEvent",
                    "companyId", COMPANY_ID.toString()
                ));

            // When
            listener.handleCompanyEvent(eventJson, "company-events", 0, 0L);

            // Then
            // Should process without idempotency check
            verify(processedEventRepository, never()).existsByEventId(any());
        }
    }

}

