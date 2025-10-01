package com.fabricmanagement.user.domain.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for PasswordResetToken Value Object
 * 
 * Tests all security constraints and business rules
 */
@DisplayName("PasswordResetToken Tests")
class PasswordResetTokenTest {

    private static final String CONTACT_VALUE = "test@example.com";
    private static final UUID TEST_USER_ID = UUID.randomUUID();

    @Nested
    @DisplayName("Token Creation Tests")
    class TokenCreationTests {

        @Test
        @DisplayName("Should create EMAIL_LINK token successfully")
        void shouldCreateEmailLinkTokenSuccessfully() {
            // When
            PasswordResetToken token = PasswordResetToken.create(TEST_USER_ID, CONTACT_VALUE, PasswordResetToken.ResetMethod.EMAIL_LINK);

            // Then
            assertThat(token).isNotNull();
            assertThat(token.getContactValue()).isEqualTo(CONTACT_VALUE);
            assertThat(token.getResetMethod()).isEqualTo(PasswordResetToken.ResetMethod.EMAIL_LINK);
            assertThat(token.getToken()).isNotBlank();
            assertThat(token.getToken()).hasSize(32); // UUID without dashes
            assertThat(token.getExpiresAt()).isAfter(LocalDateTime.now());
            assertThat(token.getAttemptsRemaining()).isEqualTo(3);
            assertThat(token.isUsed()).isFalse();
        }

        @Test
        @DisplayName("Should create SMS_CODE token successfully")
        void shouldCreateSmsCodeTokenSuccessfully() {
            // When
            PasswordResetToken token = PasswordResetToken.create(TEST_USER_ID, CONTACT_VALUE, PasswordResetToken.ResetMethod.SMS_CODE);

            // Then
            assertThat(token).isNotNull();
            assertThat(token.getContactValue()).isEqualTo(CONTACT_VALUE);
            assertThat(token.getResetMethod()).isEqualTo(PasswordResetToken.ResetMethod.SMS_CODE);
            assertThat(token.getToken()).isNotBlank();
            assertThat(token.getToken()).hasSize(6); // 6-digit code
            assertThat(token.getToken()).matches("\\d{6}"); // Only digits
            assertThat(token.getExpiresAt()).isAfter(LocalDateTime.now());
            assertThat(token.getAttemptsRemaining()).isEqualTo(3);
            assertThat(token.isUsed()).isFalse();
        }

        @Test
        @DisplayName("Should create EMAIL_CODE token successfully")
        void shouldCreateEmailCodeTokenSuccessfully() {
            // When
            PasswordResetToken token = PasswordResetToken.create(TEST_USER_ID, CONTACT_VALUE, PasswordResetToken.ResetMethod.EMAIL_CODE);

            // Then
            assertThat(token).isNotNull();
            assertThat(token.getContactValue()).isEqualTo(CONTACT_VALUE);
            assertThat(token.getResetMethod()).isEqualTo(PasswordResetToken.ResetMethod.EMAIL_CODE);
            assertThat(token.getToken()).isNotBlank();
            assertThat(token.getToken()).hasSize(6); // 6-digit code
            assertThat(token.getToken()).matches("\\d{6}"); // Only digits
            assertThat(token.getExpiresAt()).isAfter(LocalDateTime.now());
            assertThat(token.getAttemptsRemaining()).isEqualTo(3);
            assertThat(token.isUsed()).isFalse();
        }
    }

    @Nested
    @DisplayName("Token Validation Tests")
    class TokenValidationTests {

        @Test
        @DisplayName("Should be valid when token is fresh and not used")
        void shouldBeValidWhenTokenIsFreshAndNotUsed() {
            // Given
            PasswordResetToken token = PasswordResetToken.create(TEST_USER_ID, CONTACT_VALUE, PasswordResetToken.ResetMethod.EMAIL_LINK);

            // When & Then
            assertThat(token.isValid()).isTrue();
            assertThat(token.isExpired()).isFalse();
        }

        @Test
        @DisplayName("Should be invalid when token is expired")
        void shouldBeInvalidWhenTokenIsExpired() {
            // Given
            PasswordResetToken token = PasswordResetToken.create(TEST_USER_ID, CONTACT_VALUE, PasswordResetToken.ResetMethod.EMAIL_LINK);
            // Manually set expired time
            PasswordResetToken expiredToken = PasswordResetToken.builder()
                .userId(token.getUserId())
                .contactValue(token.getContactValue())
                .token(token.getToken())
                .resetMethod(token.getResetMethod())
                .expiresAt(LocalDateTime.now().minusMinutes(1)) // Expired 1 minute ago
                .attemptsRemaining(token.getAttemptsRemaining())
                .isUsed(token.isUsed())
                .build();

            // When & Then
            assertThat(expiredToken.isValid()).isFalse();
            assertThat(expiredToken.isExpired()).isTrue();
        }

        @Test
        @DisplayName("Should be invalid when token is used")
        void shouldBeInvalidWhenTokenIsUsed() {
            // Given
            PasswordResetToken token = PasswordResetToken.create(TEST_USER_ID, CONTACT_VALUE, PasswordResetToken.ResetMethod.EMAIL_LINK);
            PasswordResetToken usedToken = token.markAsUsed();

            // When & Then
            assertThat(usedToken.isValid()).isFalse();
            assertThat(usedToken.isUsed()).isTrue();
        }

