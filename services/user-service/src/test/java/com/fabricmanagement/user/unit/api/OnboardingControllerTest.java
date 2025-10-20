package com.fabricmanagement.user.unit.api;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.user.api.OnboardingController;
import com.fabricmanagement.user.api.dto.request.TenantRegistrationRequest;
import com.fabricmanagement.user.api.dto.response.TenantOnboardingResponse;
import com.fabricmanagement.user.application.service.TenantOnboardingService;
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
 * Unit Tests for OnboardingController
 * 
 * Tests tenant registration public endpoint
 * 
 * Strategy:
 * - Mock TenantOnboardingService
 * - Test public endpoint (no auth)
 * - Verify API contracts
 * 
 * Coverage Goal: 85%+
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OnboardingController - Unit Tests")
class OnboardingControllerTest {

    @Mock
    private TenantOnboardingService onboardingService;

    @InjectMocks
    private OnboardingController onboardingController;

    // ═══════════════════════════════════════════════════════
    // REGISTER TENANT TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Register Tenant Tests")
    class RegisterTenantTests {

        @Test
        @DisplayName("Should register tenant and return 201")
        @SuppressWarnings("null")
        void shouldRegisterTenant() {
            // Given
            TenantRegistrationRequest request = TenantRegistrationRequest.builder()
                    .companyName("Test Company")
                    .firstName("John")
                    .lastName("Doe")
                    .email("admin@testcompany.com")
                    .companyType("MANUFACTURER")
                    .industry("TEXTILE")
                    .taxId("1234567890")
                    .addressLine1("Test Street 123")
                    .city("Istanbul")
                    .build();

            TenantOnboardingResponse expectedResponse = TenantOnboardingResponse.builder()
                    .companyId(UUID.randomUUID())
                    .userId(UUID.randomUUID())
                    .email("admin@testcompany.com")
                    .message("Tenant registered successfully")
                    .nextStep("setup-password")
                    .build();

            when(onboardingService.registerTenant(any())).thenReturn(expectedResponse);

            // When
            ResponseEntity<ApiResponse<TenantOnboardingResponse>> response = 
                onboardingController.registerTenant(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSuccess()).isTrue();
            assertThat(response.getBody().getData()).isEqualTo(expectedResponse);
            verify(onboardingService).registerTenant(request);
        }

        @Test
        @DisplayName("Should handle complete tenant registration data")
        @SuppressWarnings("null")
        void shouldHandleCompleteTenantData() {
            // Given
            TenantRegistrationRequest request = TenantRegistrationRequest.builder()
                    .companyName("Complete Company")
                    .firstName("Admin")
                    .lastName("User")
                    .email("admin@complete.com")
                    .phone("+905551234567")
                    .taxId("1234567890")
                    .companyType("MANUFACTURER")
                    .industry("TEXTILE")
                    .addressLine1("Street 1")
                    .city("Istanbul")
                    .build();

            TenantOnboardingResponse expectedResponse = TenantOnboardingResponse.builder()
                    .companyId(UUID.randomUUID())
                    .userId(UUID.randomUUID())
                    .email("admin@complete.com")
                    .message("Registration successful")
                    .nextStep("verify")
                    .build();

            when(onboardingService.registerTenant(any())).thenReturn(expectedResponse);

            // When
            ResponseEntity<ApiResponse<TenantOnboardingResponse>> response = 
                onboardingController.registerTenant(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().getSuccess()).isTrue();
            verify(onboardingService).registerTenant(request);
        }
    }
}

