package com.fabricmanagement.user.unit.service;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.domain.exception.TenantRegistrationException;
import com.fabricmanagement.shared.infrastructure.util.EmailValidationUtil;
import com.fabricmanagement.shared.infrastructure.util.MaskingUtil;
import com.fabricmanagement.user.api.dto.request.TenantRegistrationRequest;
import com.fabricmanagement.user.application.service.TenantOnboardingService;
import com.fabricmanagement.user.infrastructure.client.CompanyServiceClient;
import com.fabricmanagement.user.infrastructure.client.ContactServiceClient;
import com.fabricmanagement.user.infrastructure.client.dto.CompanyDuplicateCheckResult;
import com.fabricmanagement.user.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for TenantOnboardingService
 * 
 * Testing Strategy:
 * - Focus on validation methods (easy to test, high value)
 * - registerTenant() tested in E2E (complex orchestration)
 * - Mock external services (Feign clients)
 * 
 * Coverage Goal: 40%+ (validation paths)
 * 
 * Note: Full onboarding flow tested in OnboardingControllerIT
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantOnboardingService Unit Tests")
class TenantOnboardingServiceTest {

    @Mock
    private CompanyServiceClient companyServiceClient;
    @Mock
    private ContactServiceClient contactServiceClient;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailValidationUtil emailValidationUtil;
    @Mock
    private MaskingUtil maskingUtil;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private TenantOnboardingService service;

    // ═════════════════════════════════════════════════════
    // CORPORATE EMAIL VALIDATION TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Corporate Email Validation Tests")
    class CorporateEmailValidationTests {

