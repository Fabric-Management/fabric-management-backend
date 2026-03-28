package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.auth.domain.VerificationType;
import com.fabricmanagement.platform.auth.infra.repository.VerificationCodeRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationThrottleService")
class VerificationThrottleServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final String CONTACT = "user@example.com";
  private static final VerificationType TYPE = VerificationType.REGISTRATION;

  @Mock private VerificationCodeRepository verificationCodeRepository;

  @InjectMocks private VerificationThrottleService service;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "maxPerContactWindow", 5);
    ReflectionTestUtils.setField(service, "contactWindowSeconds", 600);
    ReflectionTestUtils.setField(service, "maxPerTenantWindow", 100);
    ReflectionTestUtils.setField(service, "maxGlobalWindow", 1000);
    ReflectionTestUtils.setField(service, "throttleWindowSeconds", 600);
  }

  @Nested
  @DisplayName("checkThrottle")
  class CheckThrottle {

    @Test
    void passesWhenUnderAllLimits() {
      when(verificationCodeRepository.countByTenantIdAndContactValueAndTypeAndCreatedAtAfter(
              eq(TENANT_ID), eq(CONTACT), eq(TYPE), any(Instant.class)))
          .thenReturn(2L);
      when(verificationCodeRepository.countByTenantIdAndTypeAndCreatedAtAfter(
              eq(TENANT_ID), eq(TYPE), any(Instant.class)))
          .thenReturn(50L);
      when(verificationCodeRepository.countByCreatedAtAfter(any(Instant.class))).thenReturn(500L);

      service.checkThrottle(CONTACT, TENANT_ID, TYPE);

      verify(verificationCodeRepository)
          .countByTenantIdAndContactValueAndTypeAndCreatedAtAfter(
              eq(TENANT_ID), eq(CONTACT), eq(TYPE), any(Instant.class));
    }

    @Test
    void throwsWhenContactLimitExceeded() {
      when(verificationCodeRepository.countByTenantIdAndContactValueAndTypeAndCreatedAtAfter(
              eq(TENANT_ID), eq(CONTACT), eq(TYPE), any(Instant.class)))
          .thenReturn(5L);

      assertThatThrownBy(() -> service.checkThrottle(CONTACT, TENANT_ID, TYPE))
          .isInstanceOf(
              com.fabricmanagement.platform.common.exception.PlatformDomainException.class)
          .hasMessageContaining("Too many verification requests");
    }

    @Test
    void throwsWhenTenantLimitExceeded() {
      when(verificationCodeRepository.countByTenantIdAndContactValueAndTypeAndCreatedAtAfter(
              eq(TENANT_ID), eq(CONTACT), eq(TYPE), any(Instant.class)))
          .thenReturn(2L);
      when(verificationCodeRepository.countByTenantIdAndTypeAndCreatedAtAfter(
              eq(TENANT_ID), eq(TYPE), any(Instant.class)))
          .thenReturn(100L);

      assertThatThrownBy(() -> service.checkThrottle(CONTACT, TENANT_ID, TYPE))
          .isInstanceOf(
              com.fabricmanagement.platform.common.exception.PlatformDomainException.class)
          .hasMessageContaining("temporarily limited");
    }

    @Test
    void throwsWhenGlobalLimitExceeded() {
      when(verificationCodeRepository.countByTenantIdAndContactValueAndTypeAndCreatedAtAfter(
              eq(TENANT_ID), eq(CONTACT), eq(TYPE), any(Instant.class)))
          .thenReturn(2L);
      when(verificationCodeRepository.countByTenantIdAndTypeAndCreatedAtAfter(
              eq(TENANT_ID), eq(TYPE), any(Instant.class)))
          .thenReturn(50L);
      when(verificationCodeRepository.countByCreatedAtAfter(any(Instant.class))).thenReturn(1000L);

      assertThatThrownBy(() -> service.checkThrottle(CONTACT, TENANT_ID, TYPE))
          .isInstanceOf(
              com.fabricmanagement.platform.common.exception.PlatformDomainException.class)
          .hasMessageContaining("busy");
    }
  }
}
