package com.fabricmanagement.common.infrastructure.web;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TrialLifecyclePort;
import com.fabricmanagement.platform.auth.app.JwtService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class JwtContextInterceptorTest {

  private final JwtService jwtService = org.mockito.Mockito.mock(JwtService.class);
  private final TenantQueryPort tenantQueryPort = org.mockito.Mockito.mock(TenantQueryPort.class);
  private final TrialLifecyclePort trialLifecyclePort =
      org.mockito.Mockito.mock(TrialLifecyclePort.class);
  private final JwtContextInterceptor interceptor =
      new JwtContextInterceptor(jwtService, tenantQueryPort, Optional.of(trialLifecyclePort));

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("touches trial activity for regular authenticated tenant requests")
  void shouldTouchTrialActivityForRegularTenantToken() {
    UUID tenantId = UUID.randomUUID();
    arrangeToken(authClaims(tenantId, false, null, null));

    interceptor.preHandle(request(), new MockHttpServletResponse(), new Object());

    verify(trialLifecyclePort).touchTenantActivity(tenantId);
  }

  @Test
  @DisplayName("skips trial activity touch for playground tokens")
  void shouldSkipPlaygroundTokens() {
    UUID tenantId = UUID.randomUUID();
    arrangeToken(authClaims(tenantId, true, null, null));

    interceptor.preHandle(request(), new MockHttpServletResponse(), new Object());

    verify(trialLifecyclePort, never()).touchTenantActivity(tenantId);
  }

  @Test
  @DisplayName("skips trial activity touch for partner tokens")
  void shouldSkipPartnerTokens() {
    UUID tenantId = UUID.randomUUID();
    arrangeToken(authClaims(tenantId, false, "PARTNER", UUID.randomUUID()));

    interceptor.preHandle(request(), new MockHttpServletResponse(), new Object());

    verify(trialLifecyclePort, never()).touchTenantActivity(tenantId);
  }

  private void arrangeToken(JwtService.AuthTokenClaims claims) {
    when(jwtService.validateToken("token")).thenReturn(true);
    when(jwtService.extractAuthContext("token")).thenReturn(claims);
    when(jwtService.getTenantUidFromToken("token")).thenReturn("ACME-001");
  }

  private static MockHttpServletRequest request() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
    request.addHeader("Authorization", "Bearer token");
    return request;
  }

  private static JwtService.AuthTokenClaims authClaims(
      UUID tenantId, boolean playground, String userType, UUID partnerId) {
    return new JwtService.AuthTokenClaims(
        UUID.randomUUID(),
        "ADMIN",
        userType,
        List.of(),
        null,
        tenantId,
        playground,
        playground ? "guest-123" : null,
        partnerId);
  }
}
