package com.fabricmanagement.platform.communication.domain.strategy;

import com.fabricmanagement.common.util.PiiMaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SMS verification strategy - Priority 3 (Fallback).
 *
 * <p>Sends verification codes via SMS when primary channels (like WhatsApp) are unavailable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmsStrategy implements VerificationStrategy {

  @Override
  public void sendVerificationCode(String recipient, String code) {
    log.info("Sending SMS verification code to: {}", PiiMaskingUtil.maskPhone(recipient));

    try {
      // Simulate SMS API call since actual SmsClient doesn't exist yet
      // e.g. twilioClient.send(recipient, code);
      log.info("✅ SMS verification code simulated successfully");
    } catch (Exception e) {
      log.error("❌ Failed to send SMS verification code: {}", e.getMessage(), e);
      throw new RuntimeException("SMS sending failed: " + e.getMessage(), e);
    }
  }

  @Override
  public boolean isAvailable() {
    // Modify based on actual SMS provider health check in the future
    return true;
  }

  @Override
  public int priority() {
    return 3; // Priority 3 (Fallback)
  }

  @Override
  public String name() {
    return "SMS";
  }
}