        @Test
        @DisplayName("Should accept corporate email")
        void shouldAcceptCorporateEmail() throws Exception {
            // Given
            when(emailValidationUtil.isCorporateEmail(anyString())).thenReturn(true);

            // When - Call private method via reflection
            java.lang.reflect.Method method = TenantOnboardingService.class
                    .getDeclaredMethod("validateCorporateEmail", String.class);
            method.setAccessible(true);

            // Then
            assertThatCode(() -> method.invoke(service, "admin@acmetekstil.com"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject personal email (Gmail)")
        void shouldRejectPersonalEmail() throws Exception {
            // Given
            when(emailValidationUtil.isCorporateEmail(anyString())).thenReturn(false);
            when(emailValidationUtil.getCorporateEmailErrorMessage(anyString()))
                    .thenReturn("Personal email not allowed");

            // When - Call private method via reflection
            java.lang.reflect.Method method = TenantOnboardingService.class
                    .getDeclaredMethod("validateCorporateEmail", String.class);
            method.setAccessible(true);

            // Then
            assertThatThrownBy(() -> method.invoke(service, "user@gmail.com"))
                    .hasCauseInstanceOf(TenantRegistrationException.class);
        }
    }

    // ═════════════════════════════════════════════════════
    // EMAIL UNIQUENESS VALIDATION TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Email Uniqueness Validation Tests")
    class EmailUniquenessValidationTests {

        @Test
        @DisplayName("Should pass when email is available")
        void shouldPass_whenEmailAvailable() throws Exception {
            // Given
            when(contactServiceClient.checkAvailability(anyString()))
                    .thenReturn(ApiResponse.success(true));

            // When - Call private method via reflection
            java.lang.reflect.Method method = TenantOnboardingService.class
                    .getDeclaredMethod("validateEmailUniqueness", String.class);
            method.setAccessible(true);

            // Then
            assertThatCode(() -> method.invoke(service, "admin@acme.com"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should throw exception when email already registered")
        void shouldThrowException_whenEmailTaken() throws Exception {
            // Given
            when(contactServiceClient.checkAvailability(anyString()))
                    .thenReturn(ApiResponse.success(false));

            // When - Call private method via reflection
            java.lang.reflect.Method method = TenantOnboardingService.class
                    .getDeclaredMethod("validateEmailUniqueness", String.class);
            method.setAccessible(true);

            // Then
            assertThatThrownBy(() -> method.invoke(service, "taken@acme.com"))
                    .hasCauseInstanceOf(TenantRegistrationException.class);
        }
    }

    // ═════════════════════════════════════════════════════
    // EMAIL DOMAIN UNIQUENESS VALIDATION TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Email Domain Uniqueness Validation Tests")
    class EmailDomainUniquenessValidationTests {

        @Test
        @DisplayName("Should pass when domain not registered")
        void shouldPass_whenDomainNotRegistered() throws Exception {
            // Given
            when(emailValidationUtil.extractDomain(anyString())).thenReturn("acmetekstil.com");
            when(contactServiceClient.checkEmailDomain(anyString()))
                    .thenReturn(ApiResponse.success(Collections.emptyList()));

            // When - Call private method via reflection
            java.lang.reflect.Method method = TenantOnboardingService.class
                    .getDeclaredMethod("validateEmailDomainUniqueness", String.class);
            method.setAccessible(true);

            // Then
            assertThatCode(() -> method.invoke(service, "admin@acmetekstil.com"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should throw exception when domain already registered")
        void shouldThrowException_whenDomainRegistered() throws Exception {
            // Given
            when(emailValidationUtil.extractDomain(anyString())).thenReturn("acmetekstil.com");
            when(contactServiceClient.checkEmailDomain(anyString()))
                    .thenReturn(ApiResponse.success(List.of(UUID.randomUUID())));
            when(maskingUtil.maskEmail(anyString())).thenReturn("adm***@acmetekstil.com");

            // When - Call private method via reflection
            java.lang.reflect.Method method = TenantOnboardingService.class
                    .getDeclaredMethod("validateEmailDomainUniqueness", String.class);
            method.setAccessible(true);

            // Then
            assertThatThrownBy(() -> method.invoke(service, "admin@acmetekstil.com"))
                    .hasCauseInstanceOf(TenantRegistrationException.class);
        }

        @Test
        @DisplayName("Should handle null domain gracefully")
        void shouldHandleNullDomain() throws Exception {
            // Given
            when(emailValidationUtil.extractDomain(anyString())).thenReturn(null);

            // When - Call private method via reflection
            java.lang.reflect.Method method = TenantOnboardingService.class
                    .getDeclaredMethod("validateEmailDomainUniqueness", String.class);
            method.setAccessible(true);

            // Then - Should not throw, just return early
            assertThatCode(() -> method.invoke(service, "invalid-email"))
                    .doesNotThrowAnyException();
        }
    }

    // ═════════════════════════════════════════════════════
    // EMAIL DOMAIN MATCH VALIDATION TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Email Domain Match Validation Tests")
    class EmailDomainMatchValidationTests {

        @Test
        @DisplayName("Should skip validation when website is null")
        void shouldSkipValidation_whenWebsiteNull() throws Exception {
            // When - Call private method via reflection
            java.lang.reflect.Method method = TenantOnboardingService.class
                    .getDeclaredMethod("validateEmailDomainMatch", String.class, String.class);
            method.setAccessible(true);

            // Then
            assertThatCode(() -> method.invoke(service, "admin@acme.com", null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should skip validation when website is blank")
        void shouldSkipValidation_whenWebsiteBlank() throws Exception {
            // When - Call private method via reflection
            java.lang.reflect.Method method = TenantOnboardingService.class
                    .getDeclaredMethod("validateEmailDomainMatch", String.class, String.class);
            method.setAccessible(true);

            // Then
            assertThatCode(() -> method.invoke(service, "admin@acme.com", ""))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should warn when domains mismatch (soft validation)")
        void shouldWarn_whenDomainsMismatch() throws Exception {
            // Given
            when(emailValidationUtil.emailMatchesCompanyDomain(anyString(), anyString()))
                    .thenReturn(false);
            when(emailValidationUtil.extractDomain(anyString())).thenReturn("acme.com");
            when(emailValidationUtil.cleanDomain(anyString())).thenReturn("different.com");

            // When - Call private method via reflection
            java.lang.reflect.Method method = TenantOnboardingService.class
                    .getDeclaredMethod("validateEmailDomainMatch", String.class, String.class);
            method.setAccessible(true);

            // Then - Should NOT throw (soft validation)
            assertThatCode(() -> method.invoke(service, "admin@acme.com", "https://different.com"))
                    .doesNotThrowAnyException();
        }
    }

    // ═════════════════════════════════════════════════════
    // COMPANY UNIQUENESS VALIDATION TESTS
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Company Uniqueness Validation Tests")
    class CompanyUniquenessValidationTests {

        @Test
        @DisplayName("Should pass when company not duplicate")
        void shouldPass_whenCompanyNotDuplicate() throws Exception {
            // Given
            TenantRegistrationRequest request = TenantRegistrationRequest.builder()
                    .companyName("Acme Tekstil")
                    .legalName("Acme Tekstil A.Ş.")
                    .country("Turkey")
                    .taxId("1234567890")
                    .build();

            when(companyServiceClient.checkDuplicate(any()))
                    .thenReturn(ApiResponse.success(CompanyDuplicateCheckResult.builder()
                            .isDuplicate(false)
                            .build()));

            // When - Call private method via reflection
            java.lang.reflect.Method method = TenantOnboardingService.class
                    .getDeclaredMethod("validateCompanyUniqueness", TenantRegistrationRequest.class);
            method.setAccessible(true);

            // Then
            assertThatCode(() -> method.invoke(service, request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should throw exception when company is duplicate")
        void shouldThrowException_whenCompanyDuplicate() throws Exception {
            // Given
            TenantRegistrationRequest request = TenantRegistrationRequest.builder()
                    .companyName("Acme Tekstil")
                    .taxId("1234567890")
                    .build();

            when(companyServiceClient.checkDuplicate(any()))
                    .thenReturn(ApiResponse.success(CompanyDuplicateCheckResult.builder()
                            .isDuplicate(true)
                            .matchType("TAX_ID")
                            .matchedTaxId("1234567890")
                            .build()));

            // When - Call private method via reflection
            java.lang.reflect.Method method = TenantOnboardingService.class
                    .getDeclaredMethod("validateCompanyUniqueness", TenantRegistrationRequest.class);
            method.setAccessible(true);

            // Then
            assertThatThrownBy(() -> method.invoke(service, request))
                    .hasCauseInstanceOf(TenantRegistrationException.class);
        }
    }
}

