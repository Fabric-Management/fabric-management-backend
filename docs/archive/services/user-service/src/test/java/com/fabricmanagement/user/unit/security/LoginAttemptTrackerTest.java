package com.fabricmanagement.user.unit.security;

import com.fabricmanagement.shared.domain.exception.AccountLockedException;
import com.fabricmanagement.user.infrastructure.audit.SecurityAuditLogger;
import com.fabricmanagement.user.infrastructure.security.LoginAttemptTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for LoginAttemptTracker
 * 
 * Testing Strategy:
 * - Failed attempt tracking
 * - Account lockout after max attempts
 * - Lockout expiration
 * - Manual unlock
 * 
 * Coverage Goal: 90%+
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginAttemptTracker Unit Tests")
class LoginAttemptTrackerTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private SecurityAuditLogger auditLogger;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private LoginAttemptTracker tracker;

    private static final String TEST_CONTACT = "user@example.com";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tracker, "maxAttempts", 5);
        ReflectionTestUtils.setField(tracker, "lockoutDurationMinutes", 15);
    }

    // ═════════════════════════════════════════════════════
    // FAILED ATTEMPT TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Failed Attempt Tests")
    class FailedAttemptTests {

        @Test
        @DisplayName("Should record first failed attempt")
        void shouldRecordFirstAttempt() {
            // Given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment(anyString())).thenReturn(1L);

            // When
            tracker.recordFailedAttempt(TEST_CONTACT);

            // Then
            verify(valueOperations).increment("login:attempts:" + TEST_CONTACT);
            verify(redisTemplate).expire(anyString(), eq(15L), eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("Should lock account after max attempts")
        void shouldLockAccount_afterMaxAttempts() {
            // Given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment(anyString())).thenReturn(5L);

            // When
            tracker.recordFailedAttempt(TEST_CONTACT);

            // Then
            verify(valueOperations).set(
                eq("login:lockout:" + TEST_CONTACT),
                anyString(),
                eq(15L),
                eq(TimeUnit.MINUTES)
            );
            verify(auditLogger).logAccountLockout(TEST_CONTACT, 5, 15);
        }

        @Test
        @DisplayName("Should handle null increment result")
        void shouldHandleNullIncrement() {
            // Given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment(anyString())).thenReturn(null);

            // When & Then
            assertThatCode(() -> tracker.recordFailedAttempt(TEST_CONTACT))
                .doesNotThrowAnyException();
        }
    }

    // ═════════════════════════════════════════════════════
    // LOCKOUT CHECK TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Lockout Check Tests")
    class LockoutCheckTests {

        @Test
        @DisplayName("Should throw exception when account locked")
        void shouldThrowException_whenAccountLocked() {
            // Given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("login:lockout:" + TEST_CONTACT)).thenReturn("1234567890");
            when(redisTemplate.getExpire(anyString(), eq(TimeUnit.MINUTES))).thenReturn(10L);

            // When & Then
            assertThatThrownBy(() -> tracker.checkIfLocked(TEST_CONTACT))
                    .isInstanceOf(AccountLockedException.class);
        }

        @Test
        @DisplayName("Should not throw when account not locked")
        void shouldNotThrow_whenAccountNotLocked() {
            // Given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("login:lockout:" + TEST_CONTACT)).thenReturn(null);

            // When & Then
            assertThatCode(() -> tracker.checkIfLocked(TEST_CONTACT))
                    .doesNotThrowAnyException();
        }
    }

    // ═════════════════════════════════════════════════════
    // UNLOCK TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Unlock Tests")
    class UnlockTests {

        @Test
        @DisplayName("Should clear failed attempts")
        void shouldClearFailedAttempts() {
            // When
            tracker.clearFailedAttempts(TEST_CONTACT);

            // Then
            verify(redisTemplate).delete("login:attempts:" + TEST_CONTACT);
            verify(redisTemplate).delete("login:lockout:" + TEST_CONTACT);
        }

        @Test
        @DisplayName("Should unlock account manually")
        void shouldUnlockAccount() {
            // When
            tracker.unlockAccount(TEST_CONTACT);

            // Then
            verify(redisTemplate).delete("login:attempts:" + TEST_CONTACT);
            verify(redisTemplate).delete("login:lockout:" + TEST_CONTACT);
        }
    }

    // ═════════════════════════════════════════════════════
    // QUERY TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Query Tests")
    class QueryTests {

        @Test
        @DisplayName("Should get failed attempts count")
        void shouldGetFailedAttempts() {
            // Given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("login:attempts:" + TEST_CONTACT)).thenReturn("3");

            // When
            int attempts = tracker.getFailedAttempts(TEST_CONTACT);

            // Then
            assertThat(attempts).isEqualTo(3);
        }

        @Test
        @DisplayName("Should return zero when no attempts recorded")
        void shouldReturnZero_whenNoAttempts() {
            // Given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("login:attempts:" + TEST_CONTACT)).thenReturn(null);

            // When
            int attempts = tracker.getFailedAttempts(TEST_CONTACT);

            // Then
            assertThat(attempts).isZero();
        }

        @Test
        @DisplayName("Should get remaining lockout minutes")
        void shouldGetRemainingLockoutMinutes() {
            // Given
            when(redisTemplate.getExpire("login:lockout:" + TEST_CONTACT, TimeUnit.MINUTES))
                    .thenReturn(10L);

            // When
            int remaining = tracker.getRemainingLockoutMinutes(TEST_CONTACT);

            // Then
            assertThat(remaining).isEqualTo(10);
        }

        @Test
        @DisplayName("Should return zero when not locked")
        void shouldReturnZero_whenNotLocked() {
            // Given
            when(redisTemplate.getExpire("login:lockout:" + TEST_CONTACT, TimeUnit.MINUTES))
                    .thenReturn(null);

            // When
            int remaining = tracker.getRemainingLockoutMinutes(TEST_CONTACT);

            // Then
            assertThat(remaining).isZero();
        }
    }
}

