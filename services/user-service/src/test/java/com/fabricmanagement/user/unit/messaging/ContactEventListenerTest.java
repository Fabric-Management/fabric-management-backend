package com.fabricmanagement.user.unit.messaging;

import com.fabricmanagement.user.domain.aggregate.ProcessedEvent;
import com.fabricmanagement.user.infrastructure.messaging.ContactEventListener;
import com.fabricmanagement.user.infrastructure.repository.ProcessedEventRepository;
import com.fabricmanagement.user.infrastructure.repository.UserRepository;
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
 * Unit Tests for ContactEventListener
 * 
 * Testing Strategy:
 * - Contact verification event handling
 * - Idempotency check
 * - User contact linkage
 * 
 * Coverage Goal: 85%+
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ContactEventListener Unit Tests")
class ContactEventListenerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private ContactEventListener listener;

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID CONTACT_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String OWNER_ID = UUID.randomUUID().toString();

    // ═════════════════════════════════════════════════════
    // CONTACT VERIFIED EVENT TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Contact Verified Event Tests")
    class ContactVerifiedEventTests {

        @Test
        @DisplayName("Should skip duplicate contact verified event")
        void shouldSkipDuplicateEvent() {
            // Given
            when(processedEventRepository.existsByEventId(EVENT_ID)).thenReturn(true);

            // When
            listener.handleContactVerified(
                createContactVerifiedEvent(),
                "contact.verified",
                0,
                0L
            );

            // Then
            verify(processedEventRepository).existsByEventId(EVENT_ID);
            verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
        }

        @Test
        @DisplayName("Should process new contact verified event")
        void shouldProcessNewContactVerifiedEvent() {
            // Given
            when(processedEventRepository.existsByEventId(EVENT_ID)).thenReturn(false);

            // When
            listener.handleContactVerified(
                createContactVerifiedEvent(),
                "contact.verified",
                0,
                0L
            );

            // Then
            verify(processedEventRepository).existsByEventId(EVENT_ID);
            verify(processedEventRepository).save(any(ProcessedEvent.class));
        }
    }

    // ═════════════════════════════════════════════════════
    // HELPER METHODS
    // ═════════════════════════════════════════════════════

    private com.fabricmanagement.user.infrastructure.messaging.event.ContactVerifiedEvent createContactVerifiedEvent() {
        return com.fabricmanagement.user.infrastructure.messaging.event.ContactVerifiedEvent.builder()
                .eventId(EVENT_ID)
                .contactId(CONTACT_ID)
                .ownerId(OWNER_ID)
                .ownerType("USER")
                .contactValue("test@example.com")
                .contactType("EMAIL")
                .tenantId(TENANT_ID)
                .verifiedAt(java.time.LocalDateTime.now())
                .build();
    }
}