        @Test
        @DisplayName("Should be invalid when no attempts remaining")
        void shouldBeInvalidWhenNoAttemptsRemaining() {
            // Given
            PasswordResetToken token = PasswordResetToken.create(TEST_USER_ID, CONTACT_VALUE, PasswordResetToken.ResetMethod.EMAIL_LINK);
            PasswordResetToken exhaustedToken = token.consumeAttempt().consumeAttempt().consumeAttempt();

            // When & Then
            assertThat(exhaustedToken.isValid()).isFalse();
            assertThat(exhaustedToken.getAttemptsRemaining()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Token Consumption Tests")
    class TokenConsumptionTests {

        @Test
        @DisplayName("Should consume attempt successfully")
        void shouldConsumeAttemptSuccessfully() {
            // Given
            PasswordResetToken token = PasswordResetToken.create(TEST_USER_ID, CONTACT_VALUE, PasswordResetToken.ResetMethod.EMAIL_LINK);

            // When
            PasswordResetToken consumedToken = token.consumeAttempt();

            // Then
            assertThat(consumedToken.getAttemptsRemaining()).isEqualTo(2);
            assertThat(consumedToken.getToken()).isEqualTo(token.getToken());
            assertThat(consumedToken.getContactValue()).isEqualTo(token.getContactValue());
        }

        @Test
        @DisplayName("Should mark token as used when last attempt is consumed")
        void shouldMarkTokenAsUsedWhenLastAttemptIsConsumed() {
            // Given
            PasswordResetToken token = PasswordResetToken.create(TEST_USER_ID, CONTACT_VALUE, PasswordResetToken.ResetMethod.EMAIL_LINK);

            // When
            PasswordResetToken finalToken = token.consumeAttempt().consumeAttempt().consumeAttempt();

            // Then
            assertThat(finalToken.getAttemptsRemaining()).isEqualTo(0);
            assertThat(finalToken.isUsed()).isTrue();
        }

        @Test
        @DisplayName("Should mark token as used successfully")
        void shouldMarkTokenAsUsedSuccessfully() {
            // Given
            PasswordResetToken token = PasswordResetToken.create(TEST_USER_ID, CONTACT_VALUE, PasswordResetToken.ResetMethod.EMAIL_LINK);

            // When
            PasswordResetToken usedToken = token.markAsUsed();

            // Then
            assertThat(usedToken.isUsed()).isTrue();
            assertThat(usedToken.getAttemptsRemaining()).isEqualTo(token.getAttemptsRemaining());
            assertThat(usedToken.getToken()).isEqualTo(token.getToken());
        }
    }

    @Nested
    @DisplayName("Token Expiry Tests")
    class TokenExpiryTests {

        @Test
        @DisplayName("Should expire after 15 minutes")
        void shouldExpireAfter15Minutes() {
            // Given
            PasswordResetToken token = PasswordResetToken.create(TEST_USER_ID, CONTACT_VALUE, PasswordResetToken.ResetMethod.EMAIL_LINK);

            // When
            LocalDateTime expiryTime = token.getExpiresAt();

            // Then
            assertThat(expiryTime).isAfter(LocalDateTime.now().plusMinutes(14));
            assertThat(expiryTime).isBefore(LocalDateTime.now().plusMinutes(16));
        }

        @Test
        @DisplayName("Should not be expired immediately after creation")
        void shouldNotBeExpiredImmediatelyAfterCreation() {
            // Given
            PasswordResetToken token = PasswordResetToken.create(TEST_USER_ID, CONTACT_VALUE, PasswordResetToken.ResetMethod.EMAIL_LINK);

            // When & Then
            assertThat(token.isExpired()).isFalse();
        }
    }

    @Nested
    @DisplayName("Token Security Tests")
    class TokenSecurityTests {

        @Test
        @DisplayName("Should generate unique tokens for same contact")
        void shouldGenerateUniqueTokensForSameContact() {
            // When
            PasswordResetToken token1 = PasswordResetToken.create(TEST_USER_ID, CONTACT_VALUE, PasswordResetToken.ResetMethod.EMAIL_LINK);
            PasswordResetToken token2 = PasswordResetToken.create(TEST_USER_ID, CONTACT_VALUE, PasswordResetToken.ResetMethod.EMAIL_LINK);

            // Then
            assertThat(token1.getToken()).isNotEqualTo(token2.getToken());
        }

        @Test
        @DisplayName("Should generate unique codes for SMS")
        void shouldGenerateUniqueCodesForSms() {
            // When
            PasswordResetToken token1 = PasswordResetToken.create(TEST_USER_ID, CONTACT_VALUE, PasswordResetToken.ResetMethod.SMS_CODE);
            PasswordResetToken token2 = PasswordResetToken.create(TEST_USER_ID, CONTACT_VALUE, PasswordResetToken.ResetMethod.SMS_CODE);

            // Then
            assertThat(token1.getToken()).isNotEqualTo(token2.getToken());
        }

        @Test
        @DisplayName("Should generate 6-digit numeric codes for SMS and EMAIL_CODE")
        void shouldGenerate6DigitNumericCodesForSmsAndEmailCode() {
            // When
            PasswordResetToken smsToken = PasswordResetToken.create(TEST_USER_ID, CONTACT_VALUE, PasswordResetToken.ResetMethod.SMS_CODE);
            PasswordResetToken emailCodeToken = PasswordResetToken.create(TEST_USER_ID, CONTACT_VALUE, PasswordResetToken.ResetMethod.EMAIL_CODE);

            // Then
            assertThat(smsToken.getToken()).matches("\\d{6}");
            assertThat(emailCodeToken.getToken()).matches("\\d{6}");
        }

        @Test
        @DisplayName("Should generate UUID-based tokens for EMAIL_LINK")
        void shouldGenerateUuidBasedTokensForEmailLink() {
            // When
            PasswordResetToken linkToken = PasswordResetToken.create(TEST_USER_ID, CONTACT_VALUE, PasswordResetToken.ResetMethod.EMAIL_LINK);

            // Then
            assertThat(linkToken.getToken()).matches("[a-f0-9]{32}"); // UUID without dashes
        }
    }
}
