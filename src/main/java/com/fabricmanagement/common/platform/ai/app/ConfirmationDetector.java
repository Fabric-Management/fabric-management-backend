package com.fabricmanagement.common.platform.ai.app;

import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Confirmation Detector - Detects user confirmation in messages.
 *
 * <p>Recognizes confirmation patterns in English (and legacy Turkish for backward compatibility).
 */
@Component
@Slf4j
public class ConfirmationDetector {

  private static final Set<String> CONFIRMATION_PATTERNS =
      Set.of(
          "evet",
          "onaylıyorum",
          "onayla",
          "tamam",
          "yap",
          "olur",
          "iyi",
          "ok",
          "evet onaylıyorum",
          "onaylıyorum evet",
          "tamam onayla",
          "yes",
          "confirm",
          "proceed",
          "do it",
          "go ahead",
          "sure",
          "yes confirm",
          "confirm yes",
          "yes proceed");

  /**
   * Check if message contains confirmation.
   *
   * @param message user message
   * @return true if message indicates confirmation
   */
  public boolean isConfirmation(String message) {
    if (message == null || message.isBlank()) {
      return false;
    }

    String normalized = message.toLowerCase().trim();

    // Check exact match or contains confirmation pattern
    return CONFIRMATION_PATTERNS.contains(normalized)
        || CONFIRMATION_PATTERNS.stream().anyMatch(normalized::contains);
  }

  /**
   * Check if message is asking for confirmation.
   *
   * @param message AI message
   * @return true if message is asking for confirmation
   */
  public boolean isAskingConfirmation(String message) {
    if (message == null || message.isBlank()) {
      return false;
    }

    String normalized = message.toLowerCase();

    return normalized.contains("do you confirm")
        || normalized.contains("confirm")
        || normalized.contains("onaylıyor musunuz")
        || normalized.contains("onaylar mısınız")
        || normalized.contains("proceed")
        || normalized.contains("do you want")
        || normalized.contains("would you like");
  }
}
