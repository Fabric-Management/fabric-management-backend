package com.fabricmanagement.common.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.auth.domain.VerificationCode;
import com.fabricmanagement.common.platform.auth.domain.VerificationType;
import com.fabricmanagement.common.platform.auth.infra.repository.VerificationCodeRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationCodeService")
class VerificationCodeServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final String CONTACT = "user@example.com";
  private static final VerificationType TYPE = VerificationType.REGISTRATION;

  @Mock private VerificationCodeRepository verificationCodeRepository;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private VerificationCodeService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    ReflectionTestUtils.setField(service, "codeLength", 6);
    ReflectionTestUtils.setField(service, "codeExpiryMinutes", 10);
    ReflectionTestUtils.setField(service, "maxAttempts", 3);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Nested
  @DisplayName("generate")
  class Generate {

    @Test
    void deletesExistingCodesThenSavesNewCodeAndReturnsRawCodeAndExpiry() {
      when(passwordEncoder.encode(any(String.class))).thenReturn("hashed");

      VerificationCodeManager.IssuedVerificationCode result = service.generate(CONTACT, TYPE);

      verify(verificationCodeRepository)
          .deleteByTenantIdAndContactValueAndType(TENANT_ID, CONTACT, TYPE);
      ArgumentCaptor<VerificationCode> captor = ArgumentCaptor.forClass(VerificationCode.class);
      verify(verificationCodeRepository).save(captor.capture());
      VerificationCode saved = captor.getValue();
      assertThat(saved.getContactValue()).isEqualTo(CONTACT);
      assertThat(saved.getType()).isEqualTo(TYPE);
      assertThat(saved.getCodeHash()).isEqualTo("hashed");
      assertThat(result.code()).hasSize(6);
      assertThat(result.expiresAt()).isAfter(Instant.now());
    }
  }

  @Nested
  @DisplayName("validateAndConsume")
  class ValidateAndConsume {

    @Test
    void throwsWhenCodeNotFound() {
      when(verificationCodeRepository.findTopByTenantIdAndContactValueAndTypeOrderByCreatedAtDesc(
              TENANT_ID, CONTACT, TYPE))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.validateAndConsume(CONTACT, TYPE, "123456"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("invalid or expired");
    }

    @Test
    void throwsWhenCodeExpired() {
      VerificationCode code =
          VerificationCode.builder()
              .contactValue(CONTACT)
              .codeHash("hash")
              .type(TYPE)
              .expiresAt(Instant.now().minusSeconds(60))
              .isUsed(false)
              .attemptCount(0)
              .build();
      when(verificationCodeRepository.findTopByTenantIdAndContactValueAndTypeOrderByCreatedAtDesc(
              TENANT_ID, CONTACT, TYPE))
          .thenReturn(Optional.of(code));

      assertThatThrownBy(() -> service.validateAndConsume(CONTACT, TYPE, "123456"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("expired");
      verify(verificationCodeRepository).save(code);
    }

    @Test
    void throwsWhenCodeAlreadyUsed() {
      VerificationCode code =
          VerificationCode.builder()
              .contactValue(CONTACT)
              .codeHash("hash")
              .type(TYPE)
              .expiresAt(Instant.now().plusSeconds(600))
              .isUsed(true)
              .attemptCount(0)
              .build();
      when(verificationCodeRepository.findTopByTenantIdAndContactValueAndTypeOrderByCreatedAtDesc(
              TENANT_ID, CONTACT, TYPE))
          .thenReturn(Optional.of(code));

      assertThatThrownBy(() -> service.validateAndConsume(CONTACT, TYPE, "123456"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("already been used");
    }

    @Test
    void marksAsUsedWhenCodeMatches() {
      VerificationCode code =
          VerificationCode.builder()
              .contactValue(CONTACT)
              .codeHash("hash")
              .type(TYPE)
              .expiresAt(Instant.now().plusSeconds(600))
              .isUsed(false)
              .attemptCount(0)
              .build();
      when(verificationCodeRepository.findTopByTenantIdAndContactValueAndTypeOrderByCreatedAtDesc(
              TENANT_ID, CONTACT, TYPE))
          .thenReturn(Optional.of(code));
      when(passwordEncoder.matches(eq("123456"), eq("hash"))).thenReturn(true);

      service.validateAndConsume(CONTACT, TYPE, "123456");

      verify(verificationCodeRepository).save(code);
      assertThat(code.getIsUsed()).isTrue();
    }

    @Test
    void throwsWhenRawCodeDoesNotMatch() {
      VerificationCode code =
          VerificationCode.builder()
              .contactValue(CONTACT)
              .codeHash("hash")
              .type(TYPE)
              .expiresAt(Instant.now().plusSeconds(600))
              .isUsed(false)
              .attemptCount(0)
              .build();
      when(verificationCodeRepository.findTopByTenantIdAndContactValueAndTypeOrderByCreatedAtDesc(
              TENANT_ID, CONTACT, TYPE))
          .thenReturn(Optional.of(code));
      when(passwordEncoder.matches(eq("123456"), eq("hash"))).thenReturn(false);

      assertThatThrownBy(() -> service.validateAndConsume(CONTACT, TYPE, "123456"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("invalid or expired");
      verify(verificationCodeRepository).save(code);
      assertThat(code.getAttemptCount()).isEqualTo(1);
    }
  }
}
