package com.fabricmanagement.notification.hub.infra.websocket;

import com.fabricmanagement.common.infrastructure.security.JwtTokenExtractor;
import java.util.Arrays;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * STOMP/WebSocket konfigürasyonu — IN_APP gerçek zamanlı bildirimler için.
 *
 * <p>Client bağlantı: ws://host/api/ws endpoint'inden STOMP üzerinden bağlanır. Subscribe path:
 * /user/queue/notifications (kullanıcıya özel) Genel topic: /topic/tenant/{tenantId}
 *
 * <p><b>Security:</b>
 *
 * <ul>
 *   <li>CORS origin whitelist ile kısıtlanır (config'den okunur)
 *   <li>Handshake aşamasında HttpOnly cookie'den JWT çıkarılır (raw Cookie header parse)
 *   <li>STOMP CONNECT'te JWT authentication yapılır ({@link WebSocketAuthInterceptor})
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  @Value("${application.websocket.allowed-origins:http://localhost:3000,http://localhost:5173}")
  private String[] allowedOrigins;

  private final WebSocketAuthInterceptor webSocketAuthInterceptor;

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    // Basit in-memory broker — üretim için Redis/RabbitMQ ile değiştirilebilir
    registry.enableSimpleBroker("/topic", "/queue");
    registry.setApplicationDestinationPrefixes("/app");
    registry.setUserDestinationPrefix("/user");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint("/api/ws")
        .setAllowedOrigins(allowedOrigins)
        .addInterceptors(cookieAuthHandshakeInterceptor());
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(webSocketAuthInterceptor);
  }

  /**
   * Handshake sırasında HttpOnly {@code access_token} cookie'sinden JWT'yi çıkarıp WebSocket
   * session attribute'larına yazar. Böylece STOMP CONNECT aşamasında {@link
   * WebSocketAuthInterceptor} bu token'ı kullanabilir.
   *
   * <p>Endpoint {@code /api/ws} altında olduğu için, {@code Path=/api} cookie'si tarayıcı
   * tarafından otomatik olarak gönderilir. Cookie parsing raw HTTP header üzerinden yapılır —
   * servlet API'sine bağımlılık yoktur.
   */
  private HandshakeInterceptor cookieAuthHandshakeInterceptor() {
    return new HandshakeInterceptor() {
      @Override
      public boolean beforeHandshake(
          ServerHttpRequest request,
          ServerHttpResponse response,
          WebSocketHandler wsHandler,
          Map<String, Object> attributes) {
        String cookieHeader = request.getHeaders().getFirst(HttpHeaders.COOKIE);
        if (cookieHeader != null) {
          Arrays.stream(cookieHeader.split(";"))
              .map(String::trim)
              .filter(c -> c.startsWith(JwtTokenExtractor.ACCESS_TOKEN_COOKIE_NAME + "="))
              .findFirst()
              .map(c -> c.substring(JwtTokenExtractor.ACCESS_TOKEN_COOKIE_NAME.length() + 1))
              .ifPresent(token -> attributes.put("auth_token", token));
        }
        return true;
      }

      @Override
      public void afterHandshake(
          ServerHttpRequest request,
          ServerHttpResponse response,
          WebSocketHandler wsHandler,
          Exception exception) {}
    };
  }
}
