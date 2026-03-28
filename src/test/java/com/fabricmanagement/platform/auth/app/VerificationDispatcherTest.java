package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.domain.VerificationType;
import com.fabricmanagement.platform.communication.app.VerificationService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationDispatcher")
class VerificationDispatcherTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final String CONTACT = "user@example.com";
  private static final VerificationType TYPE = VerificationType.PASSWORD_RESET;

  @Mock private VerificationThrottleService throttleService;
  @Mock private VerificationCodeService codeService;
  @Mock private VerificationService verificationService;

  @InjectMocks private VerificationDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("sendVerificationCode checks throttle, generates code, sends via communication")
  void sendVerificationCodeFlow() {
    VerificationCodeManager.IssuedVerificationCode issued =
        new VerificationCodeManager.IssuedVerificationCode(
            "123456", Instant.now().plusSeconds(600));
    when(codeService.generate(CONTACT, TYPE)).thenReturn(issued);

    VerificationCodeManager.IssuedVerificationCode result =
        dispatcher.sendVerificationCode(CONTACT, TYPE);

    verify(throttleService).checkThrottle(CONTACT, TENANT_ID, TYPE);
    verify(codeService).generate(CONTACT, TYPE);
    verify(verificationService)
        .sendVerificationCode(
            eq(CONTACT),
            eq("123456"),
            eq(TENANT_ID),
            eq(UUID.fromString("00000000-0000-0000-0000-000000000000")),
            eq(TYPE));
    assertThat(result.code()).isEqualTo("123456");
    assertThat(result.expiresAt()).isAfter(Instant.now());
  }
}
