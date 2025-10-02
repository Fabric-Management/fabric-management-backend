package com.fabricmanagement.user.infrastructure.repository;

import com.fabricmanagement.user.domain.valueobject.PasswordResetToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * PasswordResetToken Repository Integration Tests
 * 
 * Tests password reset token database operations
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("PasswordResetToken Repository Integration Tests")
class PasswordResetTokenRepositoryIntegrationTest {

    // Test constants
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PHONE = "+1234567890";

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    private PasswordResetToken testToken;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testToken = PasswordResetToken.create(testUserId, TEST_EMAIL, PasswordResetToken.ResetMethod.EMAIL_LINK);
        
        // Token is already created with proper user ID
        
        entityManager.persistAndFlush(testToken);
        entityManager.clear();
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudOperations {

        @Test
        @DisplayName("Should save and retrieve token successfully")
        void shouldSaveAndRetrieveTokenSuccessfully() {
            // When
            PasswordResetToken savedToken = tokenRepository.save(testToken);
            Optional<PasswordResetToken> foundToken = tokenRepository.findByToken(savedToken.getToken());

            // Then
            assertThat(foundToken).isPresent();
            assertThat(foundToken.get().getContactValue()).isEqualTo(TEST_EMAIL);
            assertThat(foundToken.get().getResetMethod()).isEqualTo(PasswordResetToken.ResetMethod.EMAIL_LINK);
            assertThat(foundToken.get().isValid()).isTrue();
        }

        @Test
        @DisplayName("Should update token successfully")
        void shouldUpdateTokenSuccessfully() {
            // Given
            PasswordResetToken savedToken = tokenRepository.save(testToken);
            
            // When
            PasswordResetToken consumedToken = savedToken.consumeAttempt();
            tokenRepository.save(consumedToken);
            
            // Then
            Optional<PasswordResetToken> updatedToken = tokenRepository.findByToken(savedToken.getToken());
            assertThat(updatedToken).isPresent();
            assertThat(updatedToken.get().getAttemptsRemaining()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should mark token as used")
        void shouldMarkTokenAsUsed() {
            // Given
            PasswordResetToken savedToken = tokenRepository.save(testToken);
            
            // When
            PasswordResetToken usedToken = savedToken.markAsUsed();
            tokenRepository.save(usedToken);
            
            // Then
            Optional<PasswordResetToken> foundToken = tokenRepository.findByToken(savedToken.getToken());
            assertThat(foundToken).isPresent();
            assertThat(foundToken.get().isUsed()).isTrue();
        }
    }

    @Nested
    @DisplayName("Token Lookup Operations")
    class TokenLookupOperations {

        @Test
        @DisplayName("Should find token by token value")
        void shouldFindTokenByTokenValue() {
            // When
            Optional<PasswordResetToken> foundToken = tokenRepository.findByToken(testToken.getToken());

            // Then
            assertThat(foundToken).isPresent();
            assertThat(foundToken.get().getContactValue()).isEqualTo(TEST_EMAIL);
        }

        @Test
        @DisplayName("Should return empty when token not found")
        void shouldReturnEmptyWhenTokenNotFound() {
            // When
            Optional<PasswordResetToken> foundToken = tokenRepository.findByToken("nonexistent-token");

            // Then
            assertThat(foundToken).isEmpty();
        }

        @Test
        @DisplayName("Should find tokens by contact value")
        void shouldFindTokensByContactValue() {
            // When
            List<PasswordResetToken> tokens = tokenRepository.findByContactValue(TEST_EMAIL);

            // Then
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0).getContactValue()).isEqualTo(TEST_EMAIL);
        }

        @Test
        @DisplayName("Should find tokens by reset method")
        void shouldFindTokensByResetMethod() {
            // When
            List<PasswordResetToken> linkTokens = tokenRepository.findByResetMethod(PasswordResetToken.ResetMethod.EMAIL_LINK);

            // Then
            assertThat(linkTokens).hasSize(1);
            assertThat(linkTokens.get(0).getResetMethod()).isEqualTo(PasswordResetToken.ResetMethod.EMAIL_LINK);
        }
    }

    @Nested
    @DisplayName("Token Status Operations")
    class TokenStatusOperations {

        @Test
        @DisplayName("Should find active tokens")
        void shouldFindActiveTokens() {
            // When
            List<PasswordResetToken> activeTokens = tokenRepository.findActiveTokens(LocalDateTime.now());

            // Then
            assertThat(activeTokens).hasSize(1);
            assertThat(activeTokens.get(0).isValid()).isTrue();
        }

        @Test
        @DisplayName("Should find expired tokens")
        void shouldFindExpiredTokens() {
            // Given
            PasswordResetToken expiredToken = PasswordResetToken.builder()
                .userId(testUserId)
                .contactValue(TEST_EMAIL)
                .token("expired-token")
                .resetMethod(PasswordResetToken.ResetMethod.EMAIL_LINK)
                .expiresAt(LocalDateTime.now().minusMinutes(1)) // Expired 1 minute ago
                .attemptsRemaining(3)
                .isUsed(false)
                .build();
            tokenRepository.save(expiredToken);

            // When
            List<PasswordResetToken> expiredTokens = tokenRepository.findExpiredTokens(LocalDateTime.now());

            // Then
            assertThat(expiredTokens).hasSize(1);
            assertThat(expiredTokens.get(0).getToken()).isEqualTo("expired-token");
        }

