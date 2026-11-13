package com.fabricmanagement.common.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.app.JwtService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * PLATFORM-BOUNDARY-1: the filter grants the platform role only to a real session in the platform
 * tenant. Every other token that carries the code acts with no role, so every {@code
 * hasRole('PLATFORM_ADMIN')} endpoint refuses it.
 */
class JwtAuthenticationFilterPlatformRoleTest {

  private static final UUID CUSTOMER = UUID.fromString("44444444-4444-4444-4444-444444444444");

  private final JwtService jwtService = mock(JwtService.class);
  private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("platform staff in the platform tenant get the platform role")
  void platformTenantRealSession() throws Exception {
    Authentication auth = authenticate(claims("PLATFORM_ADMIN", TenantContext.SYSTEM_TENANT_ID));

    assertThat(authorities(auth)).contains("ROLE_PLATFORM_ADMIN", "PLATFORM_ADMIN");
    assertThat(context(auth).roleCode()).isEqualTo("PLATFORM_ADMIN");
  }

  @Test
  @DisplayName("the platform role in a customer tenant grants no role at all")
  void customerTenant() throws Exception {
    Authentication auth = authenticate(claims("PLATFORM_ADMIN", CUSTOMER));

    assertThat(authorities(auth)).isEmpty();
    assertThat(context(auth).roleCode()).isNull();
  }

  @Test
  @DisplayName("a Playground token never carries the platform role")
  void playgroundToken() throws Exception {
    Authentication auth =
        authenticate(
            new JwtService.AuthTokenClaims(
                UUID.randomUUID(),
                "PLATFORM_ADMIN",
                null,
                List.of(),
                null,
                TenantContext.SYSTEM_TENANT_ID,
                true,
                "guest",
                null,
                false));

    assertThat(authorities(auth)).isEmpty();
  }

  @Test
  @DisplayName("a demo-persona token never carries the platform role")
  void personaToken() throws Exception {
    Authentication auth =
        authenticate(
            new JwtService.AuthTokenClaims(
                UUID.randomUUID(),
                "PLATFORM_ADMIN",
                null,
                List.of(),
                null,
                TenantContext.SYSTEM_TENANT_ID,
                false,
                null,
                null,
                true));

    assertThat(authorities(auth)).isEmpty();
  }

  @Test
  @DisplayName("other roles are untouched")
  void otherRoles() throws Exception {
    Authentication auth = authenticate(claims("ADMIN", CUSTOMER));

    assertThat(authorities(auth)).contains("ROLE_ADMIN", "ADMIN");
    assertThat(context(auth).roleCode()).isEqualTo("ADMIN");
  }

  private Authentication authenticate(JwtService.AuthTokenClaims claims) throws Exception {
    when(jwtService.validateToken("token")).thenReturn(true);
    when(jwtService.isPreAuthToken("token")).thenReturn(false);
    when(jwtService.extractAuthContext("token")).thenReturn(claims);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/tenants");
    request.addHeader("Authorization", "Bearer token");

    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    return auth;
  }

  private static JwtService.AuthTokenClaims claims(String role, UUID tenantId) {
    return new JwtService.AuthTokenClaims(
        UUID.randomUUID(), role, null, List.of(), null, tenantId, false, null, null, false);
  }

  private static List<String> authorities(Authentication auth) {
    return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
  }

  private static AuthenticatedUserContext context(Authentication auth) {
    return (AuthenticatedUserContext) auth.getPrincipal();
  }
}
