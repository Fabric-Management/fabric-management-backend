package com.fabricmanagement.notification.hub.infra.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.platform.auth.app.JwtService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/** PLATFORM-BOUNDARY-1: the live channel applies the same platform-role rule as the HTTP filter. */
class WebSocketAuthInterceptorPlatformRoleTest {

  private static final UUID CUSTOMER = UUID.fromString("66666666-6666-6666-6666-666666666666");

  private final JwtService jwtService = mock(JwtService.class);
  private final WebSocketAuthInterceptor interceptor = new WebSocketAuthInterceptor(jwtService);

  @Test
  @DisplayName("platform staff in the platform tenant keep the platform role")
  void platformTenant() {
    Authentication auth = connect(TenantContext.SYSTEM_TENANT_ID, false, false);

    assertThat(authorities(auth)).containsExactly("ROLE_PLATFORM_ADMIN");
    assertThat(((AuthenticatedUserContext) auth.getPrincipal()).roleCode())
        .isEqualTo("PLATFORM_ADMIN");
  }

  @Test
  @DisplayName("the platform role in a customer tenant connects with no role")
  void customerTenant() {
    Authentication auth = connect(CUSTOMER, false, false);

    assertThat(authorities(auth)).isEmpty();
    assertThat(((AuthenticatedUserContext) auth.getPrincipal()).roleCode()).isNull();
  }

  @Test
  @DisplayName("Playground and demo-persona tokens connect with no role")
  void playgroundAndPersona() {
    assertThat(authorities(connect(TenantContext.SYSTEM_TENANT_ID, true, false))).isEmpty();
    assertThat(authorities(connect(TenantContext.SYSTEM_TENANT_ID, false, true))).isEmpty();
  }

  private Authentication connect(UUID tenantId, boolean playground, boolean persona) {
    UUID userId = UUID.randomUUID();
    when(jwtService.validateToken("token")).thenReturn(true);
    when(jwtService.isPreAuthToken("token")).thenReturn(false);
    when(jwtService.getUserIdFromToken("token")).thenReturn(userId);
    when(jwtService.getRoleCodeFromToken("token")).thenReturn("PLATFORM_ADMIN");
    when(jwtService.getTenantIdFromToken("token")).thenReturn(tenantId);
    when(jwtService.getDepartmentCodesFromToken("token")).thenReturn(List.of());
    when(jwtService.extractAuthContext("token"))
        .thenReturn(
            new JwtService.AuthTokenClaims(
                userId,
                "PLATFORM_ADMIN",
                null,
                List.of(),
                null,
                tenantId,
                playground,
                null,
                null,
                persona));

    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
    accessor.setNativeHeader("Authorization", "Bearer token");
    accessor.setLeaveMutable(true);
    Message<byte[]> message =
        MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

    interceptor.preSend(message, null);

    Authentication auth = (Authentication) accessor.getUser();
    assertThat(auth).isNotNull();
    return auth;
  }

  private static List<String> authorities(Authentication auth) {
    return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
  }
}
