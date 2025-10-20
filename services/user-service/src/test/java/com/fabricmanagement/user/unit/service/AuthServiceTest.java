package com.fabricmanagement.user.unit.service;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.domain.exception.PasswordNotSetException;
import com.fabricmanagement.shared.infrastructure.util.MaskingUtil;
import com.fabricmanagement.shared.security.jwt.JwtTokenProvider;
import com.fabricmanagement.user.api.dto.request.CheckContactRequest;
import com.fabricmanagement.user.api.dto.request.LoginRequest;
import com.fabricmanagement.user.api.dto.request.SetupPasswordRequest;
import com.fabricmanagement.user.api.dto.response.CheckContactResponse;
import com.fabricmanagement.user.api.dto.response.LoginResponse;
import com.fabricmanagement.user.application.mapper.AuthMapper;
import com.fabricmanagement.user.application.service.AuthService;
import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.fixtures.UserFixtures;
import com.fabricmanagement.user.infrastructure.audit.SecurityAuditLogger;
import com.fabricmanagement.user.infrastructure.client.ContactServiceClient;
import com.fabricmanagement.user.infrastructure.client.dto.ContactDto;
import com.fabricmanagement.user.infrastructure.repository.UserRepository;
import com.fabricmanagement.user.infrastructure.security.LoginAttemptTracker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for AuthService
 * 
 * Testing Strategy:
 * - Focus on critical paths: login, checkContact, setupPassword
 * - Mock external dependencies (ContactServiceClient, JWT, Redis)
 * - Test security patterns (password validation, account lockout)
 * 
 * Coverage Goal: 60%+ (critical paths only)
 * 
 * Note: Full auth flow tested in AuthControllerIT (integration test)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ContactServiceClient contactServiceClient;
    @Mock
    private AuthMapper authMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private LoginAttemptTracker loginAttemptTracker;
    @Mock
    private SecurityAuditLogger auditLogger;
    @Mock
    private MaskingUtil maskingUtil;

    @InjectMocks
    private AuthService authService;

    private static final UUID TEST_USER_ID = UUID.randomUUID();

    // ═════════════════════════════════════════════════════
    // CHECK CONTACT TESTS (Critical for UX)
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Check Contact Tests")
    class CheckContactTests {

        @Test
        @DisplayName("Should return exists=false when contact not found")
        void shouldReturnNotFound_whenContactNotFound() {
            // Given
            CheckContactRequest request = CheckContactRequest.builder()
                    .contactValue("unknown@test.com")
                    .build();

            CheckContactResponse notFoundResponse = CheckContactResponse.builder()
                    .exists(false)
                    .hasPassword(false)
                    .build();

            when(contactServiceClient.findByContactValue(anyString())).thenReturn(null);
            when(authMapper.toNotFoundResponse()).thenReturn(notFoundResponse);

            // When
            CheckContactResponse result = authService.checkContact(request);

            // Then
            assertThat(result.isExists()).isFalse();
            verify(contactServiceClient).findByContactValue("unknown@test.com");
            verify(authMapper).toNotFoundResponse();
        }

        @Test
        @DisplayName("Should return exists=true when user found")
        void shouldReturnExists_whenUserFound() {
            // Given
            CheckContactRequest request = CheckContactRequest.builder()
                    .contactValue("john@test.com")
                    .build();

            ContactDto contact = ContactDto.builder()
                    .id(UUID.randomUUID())
                    .ownerId(TEST_USER_ID)
                    .contactValue("john@test.com")
                    .isVerified(true)
                    .build();

            User user = UserFixtures.createActiveUser("John", "Doe", "john@test.com");
            user.setId(TEST_USER_ID);
            user.setPasswordHash("$2a$10$hashedPassword");

            CheckContactResponse expectedResponse = CheckContactResponse.builder()
                    .exists(true)
                    .hasPassword(true)
                    .verified(true)
                    .userId(TEST_USER_ID.toString())
                    .build();

            when(contactServiceClient.findByContactValue(anyString()))
                    .thenReturn(ApiResponse.success(contact));
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(authMapper.toCheckResponse(any(User.class))).thenReturn(expectedResponse);

            // When
            CheckContactResponse result = authService.checkContact(request);

            // Then
            assertThat(result.isExists()).isTrue();
            assertThat(result.isHasPassword()).isTrue();
            verify(contactServiceClient).findByContactValue("john@test.com");
            verify(userRepository).findById(TEST_USER_ID);
        }
    }

    // ═════════════════════════════════════════════════════
    // LOGIN TESTS (Most critical)
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Login Tests")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully with valid credentials")
        void shouldLogin_whenValidCredentials() {
            // Given
            LoginRequest request = LoginRequest.builder()
                    .contactValue("john@test.com")
                    .password("SecurePassword123!")
                    .build();

            ContactDto contact = ContactDto.builder()
                    .id(UUID.randomUUID())
                    .ownerId(TEST_USER_ID)
                    .contactValue("john@test.com")
                    .isVerified(true)
                    .build();

            User user = UserFixtures.createActiveUser("John", "Doe", "john@test.com");
            user.setId(TEST_USER_ID);
            user.setPasswordHash("$2a$10$hashedPassword");

            LoginResponse expectedResponse = LoginResponse.builder()
                    .accessToken("jwt-access-token")
                    .refreshToken("jwt-refresh-token")
                    .userId(TEST_USER_ID)
                    .build();

            when(contactServiceClient.findByContactValue(anyString()))
                    .thenReturn(ApiResponse.success(contact));
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
            when(jwtTokenProvider.generateToken(anyString(), anyString(), any())).thenReturn("jwt-access-token");
            when(jwtTokenProvider.generateRefreshToken(anyString(), anyString())).thenReturn("jwt-refresh-token");
            when(authMapper.toLoginResponse(any(User.class), any(ContactDto.class), anyString(), anyString()))
                    .thenReturn(expectedResponse);

            // When
            LoginResponse result = authService.login(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getAccessToken()).isEqualTo("jwt-access-token");
            verify(passwordEncoder).matches("SecurePassword123!", "$2a$10$hashedPassword");
            verify(userRepository).save(any(User.class)); // lastLoginAt update
            verify(loginAttemptTracker).clearFailedAttempts("john@test.com");
        }

        @Test
        @DisplayName("Should throw exception when password not set")
        void shouldThrowException_whenPasswordNotSet() {
            // Given
            LoginRequest request = LoginRequest.builder()
                    .contactValue("john@test.com")
                    .password("Password123!")
                    .build();

            ContactDto contact = ContactDto.builder()
                    .id(UUID.randomUUID())
                    .ownerId(TEST_USER_ID)
                    .contactValue("john@test.com")
                    .isVerified(true)
                    .build();

            User user = UserFixtures.createActiveUser("John", "Doe", "john@test.com");
            user.setId(TEST_USER_ID);
            user.setPasswordHash(null); // No password set

            when(contactServiceClient.findByContactValue(anyString()))
                    .thenReturn(ApiResponse.success(contact));
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

            // When & Then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(PasswordNotSetException.class);

            verify(passwordEncoder, never()).matches(anyString(), anyString());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when contact not found")
        void shouldThrowException_whenContactNotFound() {
            // Given
            LoginRequest request = LoginRequest.builder()
                    .contactValue("unknown@test.com")
                    .password("Password123!")
                    .build();

            when(contactServiceClient.findByContactValue(anyString())).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(com.fabricmanagement.shared.domain.exception.InvalidPasswordException.class);

            verify(loginAttemptTracker).recordFailedAttempt("unknown@test.com");
        }
    }

    // ═════════════════════════════════════════════════════
    // SETUP PASSWORD TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Setup Password Tests")
    class SetupPasswordTests {

        @Test
        @DisplayName("Should setup password when contact verified")
        void shouldSetupPassword_whenContactVerified() {
            // Given
            SetupPasswordRequest request = SetupPasswordRequest.builder()
                    .contactValue("john@test.com")
                    .password("SecurePassword123!")
                    .build();

            ContactDto contact = ContactDto.builder()
                    .id(UUID.randomUUID())
                    .ownerId(TEST_USER_ID)
                    .contactValue("john@test.com")
                    .isVerified(true)
                    .build();

            User user = UserFixtures.createActiveUser("John", "Doe", "john@test.com");
            user.setId(TEST_USER_ID);
            user.setPasswordHash(null); // No password yet

            when(contactServiceClient.findByContactValue(anyString()))
                    .thenReturn(ApiResponse.success(contact));
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$newHashedPassword");

            // When
            authService.setupPassword(request);

            // Then
            verify(passwordEncoder).encode("SecurePassword123!");
            verify(userRepository).save(any(User.class));
            verify(auditLogger).logPasswordSetup("john@test.com", TEST_USER_ID.toString());
        }
    }

    // ═════════════════════════════════════════════════════
    // SEND VERIFICATION CODE TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Send Verification Code Tests")
    class SendVerificationCodeTests {

        @Test
        @DisplayName("Should send verification code successfully")
        void shouldSendVerificationCode() {
            // Given
            com.fabricmanagement.user.api.dto.request.SendVerificationRequest request =
                    com.fabricmanagement.user.api.dto.request.SendVerificationRequest.builder()
                            .contactValue("john@test.com")
                            .build();

            ContactDto contact = ContactDto.builder()
                    .id(UUID.randomUUID())
                    .contactValue("john@test.com")
                    .build();

            when(contactServiceClient.findByContactValue(anyString()))
                    .thenReturn(ApiResponse.success(contact));

            // When
            authService.sendVerificationCode(request);

            // Then
            verify(contactServiceClient).sendVerificationCode(any(UUID.class));
        }
    }

    // ═════════════════════════════════════════════════════
    // SETUP PASSWORD WITH VERIFICATION TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Setup Password With Verification Tests")
    class SetupPasswordWithVerificationTests {

        @Test
        @DisplayName("Should setup password with verification code")
        void shouldSetupPasswordWithVerification() {
            // Given
            com.fabricmanagement.user.api.dto.request.SetupPasswordWithVerificationRequest request =
                    com.fabricmanagement.user.api.dto.request.SetupPasswordWithVerificationRequest.builder()
                            .contactValue("john@test.com")
                            .verificationCode("123456")
                            .password("SecurePassword123!")
                            .build();

            ContactDto contact = ContactDto.builder()
                    .id(UUID.randomUUID())
                    .ownerId(TEST_USER_ID)
                    .contactValue("john@test.com")
                    .isVerified(false)
                    .build();

            User user = UserFixtures.createActiveUser("John", "Doe", "john@test.com");
            user.setId(TEST_USER_ID);
            user.setPasswordHash(null);

            when(contactServiceClient.findByContactValue(anyString()))
                    .thenReturn(ApiResponse.success(contact));
            when(contactServiceClient.verifyContact(any(UUID.class), anyString()))
                    .thenReturn(ApiResponse.success(contact));
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$newHashedPassword");

            // When
            authService.setupPasswordWithVerification(request);

            // Then
            verify(contactServiceClient).verifyContact(any(UUID.class), eq("123456"));
            verify(passwordEncoder).encode("SecurePassword123!");
            verify(userRepository).save(any(User.class));
        }
    }
}

