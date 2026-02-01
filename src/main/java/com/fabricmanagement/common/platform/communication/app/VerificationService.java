package com.fabricmanagement.common.platform.communication.app;

import com.fabricmanagement.common.platform.communication.domain.strategy.VerificationStrategy;
import com.fabricmanagement.common.platform.communication.infra.client.WhatsAppClient;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Verification Service - Multi-channel verification code delivery with smart channel selection.
 *
 * <p>Automatically selects best available channel based on recipient type and capability:
 *
 * <ol>
 *   <li><b>Phone Numbers:</b> Checks WhatsApp capability first (fail-fast), then tries SMS
 *   <li><b>Email:</b> Uses Email strategy (Priority 2)
 *   <li><b>Fallback:</b> Tries all strategies in priority order
 * </ol>
 *
 * <p><b>Smart Selection Logic:</b>
 *
 * <ul>
 *   <li>✅ Detects phone numbers (E.164 format)
 *   <li>✅ Checks WhatsApp capability before attempting (fail-fast optimization)
 *   <li>✅ Automatically falls back to SMS if WhatsApp unavailable
 *   <li>✅ Email detection for email recipients
 * </ul>
 *
 * <h2>Usage:</h2>
 *
 * <pre>{@code
 * // Phone number - checks WhatsApp first
 * verificationService.sendVerificationCode("+14155551234", "123456");
 *
 * // Email - uses Email strategy
 * verificationService.sendVerificationCode("user@example.com", "123456");
 * }</pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationService {

  private final List<VerificationStrategy> strategies;
  private final WhatsAppClient whatsAppClient;

  /**
   * Send verification code with smart channel selection (async - doesn't block user response).
   *
   * <p><b>Performance:</b> Async execution ensures fast user responses. Email/SMS sending happens
   * in background thread pool.
   *
   * <p><b>For Phone Numbers:</b>
   *
   * <ol>
   *   <li>Check WhatsApp capability first (fail-fast)
   *   <li>If WhatsApp available → Use WhatsApp (Priority 1)
   *   <li>If WhatsApp unavailable → Fallback to SMS (Priority 3)
   * </ol>
   *
   * <p><b>For Email:</b>
   *
   * <ol>
   *   <li>Use Email strategy (Priority 2)
   * </ol>
   *
   * <p><b>Fallback:</b> Try all strategies in priority order if recipient type unknown.
   *
   * @param recipient Email or phone number (E.164 format)
   * @param code 6-digit verification code
   */
  @Async
  public void sendVerificationCode(String recipient, String code) {
    log.info("Sending verification code to: {}", maskRecipient(recipient));

    // Resolve channel at verification time (no isWhatsApp on Contact - check API/cache)
    if (isPhoneNumber(recipient)) {
      boolean hasWhatsApp = whatsAppClient.phoneHasWhatsApp(recipient);

      if (hasWhatsApp) {
        log.info("Phone has WhatsApp capability, using WhatsApp strategy");
        sendViaStrategy("WhatsApp", recipient, code);
        return;
      } else {
        log.info("Phone does not have WhatsApp, using SMS strategy");
        sendViaStrategy("SMS", recipient, code);
        return;
      }
    }

    // Email flow
    if (isEmail(recipient)) {
      log.info("Email detected, using Email strategy");
      sendViaStrategy("Email", recipient, code);
      return;
    }

    // Fallback: Try all strategies in priority order
    log.warn("Unknown recipient type, trying all strategies");
    strategies.stream()
        .sorted(Comparator.comparing(VerificationStrategy::priority))
        .filter(VerificationStrategy::isAvailable)
        .findFirst()
        .ifPresentOrElse(
            strategy -> {
              log.info("Using {} strategy", strategy.name());
              strategy.sendVerificationCode(recipient, code);
              log.info("✅ Verification code sent successfully via {}", strategy.name());
            },
            () -> {
              log.error("No verification strategy available!");
              throw new RuntimeException("All verification channels unavailable");
            });
  }

  /** Send verification code via specific strategy. */
  private void sendViaStrategy(String strategyName, String recipient, String code) {
    strategies.stream()
        .filter(s -> s.name().equals(strategyName))
        .filter(VerificationStrategy::isAvailable)
        .findFirst()
        .ifPresentOrElse(
            strategy -> {
              log.info("Using {} strategy for verification", strategyName);
              strategy.sendVerificationCode(recipient, code);
              log.info("✅ Verification code sent via {}", strategyName);
            },
            () -> {
              log.warn("{} strategy not available, trying fallback", strategyName);
              sendViaFallback(recipient, code);
            });
  }

  /** Fallback: Try all available strategies in priority order. */
  private void sendViaFallback(String recipient, String code) {
    strategies.stream()
        .sorted(Comparator.comparing(VerificationStrategy::priority))
        .filter(VerificationStrategy::isAvailable)
        .findFirst()
        .ifPresentOrElse(
            strategy -> {
              log.info("Using fallback {} strategy", strategy.name());
              strategy.sendVerificationCode(recipient, code);
              log.info("✅ Verification code sent via {}", strategy.name());
            },
            () -> {
              log.error("No verification strategy available!");
              throw new RuntimeException("All verification channels unavailable");
            });
  }

  /** Check if recipient is a phone number (E.164 format). */
  private boolean isPhoneNumber(String recipient) {
    return recipient != null && recipient.matches("^\\+[1-9]\\d{1,14}$");
  }

  /** Check if recipient is an email address. */
  private boolean isEmail(String recipient) {
    return recipient != null && recipient.contains("@") && recipient.contains(".");
  }

  /** Mask recipient for logging (PII protection). */
  private String maskRecipient(String recipient) {
    if (recipient == null) {
      return null;
    }
    if (recipient.contains("@")) {
      return PiiMaskingUtil.maskEmail(recipient);
    }
    if (recipient.startsWith("+")) {
      return PiiMaskingUtil.maskPhone(recipient);
    }
    return recipient.length() > 4 ? recipient.substring(0, 2) + "***" : "***";
  }
}
