package com.fabricmanagement.common.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantAccessPort;
import java.util.List;
import java.util.Optional;
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
  private TenantAccessPort tenantAccessPort;

  @BeforeEach
  void setUp() {
    tenantAccessPort = mock(TenantAccessPort.class);
    interceptor = new PlaygroundQuotaInterceptor(Optional.of(tenantAccessPort));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Nested
  @DisplayName("preHandle")
  class PreHandle {

    @Test
    @DisplayName("Should block POST request after 5000 mutations in playground session")
    void shouldBlockAfter5000Mutations() throws Exception {
      UUID tenantId = UUID.randomUUID();
      setPlaygroundContext(tenantId, "guest-1");

      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");

      // Perform 5000 requests
      for (int i = 0; i < 5_000; i++) {
        MockHttpServletResponse res = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(request, res, new Object());
        assertThat(allowed).isTrue();
        assertThat(res.getHeader("X-Playground-Quota-Remaining"))
            .isEqualTo(String.valueOf(4_999 - i));
      }

      // The 5001st request should be blocked
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
    @DisplayName("Should count demoMode regular sessions")
    void shouldCountDemoModeRegularSessions() throws Exception {
      UUID tenantId = UUID.randomUUID();
      setRegularContext(tenantId);
      when(tenantAccessPort.isDemoMode(tenantId)).thenReturn(true);

      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
      MockHttpServletResponse response = new MockHttpServletResponse();

      boolean allowed = interceptor.preHandle(request, response, new Object());

      assertThat(allowed).isTrue();
      assertThat(response.getHeader("X-Playground-Quota-Remaining")).isEqualTo("4999");
    }

    @Test
    @DisplayName("Should still count legacy is_playground sessions")
    void shouldStillCountLegacyPlaygroundSessions() throws Exception {
      UUID tenantId = UUID.randomUUID();
      setPlaygroundContext(tenantId, "guest-legacy");

      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
      MockHttpServletResponse response = new MockHttpServletResponse();

      boolean allowed = interceptor.preHandle(request, response, new Object());

      assertThat(allowed).isTrue();
      assertThat(response.getHeader("X-Playground-Quota-Remaining")).isEqualTo("4999");
    }

    @Test
    @DisplayName("Should ignore real-mode regular sessions")
    void shouldIgnoreRealModeRegularSessions() throws Exception {
      UUID tenantId = UUID.randomUUID();
      setRegularContext(tenantId);
      when(tenantAccessPort.isDemoMode(tenantId)).thenReturn(false);

      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
      MockHttpServletResponse response = new MockHttpServletResponse();

      boolean allowed = interceptor.preHandle(request, response, new Object());

      assertThat(allowed).isTrue();
      assertThat(response.getHeader("X-Playground-Quota-Remaining")).isNull();
    }

    @Test
    @DisplayName("Should count demoMode session with null guestId")
    void shouldCountDemoModeSessionWithNullGuestId() throws Exception {
      UUID tenantId = UUID.randomUUID();
      setRegularContext(tenantId);
      when(tenantAccessPort.isDemoMode(tenantId)).thenReturn(true);

      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
      MockHttpServletResponse response = new MockHttpServletResponse();

      boolean allowed = interceptor.preHandle(request, response, new Object());

      assertThat(allowed).isTrue();
      assertThat(response.getHeader("X-Playground-Quota-Remaining")).isEqualTo("4999");
    }

    @Test
    @DisplayName("Should no-op when tenant access port is unavailable")
    void shouldNoOpWhenTenantAccessPortUnavailable() throws Exception {
      interceptor = new PlaygroundQuotaInterceptor(Optional.empty());
      UUID tenantId = UUID.randomUUID();
      setRegularContext(tenantId);

      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
      MockHttpServletResponse response = new MockHttpServletResponse();

      boolean allowed = interceptor.preHandle(request, response, new Object());

      assertThat(allowed).isTrue();
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
