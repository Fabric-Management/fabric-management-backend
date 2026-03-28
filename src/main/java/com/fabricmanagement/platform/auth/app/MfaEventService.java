package com.fabricmanagement.platform.auth.app;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Manages SSE (Server-Sent Events) connections for real-time MFA status updates.
 *
 * <p>When a WhatsApp verification times out and falls back to SMS, the frontend receives a push
 * event so it can display an appropriate message ("WhatsApp timed out, SMS code sent") without
 * polling.
 *
 * <p>Lifecycle: client calls GET /api/auth/mfa/events/{userId} after receiving mfaRequired=true.
 * The emitter stays open until MFA completes, times out, or the connection drops.
 */
@Service
@Slf4j
public class MfaEventService {

  private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L; // 5 minutes

  private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

  /**
   * Register a new SSE emitter for a user's MFA session.
   *
   * @param userId the user awaiting MFA verification
   * @return a new SseEmitter for the client to consume
   */
  public SseEmitter subscribe(UUID userId) {
    removeExisting(userId);

    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

    emitter.onCompletion(
        () -> {
          emitters.remove(userId);
          log.debug("MFA SSE emitter completed for user: {}", userId);
        });

    emitter.onTimeout(
        () -> {
          emitters.remove(userId);
          log.debug("MFA SSE emitter timed out for user: {}", userId);
        });

    emitter.onError(
        ex -> {
          emitters.remove(userId);
          log.debug("MFA SSE emitter error for user: {}: {}", userId, ex.getMessage());
        });

    emitters.put(userId, emitter);
    log.info("MFA SSE emitter registered for user: {}", userId);

    return emitter;
  }

  /**
   * Push a fallback event to the client when WhatsApp verification times out and SMS is triggered.
   *
   * @param userId the user whose MFA channel changed
   * @param fallbackChannel the new channel (e.g. "SMS")
   */
  public void pushFallbackEvent(UUID userId, String fallbackChannel) {
    SseEmitter emitter = emitters.get(userId);
    if (emitter == null) {
      log.debug("No active SSE emitter for user {} — fallback event not pushed", userId);
      return;
    }

    try {
      MfaFallbackEvent event =
          new MfaFallbackEvent(
              "MFA_CHANNEL_FALLBACK",
              fallbackChannel,
              "Verification code sent via " + fallbackChannel + ". Previous channel timed out.");

      emitter.send(SseEmitter.event().name("mfa-fallback").data(event));

      log.info("MFA fallback SSE event pushed to user {}: channel={}", userId, fallbackChannel);
    } catch (IOException e) {
      log.warn("Failed to push MFA fallback event to user {}: {}", userId, e.getMessage());
      emitters.remove(userId);
    }
  }

  /**
   * Push MFA completion event and close the emitter.
   *
   * @param userId the user whose MFA completed
   */
  public void pushCompletionEvent(UUID userId) {
    SseEmitter emitter = emitters.get(userId);
    if (emitter == null) return;

    try {
      emitter.send(SseEmitter.event().name("mfa-complete").data(Map.of("type", "MFA_COMPLETE")));
      emitter.complete();
    } catch (IOException e) {
      log.debug("Failed to push MFA completion event to user {}: {}", userId, e.getMessage());
    } finally {
      emitters.remove(userId);
    }
  }

  private void removeExisting(UUID userId) {
    SseEmitter existing = emitters.remove(userId);
    if (existing != null) {
      try {
        existing.complete();
      } catch (Exception ignored) {
        // emitter may already be completed
      }
    }
  }

  /** Event payload pushed to the client when MFA falls back to another channel. */
  public record MfaFallbackEvent(String type, String fallbackChannel, String message) {}
}
