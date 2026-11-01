package com.fabricmanagement.platform.auth.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.AuthCookieSupport;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.platform.auth.app.JwtService;
import com.fabricmanagement.platform.auth.app.PlaygroundService;
import com.fabricmanagement.platform.auth.dto.PlaygroundImpersonateResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Persona tokens per browser tab: a signed-in owner receives the persona's token in the body and
 * keeps its own cookies; a persona token cannot ask for more.
 */
@WebMvcTest(controllers = PlaygroundAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class PlaygroundAuthControllerTest {

  private static final String PATH = "/api/v1/playground/impersonate/";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PlaygroundService playgroundService;
  @MockitoBean private AuthCookieSupport authCookieSupport;
  @MockitoBean private JwtService jwtService;

  // The web slice still registers the global interceptors; their dependencies live outside it.
  @MockitoBean
  private com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort tenantQueryPort;

  @MockitoBean
  private com.fabricmanagement.platform.tenant.infra.repository.TenantRepository tenantRepository;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID personaId = UUID.randomUUID();

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void signedInOwnerGetsThePersonaTokenInTheBodyAndKeepsItsCookies() throws Exception {
    signIn(false);
    when(playgroundService.impersonate(tenantId, personaId, null, true))
        .thenReturn(new PlaygroundImpersonateResponse("persona-token", personaId, "Emma", "W"));

    mockMvc
        .perform(post(PATH + personaId).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value("persona-token"))
        .andExpect(jsonPath("$.userId").value(personaId.toString()));

    // Other tabs of the same browser share the cookies: they must stay the owner's.
    verifyNoInteractions(authCookieSupport);
  }

  @Test
  void anonymousPlaygroundStillSwitchesThroughTheAccessCookie() throws Exception {
    signIn(true);
    when(playgroundService.impersonate(tenantId, personaId, "guest-1", false))
        .thenReturn(new PlaygroundImpersonateResponse("guest-token", personaId, "Emma", "W"));

    mockMvc.perform(post(PATH + personaId).with(csrf())).andExpect(status().isOk());

    verify(authCookieSupport).addAuthCookies(any(), eq("guest-token"), isNull());
  }

  @Test
  void aPersonaTokenCannotMintPersonaTokens() throws Exception {
    signIn(false);
    when(jwtService.isDemoImpersonationToken("persona-token")).thenReturn(true);

    mockMvc
        .perform(
            post(PATH + UUID.randomUUID())
                .with(csrf())
                .header("Authorization", "Bearer persona-token"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("IMPERSONATION_FROM_PERSONA"));

    verify(playgroundService, never()).impersonate(any(), any(), any(), anyBoolean());
    verifyNoInteractions(authCookieSupport);
  }

  private void signIn(boolean anonymousPlayground) {
    AuthenticatedUserContext owner =
        new AuthenticatedUserContext(
            UUID.randomUUID(),
            "ADMIN",
            List.of(),
            null,
            tenantId,
            anonymousPlayground,
            anonymousPlayground ? "guest-1" : null);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of()));
  }
}
