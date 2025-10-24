package com.fabricmanagement.user.unit.mapper;

import com.fabricmanagement.user.application.mapper.UserEventMapper;
import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.user.domain.event.UserDeletedEvent;
import com.fabricmanagement.user.domain.event.UserUpdatedEvent;
import com.fabricmanagement.user.fixtures.UserFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for UserEventMapper
 * 
 * Tests mapping from User entities to domain events
 * 
 * Strategy:
 * - Use fixtures for test data
 * - Verify all fields mapped correctly
 * - Test edge cases (null fields)
 * 
 * Coverage Goal: 95%+
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserEventMapper - Unit Tests")
class UserEventMapperTest {

    @InjectMocks
    private UserEventMapper userEventMapper;

    // ═══════════════════════════════════════════════════════
    // USER CREATED EVENT TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("User Created Event Mapping Tests")
    class UserCreatedEventTests {

        @Test
        @DisplayName("Should map User to UserCreatedEvent correctly")
        void shouldMapToCreatedEvent() {
            // Given
            User user = UserFixtures.createActiveUser("John", "Doe", "john@test.com");
            user.setId(UUID.randomUUID()); // Simulate persisted entity
            String email = "john@test.com";

            // When
            UserCreatedEvent event = userEventMapper.toCreatedEvent(user, email);

            // Then
            assertThat(event).isNotNull();
            assertThat(event.getUserId()).isEqualTo(user.getId());
            assertThat(event.getTenantId()).isEqualTo(user.getTenantId().toString());
            assertThat(event.getFirstName()).isEqualTo("John");
            assertThat(event.getLastName()).isEqualTo("Doe");
            assertThat(event.getEmail()).isEqualTo(email);
            assertThat(event.getStatus()).isEqualTo(user.getStatus().name());
            assertThat(event.getRegistrationType()).isEqualTo(user.getRegistrationType().name());
            assertThat(event.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("Should handle self-registered user event")
        void shouldMapSelfRegisteredUserEvent() {
            // Given
            User user = UserFixtures.createSelfRegisteredUser("Jane", "Smith");
            user.setId(UUID.randomUUID());

            // When
            UserCreatedEvent event = userEventMapper.toCreatedEvent(user, "jane@test.com");

            // Then
            assertThat(event.getRegistrationType()).isEqualTo("SELF_REGISTRATION");
            assertThat(event.getStatus()).isEqualTo("PENDING_VERIFICATION");
        }
    }

    // ═══════════════════════════════════════════════════════
    // USER UPDATED EVENT TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("User Updated Event Mapping Tests")
    class UserUpdatedEventTests {

        @Test
        @DisplayName("Should map User to UserUpdatedEvent correctly")
        void shouldMapToUpdatedEvent() {
            // Given
            User user = UserFixtures.createActiveUser("John", "Doe", "john@test.com");
            user.setId(UUID.randomUUID());

            // When
            UserUpdatedEvent event = userEventMapper.toUpdatedEvent(user);

            // Then
            assertThat(event).isNotNull();
            assertThat(event.getUserId()).isEqualTo(user.getId());
            assertThat(event.getTenantId()).isEqualTo(user.getTenantId().toString());
            assertThat(event.getFirstName()).isEqualTo("John");
            assertThat(event.getLastName()).isEqualTo("Doe");
            assertThat(event.getStatus()).isEqualTo(user.getStatus().name());
            assertThat(event.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("Should map suspended user update event")
        void shouldMapSuspendedUserUpdateEvent() {
            // Given
            User user = UserFixtures.createSuspendedUser("Test", "User");
            user.setId(UUID.randomUUID());

            // When
            UserUpdatedEvent event = userEventMapper.toUpdatedEvent(user);

            // Then
            assertThat(event.getStatus()).isEqualTo("SUSPENDED");
        }
    }

    // ═══════════════════════════════════════════════════════
    // USER DELETED EVENT TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("User Deleted Event Mapping Tests")
    class UserDeletedEventTests {

        @Test
        @DisplayName("Should map User to UserDeletedEvent correctly")
        void shouldMapToDeletedEvent() {
            // Given
            User user = UserFixtures.createDeletedUser("John", "Doe");
            user.setId(UUID.randomUUID());

            // When
            UserDeletedEvent event = userEventMapper.toDeletedEvent(user);

            // Then
            assertThat(event).isNotNull();
            assertThat(event.getUserId()).isEqualTo(user.getId());
            assertThat(event.getTenantId()).isEqualTo(user.getTenantId().toString());
            assertThat(event.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("Should map deleted event with minimal fields")
        void shouldMapMinimalDeletedEvent() {
            // Given
            UUID userId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            
            User user = User.builder()
                    .id(userId)
                    .tenantId(tenantId)
                    .build();

            // When
            UserDeletedEvent event = userEventMapper.toDeletedEvent(user);

            // Then
            assertThat(event.getUserId()).isEqualTo(userId);
            assertThat(event.getTenantId()).isEqualTo(tenantId.toString());
        }
    }
}

