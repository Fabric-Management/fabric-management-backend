package com.fabricmanagement.flowboard.common.websocket;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * FlowBoard board güncellemelerini WebSocket üzerinden yayınlar.
 *
 * <p>Faz 7'de kurulan {@link SimpMessagingTemplate} (STOMP) altyapısını kullanır.
 *
 * <p>Kanal: {@code /topic/board/{boardId}}
 *
 * <p>Docs: {@code 07-flowboard/board-task.md} — WebSocket Kanalları
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BoardWebSocketPublisher {

  private static final String BOARD_TOPIC_PREFIX = "/topic/board/";

  private final SimpMessagingTemplate messagingTemplate;

  /**
   * Board kanalına event yayınlar.
   *
   * @param boardId Hedef board
   * @param eventType Event tipi
   * @param payload Event payload (taskId, status vs.)
   */
  public void publish(
      UUID boardId, BoardWebSocketEventType eventType, Map<String, String> payload) {
    String destination = BOARD_TOPIC_PREFIX + boardId;
    var message =
        new BoardWebSocketMessage(UUID.randomUUID(), eventType, boardId, payload, Instant.now());
    try {
      messagingTemplate.convertAndSend(destination, message);
      log.debug("WS published: board={} event={}", boardId, eventType);
    } catch (Exception e) {
      // WebSocket hatası task işlemini engellemez — sadece loglanır
      log.warn("WS publish failed: board={} event={} error={}", boardId, eventType, e.getMessage());
    }
  }

  /**
   * [EV2 FIX] WebSocket mesaj yapısı — eventId ile deduplication desteği.
   *
   * @param eventId Benzersiz event ID — frontend deduplication için
   * @param eventType Event tipi
   * @param boardId Board UUID
   * @param payload Event detayları
   * @param timestamp Yayın zamanı
   */
  public record BoardWebSocketMessage(
      UUID eventId,
      BoardWebSocketEventType eventType,
      UUID boardId,
      Map<String, String> payload,
      Instant timestamp) {}
}
