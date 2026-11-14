package com.fabricmanagement.platform.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.AuthCookieSupport;
import com.fabricmanagement.common.infrastructure.web.exception.GlobalExceptionHandler;
import com.fabricmanagement.platform.auth.app.PasswordResetService;
import com.fabricmanagement.platform.auth.app.PasswordSetupService;
import com.fabricmanagement.platform.auth.dto.LoginResponse;
import com.fabricmanagement.platform.auth.dto.PasswordSetupRequest;
import com.fabricmanagement.platform.auth.dto.SetupLinkStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The setup page's two calls: look at the link, then use it. Authorisation is the security config.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordController setup links")
class PasswordControllerTest {

  private static final String TOKEN = "Zk3pQ9-raw-signup-link-token-7Fh2";

  @Mock private PasswordSetupService setup;
  @Mock private PasswordResetService reset;
  @Mock private AuthCookieSupport cookies;
  @Mock private ObjectProvider<MeterRegistry> meters;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new PasswordController(setup, reset, cookies))
            .setControllerAdvice(new GlobalExceptionHandler(meters))
            .build();
  }

  // ---- POST /setup-link/check -------------------------------------------------------------

  @Test
  @DisplayName("a usable link is reported with whether it needs a password")
  void checkReportsAUsableLink() throws Exception {
    when(setup.checkLink(TOKEN)).thenReturn(new SetupLinkStatus(true, true));

    mvc.perform(
            post("/api/v1/auth/setup-link/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + TOKEN + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.valid").value(true))
        .andExpect(jsonPath("$.data.passwordRequired").value(true));
  }

  @Test
  @DisplayName("a link whose address already has a login asks for no password")
  void checkReportsALinkThatNeedsNoPassword() throws Exception {
    when(setup.checkLink(TOKEN)).thenReturn(new SetupLinkStatus(true, false));

    mvc.perform(
            post("/api/v1/auth/setup-link/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + TOKEN + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.valid").value(true))
        .andExpect(jsonPath("$.data.passwordRequired").value(false));
  }

  @Test
  @DisplayName("an unusable link is a normal answer, not an error")
  void checkReportsAnUnusableLinkAsAnAnswer() throws Exception {
    when(setup.checkLink("old")).thenReturn(SetupLinkStatus.invalid());

    mvc.perform(
            post("/api/v1/auth/setup-link/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"old\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.valid").value(false))
        .andExpect(jsonPath("$.data.passwordRequired").value(false));
  }

  @Test
  @DisplayName("a call without a token is refused before the service is asked")
  void checkRefusesABlankToken() throws Exception {
    mvc.perform(
            post("/api/v1/auth/setup-link/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"  \"}"))
        .andExpect(status().isUnprocessableEntity());

    verifyNoInteractions(setup);
  }

  // ---- POST /setup-password ---------------------------------------------------------------

  @Test
  @DisplayName("a signed-in result goes into cookies, never into the body")
  void setupPutsTheSessionIntoCookies() throws Exception {
    when(setup.setupPassword(any(), any()))
        .thenReturn(
            LoginResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .needsOnboarding(true)
                .build());

    String body =
        mvc.perform(
                post("/api/v1/auth/setup-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"" + TOKEN + "\",\"password\":\"Hawthorn!Secret27\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.needsOnboarding").value(true))
            .andExpect(
                jsonPath("$.message").value("Welcome! Complete your profile to get started."))
            .andReturn()
            .getResponse()
            .getContentAsString();

    verify(cookies).addAuthCookies(any(), eq("access-token"), eq("refresh-token"));
    assertThat(body).doesNotContain("access-token", "refresh-token");
  }

  @Test
  @DisplayName("an existing login is told to sign in: no cookies, no tokens")
  void setupForAnExistingLoginSetsNoCookies() throws Exception {
    when(setup.setupPassword(any(), any()))
        .thenReturn(LoginResponse.builder().needsOnboarding(false).signInRequired(true).build());

    mvc.perform(
            post("/api/v1/auth/setup-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + TOKEN + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.signInRequired").value(true))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Sign in")));

    verify(cookies, never()).addAuthCookies(any(), any(), any());
  }

  @Test
  @DisplayName("the password is optional in the request: the service decides whether it is needed")
  void setupAcceptsARequestWithoutAPassword() throws Exception {
    when(setup.setupPassword(any(), any()))
        .thenReturn(LoginResponse.builder().needsOnboarding(false).signInRequired(true).build());

    mvc.perform(
            post("/api/v1/auth/setup-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + TOKEN + "\"}"))
        .andExpect(status().isOk());

    var request = ArgumentCaptor.forClass(PasswordSetupRequest.class);
    verify(setup).setupPassword(request.capture(), any());
    assertThat(request.getValue().getToken()).isEqualTo(TOKEN);
    assertThat(request.getValue().getPassword()).isNull();
  }

  @Test
  @DisplayName("a password that is too short is refused before the service is asked")
  void setupRefusesAShortPassword() throws Exception {
    mvc.perform(
            post("/api/v1/auth/setup-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + TOKEN + "\",\"password\":\"short\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors.password").exists());

    verifyNoInteractions(setup);
  }
}
