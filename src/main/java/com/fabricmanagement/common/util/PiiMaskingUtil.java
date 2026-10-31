package com.fabricmanagement.common.util;

import lombok.experimental.UtilityClass;

/**
 * Utility for masking Personally Identifiable Information (PII) in logs.
 *
 * <p>GDPR/KVKK compliance: sensitive data is masked in every log, in every environment.
 *
 * <p><b>Usage:</b>
 *
 * <pre>
 * log.info("User registered: {}", PiiMaskingUtil.maskEmail(email));
 * log.info("Phone verification: {}", PiiMaskingUtil.maskPhone(phone));
 * </pre>
 */
@UtilityClass
public class PiiMaskingUtil {

  private static final String MASK = "***";

  public static String maskEmail(String email) {
    if (email == null || email.isBlank()) {
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
    if (phone == null || phone.isBlank()) {
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
    if (cardNumber == null || cardNumber.isBlank()) {
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
    if (sensitive == null || sensitive.isBlank()) {
      return sensitive;
    }

    if (sensitive.length() <= 2) {
      return MASK;
    }

    return sensitive.charAt(0) + MASK + sensitive.charAt(sensitive.length() - 1);
  }
}
