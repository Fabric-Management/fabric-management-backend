package com.fabricmanagement.notification.hub.infra.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP/WebSocket konfigürasyonu — IN_APP gerçek zamanlı bildirimler için.
 *
 * <p>Client bağlantı: ws://host/ws endpoint'inden STOMP üzerinden bağlanır. Subscribe path:
 * /user/queue/notifications (kullanıcıya özel) Genel topic: /topic/tenant/{tenantId}
 *
 * <p><b>Security:</b>
 *
 * <ul>
 *   <li>CORS origin whitelist ile kısıtlanır (config'den okunur)
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
        .addEndpoint("/ws")
        .setAllowedOrigins(allowedOrigins)
        .withSockJS(); // SockJS fallback — eski browser uyumu
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(webSocketAuthInterceptor);
  }
}
