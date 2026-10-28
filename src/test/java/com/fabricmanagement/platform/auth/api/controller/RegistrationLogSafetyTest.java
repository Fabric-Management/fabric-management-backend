package com.fabricmanagement.platform.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.AuthCookieSupport;
import com.fabricmanagement.common.infrastructure.web.exception.GlobalExceptionHandler;
import com.fabricmanagement.platform.auth.app.PasswordResetService;
import com.fabricmanagement.platform.auth.app.PasswordSetupService;
import com.fabricmanagement.platform.auth.app.TenantOnboardingService;
import com.fabricmanagement.platform.auth.dto.LoginResponse;
import com.fabricmanagement.platform.auth.dto.TenantOnboardingResponse;
import com.fabricmanagement.testsupport.SensitiveLogCapture;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Logging regression tests; existing controller/security tests own endpoint authorisation. */
@ExtendWith(MockitoExtension.class)
class RegistrationLogSafetyTest {
  private static final String TOKEN = "b24788a1-62fe-40ad-899f-80ea7543a1cc";
  private static final String URL = "https://app.example.com/setup?token=" + TOKEN;
  private static final String PASSWORD = "Hawthorn!Secret27";
  private static final String PUBLIC_BODY =
      """
      {"organizationName":"Hawthorn Weaving Ltd","taxId":"GB123456789",
       "organizationType":"WEAVER","firstName":"Eleanor","lastName":"Hughes",
       "email":"eleanor.hughes@example.com","acceptedTerms":true}
      """;
  private static final String ADMIN_BODY =
      """
      {"organizationName":"Hawthorn Weaving Ltd","taxId":"GB123456789",
       "organizationType":"WEAVER","adminFirstName":"Eleanor","adminLastName":"Hughes",
       "adminContact":"eleanor.hughes@example.com"}
      """;

  @Mock private TenantOnboardingService onboarding;
  @Mock private PasswordSetupService setup;
  @Mock private PasswordResetService reset;
  @Mock private AuthCookieSupport cookies;
  @Mock private ObjectProvider<MeterRegistry> meters;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                new PublicSignupController(onboarding),
                new TenantOnboardingController(onboarding),
                new PasswordController(setup, reset, cookies))
            .setControllerAdvice(new GlobalExceptionHandler(meters))
            .build();
  }

  @Test
  void publicResponseIsGenericWhileAuthorisedSalesResponseKeepsItsSetupLink() throws Exception {
    var response =
        TenantOnboardingResponse.builder()
            .tenantId(UUID.randomUUID())
            .organizationId(UUID.randomUUID())
            .adminUserId(UUID.randomUUID())
            .setupUrl(URL)
            .registrationToken(TOKEN)
            .build();
    when(onboarding.createSelfServiceTenant(any())).thenReturn(response);
    when(onboarding.createSalesLedTenant(any())).thenReturn(response);
    try (var logs = capture()) {
      String publicJson =
          mvc.perform(
                  post("/api/v1/public/signup")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(PUBLIC_BODY))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(publicJson).doesNotContain(TOKEN, URL, "setupUrl", "registrationToken");
      mvc.perform(
              post("/api/v1/admin/onboarding/tenant")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(ADMIN_BODY))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.setupUrl").value(URL));
      assertSafe(logs);
      assertThat(logs.text())
          .contains(
              response.getTenantId().toString(), "signup completed", "onboarded successfully");
    }
  }

  @Test
  void successfulSetupControllerLogsNoTokenPrefixOrPassword() throws Exception {
    when(setup.setupPassword(any(), any()))
        .thenReturn(LoginResponse.builder().needsOnboarding(true).build());
    try (var logs = capture()) {
      mvc.perform(
              post("/api/v1/auth/setup-password")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"token\":\"" + TOKEN + "\",\"password\":\"" + PASSWORD + "\"}"))
          .andExpect(status().isOk());
      assertSafe(logs);
      assertThat(logs.text()).contains("Password setup requested");
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/v1/public/signup",
        "/api/v1/admin/onboarding/tenant",
        "/api/v1/auth/setup-password"
      })
  void downstreamFailureCannotLeakThroughTheHttpExceptionLogger(String path) throws Exception {
    var failure =
        new DataAccessResourceFailureException(
            "provider echoed " + URL, new IllegalStateException(PASSWORD));
    failure.addSuppressed(new IllegalArgumentException(TOKEN));
    String body;
    if (path.endsWith("setup-password")) {
      when(setup.setupPassword(any(), any())).thenThrow(failure);
      body = "{\"token\":\"" + TOKEN + "\",\"password\":\"" + PASSWORD + "\"}";
    } else if (path.contains("admin")) {
      when(onboarding.createSalesLedTenant(any())).thenThrow(failure);
      body = ADMIN_BODY;
    } else {
      when(onboarding.createSelfServiceTenant(any())).thenThrow(failure);
      body = PUBLIC_BODY;
    }
    try (var logs = capture()) {
      mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.code").value("SERVICE_TEMPORARILY_UNAVAILABLE"));
      assertSafe(logs);
      assertThat(logs.text())
          .contains("failureType=", "DataAccessResourceFailureException", "traceId=");
    }
  }

  @Test
  void malformedSensitiveRequestDoesNotLogJacksonSourcePayload() throws Exception {
    try (var logs = capture()) {
      mvc.perform(
              post("/api/v1/auth/setup-password")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"token\":\"" + TOKEN + "\",\"password\":\"" + PASSWORD + "\", broken}"))
          .andExpect(status().isBadRequest());
      assertSafe(logs);
    }
  }

  @Test
  void genericSetupFailureKeepsItsTraceWithoutItsSecretExceptionChain() throws Exception {
    when(setup.setupPassword(any(), any()))
        .thenThrow(new RuntimeException(URL, new IllegalArgumentException(PASSWORD)));
    try (var logs = capture()) {
      mvc.perform(
              post("/api/v1/auth/setup-password")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"token\":\"" + TOKEN + "\",\"password\":\"" + PASSWORD + "\"}"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("UNEXPECTED_ERROR"));
      assertSafe(logs);
      assertThat(logs.text()).contains("failureType=java.lang.RuntimeException", "traceId=");
    }
  }

  @Test
  void unrelatedRequestKeepsItsExistingDiagnosticExceptionLogging() {
    var request =
        new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/products");
    try (var logs = capture()) {
      new GlobalExceptionHandler(meters)
          .handleGeneric(new IllegalStateException("ordinary database fault"), request);
      assertThat(logs.text()).contains("ordinary database fault");
    }
  }

  private SensitiveLogCapture capture() {
    return new SensitiveLogCapture(
        PublicSignupController.class,
        TenantOnboardingController.class,
        PasswordController.class,
        GlobalExceptionHandler.class);
  }

  private void assertSafe(SensitiveLogCapture logs) {
    assertThat(logs.text()).doesNotContain(TOKEN, TOKEN.substring(0, 8), URL, PASSWORD);
  }
}
