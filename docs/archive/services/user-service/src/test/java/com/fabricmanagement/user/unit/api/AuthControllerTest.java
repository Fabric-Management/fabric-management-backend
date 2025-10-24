package com.fabricmanagement.user.unit.api;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.user.api.AuthController;
import com.fabricmanagement.user.api.dto.request.CheckContactRequest;
import com.fabricmanagement.user.api.dto.request.LoginRequest;
import com.fabricmanagement.user.api.dto.request.SendVerificationRequest;
import com.fabricmanagement.user.api.dto.request.SetupPasswordRequest;
import com.fabricmanagement.user.api.dto.request.SetupPasswordWithVerificationRequest;
import com.fabricmanagement.user.api.dto.response.CheckContactResponse;
import com.fabricmanagement.user.api.dto.response.LoginResponse;
import com.fabricmanagement.user.application.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit Tests for AuthController
 * 
 * Tests authentication and authorization endpoints
 * 
 * Strategy:
 * - Mock AuthService
 * - Test public endpoints (no auth required)
 * - Verify API contracts
 * 
 * Coverage Goal: 85%+
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController - Unit Tests")
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    // ═══════════════════════════════════════════════════════
    // CHECK CONTACT TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Check Contact Tests")
    class CheckContactTests {

        @Test
        @DisplayName("Should check contact and return response")
        @SuppressWarnings("null")
        void shouldCheckContact() {
            // Given
            CheckContactRequest request = CheckContactRequest.builder()
                    .contactValue("john@test.com")
                    .build();

            CheckContactResponse expectedResponse = CheckContactResponse.builder()
                    .exists(true)
                    .hasPassword(true)
                    .verified(true)
                    .userId(UUID.randomUUID().toString())
                    .build();

            when(authService.checkContact(any())).thenReturn(expectedResponse);

            // When
            ResponseEntity<ApiResponse<CheckContactResponse>> response = 
                authController.checkContact(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSuccess()).isTrue();
            assertThat(response.getBody().getData()).isEqualTo(expectedResponse);
            verify(authService).checkContact(request);
        }

        @Test
        @DisplayName("Should return exists=false for new contact")
        @SuppressWarnings("null")
        void shouldReturnFalse_whenContactNotFound() {
            // Given
            CheckContactRequest request = CheckContactRequest.builder()
                    .contactValue("newuser@test.com")
                    .build();

            CheckContactResponse expectedResponse = CheckContactResponse.builder()
                    .exists(false)
                    .hasPassword(false)
                    .verified(false)
                    .build();

            when(authService.checkContact(any())).thenReturn(expectedResponse);

            // When
            ResponseEntity<ApiResponse<CheckContactResponse>> response = 
                authController.checkContact(request);

            // Then
            assertThat(response.getBody().getData().isExists()).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════
    // SETUP PASSWORD TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Setup Password Tests")
    class SetupPasswordTests {

        @Test
        @DisplayName("Should setup password and return success")
        @SuppressWarnings("null")
        void shouldSetupPassword() {
            // Given
            SetupPasswordRequest request = SetupPasswordRequest.builder()
                    .contactValue("john@test.com")
                    .password("SecurePassword123!")
                    .build();

            // When
            ResponseEntity<ApiResponse<Void>> response = 
                authController.setupPassword(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSuccess()).isTrue();
            verify(authService).setupPassword(request);
        }
    }

    // ═══════════════════════════════════════════════════════
    // LOGIN TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Login Tests")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully and return tokens")
        @SuppressWarnings("null")
        void shouldLoginSuccessfully() {
            // Given
            LoginRequest request = LoginRequest.builder()
                    .contactValue("john@test.com")
                    .password("SecurePassword123!")
                    .build();

            LoginResponse expectedResponse = LoginResponse.builder()
                    .accessToken("jwt-access-token")
                    .refreshToken("jwt-refresh-token")
                    .userId(UUID.randomUUID())
                    .build();

            when(authService.login(any())).thenReturn(expectedResponse);

            // When
            ResponseEntity<ApiResponse<LoginResponse>> response = 
                authController.login(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSuccess()).isTrue();
            assertThat(response.getBody().getData()).isEqualTo(expectedResponse);
            assertThat(response.getBody().getData().getAccessToken()).isEqualTo("jwt-access-token");
            verify(authService).login(request);
        }
    }

    // ═══════════════════════════════════════════════════════
    // SEND VERIFICATION TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Send Verification Tests")
    class SendVerificationTests {

        @Test
        @DisplayName("Should send verification code")
        @SuppressWarnings("null")
        void shouldSendVerificationCode() {
            // Given
            SendVerificationRequest request = SendVerificationRequest.builder()
                    .contactValue("john@test.com")
                    .build();

            // When
            ResponseEntity<ApiResponse<Void>> response = 
                authController.sendVerification(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSuccess()).isTrue();
            verify(authService).sendVerificationCode(request);
        }
    }

    // ═══════════════════════════════════════════════════════
    // SETUP PASSWORD WITH VERIFICATION TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Setup Password With Verification Tests")
    class SetupPasswordWithVerificationTests {

        @Test
        @DisplayName("Should setup password with verification and login")
        @SuppressWarnings("null")
        void shouldSetupPasswordWithVerification() {
            // Given
            SetupPasswordWithVerificationRequest request = SetupPasswordWithVerificationRequest.builder()
                    .contactValue("john@test.com")
                    .verificationCode("123456")
                    .password("SecurePassword123!")
                    .build();

            LoginResponse expectedResponse = LoginResponse.builder()
                    .accessToken("jwt-token")
                    .refreshToken("refresh-token")
                    .userId(UUID.randomUUID())
                    .build();

            when(authService.setupPasswordWithVerification(any())).thenReturn(expectedResponse);

            // When
            ResponseEntity<ApiResponse<LoginResponse>> response = 
                authController.setupPasswordWithVerification(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSuccess()).isTrue();
            assertThat(response.getBody().getData()).isEqualTo(expectedResponse);
            verify(authService).setupPasswordWithVerification(request);
        }
    }
}