        @Test
        @DisplayName("Should find used tokens")
        void shouldFindUsedTokens() {
            // Given
            PasswordResetToken usedToken = testToken.markAsUsed();
            tokenRepository.save(usedToken);

            // When
            List<PasswordResetToken> usedTokens = tokenRepository.findByIsUsedTrue();

            // Then
            assertThat(usedTokens).hasSize(1);
            assertThat(usedTokens.get(0).isUsed()).isTrue();
        }

        @Test
        @DisplayName("Should count active tokens by user")
        void shouldCountActiveTokensByUser() {
            // When
            long count = tokenRepository.countActiveTokensByUser(testUserId, LocalDateTime.now());

            // Then
            assertThat(count).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Token Cleanup Operations")
    class TokenCleanupOperations {

        @Test
        @DisplayName("Should delete expired tokens")
        void shouldDeleteExpiredTokens() {
            // Given
            PasswordResetToken expiredToken = PasswordResetToken.builder()
                .userId(testUserId)
                .contactValue(TEST_EMAIL)
                .token("expired-token")
                .resetMethod(PasswordResetToken.ResetMethod.EMAIL_LINK)
                .expiresAt(LocalDateTime.now().minusMinutes(1)) // Expired 1 minute ago
                .attemptsRemaining(3)
                .isUsed(false)
                .build();
            tokenRepository.save(expiredToken);

            // When
            tokenRepository.deleteExpiredTokens(LocalDateTime.now());

            // Then
            List<PasswordResetToken> allTokens = tokenRepository.findAll();
            assertThat(allTokens).hasSize(1); // Only the non-expired token remains
            assertThat(allTokens.get(0).getToken()).isEqualTo(testToken.getToken());
        }

        @Test
        @DisplayName("Should find tokens created after specific time")
        void shouldFindTokensCreatedAfterSpecificTime() {
            // Given
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(5);

            // When
            List<PasswordResetToken> recentTokens = tokenRepository.findByCreatedAtAfter(cutoffTime);

            // Then
            assertThat(recentTokens).hasSize(1);
            assertThat(recentTokens.get(0).getToken()).isEqualTo(testToken.getToken());
        }
    }

    @Nested
    @DisplayName("Token Security Tests")
    class TokenSecurityTests {

        @Test
        @DisplayName("Should not find tokens with exhausted attempts")
        void shouldNotFindTokensWithExhaustedAttempts() {
            // Given
            PasswordResetToken exhaustedToken = testToken.consumeAttempt().consumeAttempt().consumeAttempt();
            tokenRepository.save(exhaustedToken);

            // When
            List<PasswordResetToken> activeTokens = tokenRepository.findActiveTokens(LocalDateTime.now());

            // Then
            assertThat(activeTokens).isEmpty(); // No active tokens because attempts are exhausted
        }

        @Test
        @DisplayName("Should handle multiple tokens for same contact")
        void shouldHandleMultipleTokensForSameContact() {
            // Given
            PasswordResetToken smsToken = PasswordResetToken.create(testUserId, TEST_EMAIL, PasswordResetToken.ResetMethod.SMS_CODE);
            tokenRepository.save(smsToken);

            // When
            List<PasswordResetToken> tokens = tokenRepository.findByContactValue(TEST_EMAIL);

            // Then
            assertThat(tokens).hasSize(2);
            assertThat(tokens).extracting(PasswordResetToken::getResetMethod)
                .containsExactlyInAnyOrder(
                    PasswordResetToken.ResetMethod.EMAIL_LINK,
                    PasswordResetToken.ResetMethod.SMS_CODE
                );
        }
        
        @Test
        @DisplayName("Should create and validate SMS token with phone number")
        void shouldCreateAndValidateSmsTokenWithPhone() {
            // Given
            PasswordResetToken phoneToken = PasswordResetToken.create(
                testUserId, 
                TEST_PHONE, 
                PasswordResetToken.ResetMethod.SMS_CODE
            );
            
            // When
            PasswordResetToken savedToken = tokenRepository.save(phoneToken);
            Optional<PasswordResetToken> foundToken = tokenRepository.findByToken(savedToken.getToken());

            // Then
            assertThat(foundToken).isPresent();
            assertThat(foundToken.get().getContactValue()).isEqualTo(TEST_PHONE);
            assertThat(foundToken.get().getResetMethod()).isEqualTo(PasswordResetToken.ResetMethod.SMS_CODE);
            assertThat(foundToken.get().getToken()).hasSize(6); // SMS codes are 6 digits
            assertThat(foundToken.get().isValid()).isTrue();
        }
    }
}
