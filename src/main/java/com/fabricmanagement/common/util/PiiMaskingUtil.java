package com.fabricmanagement.common.util;

import java.util.Arrays;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

/**
 * Utility for masking Personally Identifiable Information (PII) in logs.
 *
 * <p>GDPR/KVKK compliance - sensitive data should be masked in production logs.
 *
 * <p><b>Usage:</b>
 *
 * <pre>
 * log.info("User registered: {}", PiiMaskingUtil.maskEmail(email));
 * log.info("Phone verification: {}", PiiMaskingUtil.maskPhone(phone));
 * </pre>
 */
@UtilityClass
@Slf4j
public class PiiMaskingUtil {

  private static final String MASK = "***";
  private static volatile Boolean maskingEnabled;

  /**
   * Initialize masking state from Spring Environment. Called automatically via {@link
   * PiiMaskingInitializer}.
   */
  static void init(ApplicationContext ctx) {
    Environment env = ctx.getEnvironment();
    String[] activeProfiles = env.getActiveProfiles();
    boolean isLocal = Arrays.stream(activeProfiles).anyMatch(p -> "local".equalsIgnoreCase(p));
    maskingEnabled = !isLocal;
    log.info(
        "PII masking initialized: enabled={}, activeProfiles={}",
        maskingEnabled,
        Arrays.toString(activeProfiles));
  }

  private static boolean isMaskingActive() {
    if (maskingEnabled != null) {
      return maskingEnabled;
    }
    return !isLocalProfileFallback();
  }

  public static String maskEmail(String email) {
    if (!isMaskingActive() || email == null || email.isBlank()) {
      return email;
    }

    if (!email.contains("@")) {
      return MASK;
    }

    String[] parts = email.split("@");
    if (parts.length != 2) {
      return MASK;
    }

    String localPart = parts[0];
    String domain = parts[1];

    String maskedLocal = localPart.length() <= 2 ? localPart : localPart.substring(0, 2) + MASK;

    return maskedLocal + "@" + domain;
  }

  public static String maskPhone(String phone) {
    if (!isMaskingActive() || phone == null || phone.isBlank()) {
      return phone;
    }

    if (phone.length() <= 7) {
      return MASK;
    }

    String prefix = phone.substring(0, 3);
    String suffix = phone.substring(phone.length() - 4);

    return prefix + MASK + suffix;
  }

  public static String maskCardNumber(String cardNumber) {
    if (!isMaskingActive() || cardNumber == null || cardNumber.isBlank()) {
      return cardNumber;
    }

    if (cardNumber.length() <= 8) {
      return MASK;
    }

    String prefix = cardNumber.substring(0, 4);
    String suffix = cardNumber.substring(cardNumber.length() - 4);

    return prefix + MASK + suffix;
  }

  public static String mask(String sensitive) {
    if (!isMaskingActive() || sensitive == null || sensitive.isBlank()) {
      return sensitive;
    }

    if (sensitive.length() <= 2) {
      return MASK;
    }

    return sensitive.charAt(0) + MASK + sensitive.charAt(sensitive.length() - 1);
  }

  public static boolean isMaskingEnabled() {
    return isMaskingActive();
  }

  private static boolean isLocalProfileFallback() {
    String activeProfile = System.getProperty("spring.profiles.active");
    if (activeProfile == null) {
      activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
    }
    return activeProfile != null && activeProfile.contains("local");
  }
}
