package com.fabricmanagement.notification.hub.infra.websocket;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.platform.auth.app.JwtService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * WebSocket STOMP bağlantısında JWT authentication yapan interceptor.
 *
 * <p>Client, STOMP CONNECT frame'inde {@code Authorization: Bearer <token>} header'ı gönderir.
 * Token doğrulanır ve Spring Security principal ayarlanır.
 *
 * <p><b>Bu olmadan:</b> Herhangi biri başkasının userId ile subscribe olarak bildirimlerini
 * dinleyebilir (Cross-Site WebSocket Hijacking).
 *
 * <p>Kullanım (JavaScript client):
 *
 * <pre>{@code
 * const stompClient = new StompJs.Client({
 *   connectHeaders: { Authorization: 'Bearer ' + accessToken }
 * });
 * }</pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

  private final JwtService jwtService;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
      return message;
    }

    String authHeader = accessor.getFirstNativeHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      log.warn("WebSocket CONNECT rejected — missing Authorization header");
      throw new SecurityException("Missing or invalid Authorization header");
    }

    String token = authHeader.substring(7);

    if (!jwtService.validateToken(token) || jwtService.isPreAuthToken(token)) {
      log.warn("WebSocket CONNECT rejected — invalid or pre-auth JWT token");
      throw new SecurityException("Invalid JWT token");
    }

    try {
      UUID userId = jwtService.getUserIdFromToken(token);
      String roleCode = jwtService.getRoleCodeFromToken(token);
      UUID tenantId = null;
      try {
        tenantId = jwtService.getTenantIdFromToken(token);
      } catch (Exception ignored) {
        // tenant_id opsiyonel
      }

      List<String> departmentCodes = jwtService.getDepartmentCodesFromToken(token);
      String primaryDepartment = jwtService.getPrimaryDepartmentFromToken(token);

      AuthenticatedUserContext userContext =
          new AuthenticatedUserContext(
              userId, roleCode, departmentCodes, primaryDepartment, tenantId);

      var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + roleCode));
      var authentication =
          new UsernamePasswordAuthenticationToken(userId.toString(), null, authorities);
      authentication.setDetails(userContext);

      accessor.setUser(authentication);

      log.debug("WebSocket CONNECT authenticated: userId={} role={}", userId, roleCode);

    } catch (Exception ex) {
      log.warn("WebSocket CONNECT failed — JWT parsing error: {}", ex.getMessage());
      throw new SecurityException("JWT authentication failed");
    }

    return message;
  }
}
