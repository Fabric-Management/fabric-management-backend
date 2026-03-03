package com.fabricmanagement.common.platform.communication.app;

import com.fabricmanagement.common.platform.auth.domain.VerificationType;
import com.fabricmanagement.common.platform.communication.domain.DeliveryChannel;
import com.fabricmanagement.common.platform.communication.domain.DeliveryStatus;
import com.fabricmanagement.common.platform.communication.domain.SmsRoutingConfig;
import com.fabricmanagement.common.platform.communication.domain.VerificationLog;
import com.fabricmanagement.common.platform.communication.domain.strategy.VerificationStrategy;
import com.fabricmanagement.common.platform.communication.infra.client.WhatsAppClient;
import com.fabricmanagement.common.platform.communication.infra.repository.VerificationLogRepository;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
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
  private final VerificationLogRepository verificationLogRepository;
  private final MarketRoutingService routingService;
  private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

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
   * @param tenantId Tenant ID for multi-tenancy
   * @param userId User ID for tracking
   * @param verificationType Type of verification (MFA_LOGIN_PHONE, MFA_LOGIN_EMAIL, etc.)
   */
  @Async
  public void sendVerificationCode(
      String recipient,
      String code,
      UUID tenantId,
      UUID userId,
      VerificationType verificationType) {
    log.info("Sending verification code to: {}", maskRecipient(recipient));

    String countryCode = extractCountryCode(recipient);

    // Use market-based routing for phone numbers
    if (isPhoneNumber(recipient)) {
      sendViaMarketRouting(recipient, code, tenantId, userId, verificationType, countryCode);
      return;
    }

    // Email flow
    if (isEmail(recipient)) {
      log.info("Email detected, using Email strategy");
      sendViaStrategy("Email", recipient, code);
      createVerificationLog(
          tenantId, userId, recipient, verificationType, DeliveryChannel.EMAIL, countryCode, null);
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
              createVerificationLog(
                  tenantId,
                  userId,
                  recipient,
                  verificationType,
                  DeliveryChannel.EMAIL,
                  countryCode,
                  null);
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

  /**
   * Send verification code using market-based routing configuration.
   *
   * <p>Uses MarketRoutingService to determine primary and fallback channels based on tenant and
   * country code.
   */
  private void sendViaMarketRouting(
      String recipient,
      String code,
      UUID tenantId,
      UUID userId,
      VerificationType verificationType,
      String countryCode) {
    if (countryCode == null) {
      log.warn("No country code available for phone number, falling back to all strategies");
      sendViaFallback(recipient, code);
      return;
    }

    SmsRoutingConfig config = routingService.getRoutingConfig(tenantId, countryCode);
    DeliveryChannel primaryChannel = config.getPrimaryChannel();

    log.info(
        "Market routing: country={}, primaryChannel={}, fallbackChannel={}",
        countryCode,
        primaryChannel,
        config.getFallbackChannel());

    try {
      if (primaryChannel == DeliveryChannel.WHATSAPP
          && whatsAppClient.phoneHasWhatsApp(recipient)) {
        sendAndLogViaStrategy(
            "WhatsApp",
            recipient,
            code,
            tenantId,
            userId,
            verificationType,
            countryCode,
            DeliveryChannel.WHATSAPP);
      } else if (primaryChannel == DeliveryChannel.WHATSAPP) {
        log.warn("Phone does not have WhatsApp capability, using fallback channel");
        sendViaFallbackChannel(
            recipient, code, tenantId, userId, verificationType, countryCode, config);
      } else if (primaryChannel == DeliveryChannel.SMS) {
        sendAndLogViaStrategy(
            "SMS",
            recipient,
            code,
            tenantId,
            userId,
            verificationType,
            countryCode,
            DeliveryChannel.SMS);
      } else if (primaryChannel == DeliveryChannel.EMAIL) {
        log.warn("Email configured as primary for phone number, using SMS instead");
        sendAndLogViaStrategy(
            "SMS",
            recipient,
            code,
            tenantId,
            userId,
            verificationType,
            countryCode,
            DeliveryChannel.SMS);
      }
    } catch (Exception e) {
      log.error("Primary channel failed: {}", e.getMessage());
      sendViaFallbackChannel(
          recipient, code, tenantId, userId, verificationType, countryCode, config);
    }
  }

  /** Send via fallback channel configured in routing config. */
  private void sendViaFallbackChannel(
      String recipient,
      String code,
      UUID tenantId,
      UUID userId,
      VerificationType verificationType,
      String countryCode,
      SmsRoutingConfig config) {
    DeliveryChannel fallbackChannel = config.getFallbackChannel();
    if (fallbackChannel == null) {
      log.error("No fallback channel configured for country: {}", countryCode);
      throw new RuntimeException("Primary channel failed and no fallback configured");
    }

    log.info("Using fallback channel: {}", fallbackChannel);
    if (fallbackChannel == DeliveryChannel.SMS) {
      sendAndLogViaStrategy(
          "SMS",
          recipient,
          code,
          tenantId,
          userId,
          verificationType,
          countryCode,
          DeliveryChannel.SMS);
    } else if (fallbackChannel == DeliveryChannel.EMAIL) {
      log.warn("Email fallback for phone number not supported");
      throw new RuntimeException("Email fallback not supported for phone numbers");
    } else if (fallbackChannel == DeliveryChannel.WHATSAPP) {
      sendAndLogViaStrategy(
          "WhatsApp",
          recipient,
          code,
          tenantId,
          userId,
          verificationType,
          countryCode,
          DeliveryChannel.WHATSAPP);
    }
  }

  /** Helper to send via strategy and log the verification attempt */
  private void sendAndLogViaStrategy(
      String strategyName,
      String recipient,
      String code,
      UUID tenantId,
      UUID userId,
      VerificationType verificationType,
      String countryCode,
      DeliveryChannel channel) {

    strategies.stream()
        .filter(s -> s.name().equalsIgnoreCase(strategyName))
        .filter(VerificationStrategy::isAvailable)
        .findFirst()
        .ifPresentOrElse(
            strategy -> {
              try {
                log.info("Using {} strategy for verification", strategyName);
                strategy.sendVerificationCode(recipient, code);
                createVerificationLog(
                    tenantId, userId, recipient, verificationType, channel, countryCode, null);
                log.info("✅ Verification code sent via {}", strategyName);
              } catch (Exception e) {
                createVerificationLog(
                    tenantId,
                    userId,
                    recipient,
                    verificationType,
                    channel,
                    countryCode,
                    null,
                    DeliveryStatus.FAILED,
                    e.getMessage());
                throw e; // rethrow to trigger failover mechanism if any
              }
            },
            () -> {
              log.warn(
                  "{} strategy not available, throwing exception to trigger fallback",
                  strategyName);
              throw new RuntimeException(strategyName + " strategy unavailable");
            });
  }

  /** Create VerificationLog entry for tracking and fallback. */
  private void createVerificationLog(
      UUID tenantId,
      UUID userId,
      String contactValue,
      VerificationType verificationType,
      DeliveryChannel deliveryChannel,
      String countryCode,
      String externalMessageId) {
    createVerificationLog(
        tenantId,
        userId,
        contactValue,
        verificationType,
        deliveryChannel,
        countryCode,
        externalMessageId,
        DeliveryStatus.PENDING,
        null);
  }

  /** Create VerificationLog entry with custom status and error. */
  private void createVerificationLog(
      UUID tenantId,
      UUID userId,
      String contactValue,
      VerificationType verificationType,
      DeliveryChannel deliveryChannel,
      String countryCode,
      String externalMessageId,
      DeliveryStatus status,
      String errorMessage) {
    try {
      VerificationLog vLog =
          VerificationLog.builder()
              .tenantId(tenantId)
              .userId(userId)
              .contactValue(contactValue)
              .countryCode(countryCode)
              .verificationType(verificationType)
              .deliveryChannel(deliveryChannel)
              .deliveryStatus(status)
              .externalMessageId(externalMessageId)
              .errorMessage(errorMessage)
              .build();

      verificationLogRepository.save(vLog);
      log.debug(
          "VerificationLog created: channel={}, status={}, countryCode={}, messageId={}",
          deliveryChannel,
          status,
          countryCode,
          externalMessageId);
    } catch (Exception e) {
      log.error("Failed to create VerificationLog: {}", e.getMessage(), e);
    }
  }

  /**
   * Extract country code from phone number (E.164 format).
   *
   * @param recipient Phone number or email
   * @return Country code (e.g., "TR", "US") or null if not a phone number
   */
  private String extractCountryCode(String recipient) {
    if (!isPhoneNumber(recipient)) {
      return null;
    }

    try {
      Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(recipient, null);
      String regionCode = phoneNumberUtil.getRegionCodeForNumber(phoneNumber);
      log.debug("Extracted country code: {} from phone: {}", regionCode, maskRecipient(recipient));
      return regionCode;
    } catch (NumberParseException e) {
      log.warn("Failed to parse phone number for country code: {}", maskRecipient(recipient));
      return null;
    }
  }
}
