package com.fabricmanagement.common.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("PlaygroundQuotaInterceptor (Unit Test)")
class PlaygroundQuotaInterceptorTest {

  private PlaygroundQuotaInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new PlaygroundQuotaInterceptor();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Nested
  @DisplayName("preHandle")
  class PreHandle {

    @Test
    @DisplayName("Should block POST request after 500 mutations in playground session")
    void shouldBlockAfter500Mutations() throws Exception {
      UUID tenantId = UUID.randomUUID();
      setPlaygroundContext(tenantId, "guest-1");

      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");

      // Perform 500 requests
      for (int i = 0; i < 500; i++) {
        MockHttpServletResponse res = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(request, res, new Object());
        assertThat(allowed).isTrue();
        assertThat(res.getHeader("X-Playground-Quota-Remaining"))
            .isEqualTo(String.valueOf(499 - i));
      }

      // The 501st request should be blocked
      MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
      boolean allowed = interceptor.preHandle(request, blockedResponse, new Object());

      assertThat(allowed).isFalse();
      assertThat(blockedResponse.getStatus()).isEqualTo(429);
      assertThat(blockedResponse.getHeader("X-Playground-Quota-Remaining")).isEqualTo("0");
    }

    @Test
    @DisplayName("Should ignore GET requests (no quota consumed)")
    void shouldIgnoreGetRequests() throws Exception {
      UUID tenantId = UUID.randomUUID();
      setPlaygroundContext(tenantId, "guest-2");

      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
      MockHttpServletResponse response = new MockHttpServletResponse();

      boolean allowed = interceptor.preHandle(request, response, new Object());

      assertThat(allowed).isTrue();
      // Header should not be set for GET requests
      assertThat(response.getHeader("X-Playground-Quota-Remaining")).isNull();
    }

    @Test
    @DisplayName("Should ignore regular (non-playground) sessions")
    void shouldIgnoreRegularSessions() throws Exception {
      UUID tenantId = UUID.randomUUID();
      setRegularContext(tenantId);

      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
      MockHttpServletResponse response = new MockHttpServletResponse();

      boolean allowed = interceptor.preHandle(request, response, new Object());

      assertThat(allowed).isTrue();
      // Header should not be set for regular sessions
      assertThat(response.getHeader("X-Playground-Quota-Remaining")).isNull();
    }
  }

  private void setPlaygroundContext(UUID tenantId, String guestId) {
    AuthenticatedUserContext ctx =
        new AuthenticatedUserContext(
            UUID.randomUUID(), "ROLE", List.of(), null, tenantId, true, guestId);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(ctx, null, List.of()));
  }

  private void setRegularContext(UUID tenantId) {
    AuthenticatedUserContext ctx =
        new AuthenticatedUserContext(
            UUID.randomUUID(), "ROLE", List.of(), null, tenantId, false, null);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(ctx, null, List.of()));
  }
}
