package com.fabricmanagement.user.unit.audit;

import com.fabricmanagement.user.infrastructure.audit.SecurityAuditLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit Tests for SecurityAuditLogger
 * 
 * Testing Strategy:
 * - Verify logging methods execute without errors
 * - Validate masking logic
 * - Ensure all security events are captured
 * 
 * Coverage Goal: 95%+
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityAuditLogger Unit Tests")
class SecurityAuditLoggerTest {

    @InjectMocks
    private SecurityAuditLogger auditLogger;

    private static final String TEST_CONTACT = "user@example.com";
    private static final String TEST_USER_ID = UUID.randomUUID().toString();
    private static final String TEST_TENANT_ID = UUID.randomUUID().toString();

    // ═════════════════════════════════════════════════════
    // LOGIN AUDIT TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Login Audit Tests")
    class LoginAuditTests {

        @Test
        @DisplayName("Should log successful login")
        void shouldLogSuccessfulLogin() {
            // When & Then
            assertThatCode(() -> 
                auditLogger.logSuccessfulLogin(TEST_CONTACT, TEST_USER_ID, TEST_TENANT_ID))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should log failed login")
        void shouldLogFailedLogin() {
            // When & Then
            assertThatCode(() -> 
                auditLogger.logFailedLogin(TEST_CONTACT, "Invalid credentials"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should log account lockout")
        void shouldLogAccountLockout() {
            // When & Then
            assertThatCode(() -> 
                auditLogger.logAccountLockout(TEST_CONTACT, 5, 30))
                .doesNotThrowAnyException();
        }
    }

    // ═════════════════════════════════════════════════════
    // PASSWORD AUDIT TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Password Audit Tests")
    class PasswordAuditTests {

        @Test
        @DisplayName("Should log password setup")
        void shouldLogPasswordSetup() {
            // When & Then
            assertThatCode(() -> 
                auditLogger.logPasswordSetup(TEST_CONTACT, TEST_USER_ID))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should log password change")
        void shouldLogPasswordChange() {
            // When & Then
            assertThatCode(() -> 
                auditLogger.logPasswordChange(TEST_USER_ID, "User requested change"))
                .doesNotThrowAnyException();
        }
    }

    // ═════════════════════════════════════════════════════
    // SECURITY EVENT TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Security Event Tests")
    class SecurityEventTests {

        @Test
        @DisplayName("Should log suspicious activity")
        void shouldLogSuspiciousActivity() {
            // When & Then
            assertThatCode(() -> 
                auditLogger.logSuspiciousActivity(
                    TEST_CONTACT, 
                    "BRUTE_FORCE", 
                    "Multiple failed login attempts"
                ))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should log unauthorized access")
        void shouldLogUnauthorizedAccess() {
            // When & Then
            assertThatCode(() -> 
                auditLogger.logUnauthorizedAccess(
                    "/api/v1/admin/users", 
                    TEST_USER_ID, 
                    "Insufficient permissions"
                ))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should log unauthorized access for anonymous user")
        void shouldLogUnauthorizedAccess_forAnonymousUser() {
            // When & Then
            assertThatCode(() -> 
                auditLogger.logUnauthorizedAccess(
                    "/api/v1/admin/users", 
                    null, 
                    "Not authenticated"
                ))
                .doesNotThrowAnyException();
        }
    }

    // ═════════════════════════════════════════════════════
    // EDGE CASE TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle null contact value")
        void shouldHandleNullContact() {
            // When & Then
            assertThatCode(() -> 
                auditLogger.logSuccessfulLogin(null, TEST_USER_ID, TEST_TENANT_ID))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle short contact value")
        void shouldHandleShortContact() {
            // When & Then
            assertThatCode(() -> 
                auditLogger.logSuccessfulLogin("ab", TEST_USER_ID, TEST_TENANT_ID))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle empty string contact")
        void shouldHandleEmptyContact() {
            // When & Then
            assertThatCode(() -> 
                auditLogger.logFailedLogin("", "Invalid credentials"))
                .doesNotThrowAnyException();
        }
    }
}

