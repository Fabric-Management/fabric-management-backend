package com.fabricmanagement.platform.communication.app;

import com.fabricmanagement.platform.auth.app.MfaEventService;
import com.fabricmanagement.platform.auth.app.VerificationDispatcher;
import com.fabricmanagement.platform.communication.domain.DeliveryChannel;
import com.fabricmanagement.platform.communication.domain.DeliveryStatus;
import com.fabricmanagement.platform.communication.domain.SmsRoutingConfig;
import com.fabricmanagement.platform.communication.domain.VerificationLog;
import com.fabricmanagement.platform.communication.infra.repository.VerificationLogRepository;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sweeper Job to identify WhatsApp messages that haven't received a "Delivered" webhook within the
 * market's specific timeout threshold. Triggers the SMS fallback.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDeliveryJob {

  private final VerificationLogRepository logRepository;
  private final MarketRoutingService routingService;
  private final JobScheduler jobScheduler;
  private final VerificationDispatcher verificationDispatcher;
  private final MfaEventService mfaEventService;

  @PostConstruct
  public void schedule() {
    jobScheduler.scheduleRecurrently(
        "whatsapp-timeout-sweeper", "*/15 * * * * *", this::sweepAndFallback);
  }

  /** Runs every 15 seconds to sweep the log table for timed-out WhatsApp messages. */
  @Transactional
  @Job(name = "WhatsApp Timeout and SMS Fallback Job")
  public void sweepAndFallback() {
    log.debug("Sweeping for timed out WhatsApp verifications...");

    // Using a safe 30 second static threshold for this query.
    // We will do fine-grained checking per-tenant further down.
    Instant threshold = Instant.now().minus(15, ChronoUnit.SECONDS);
    List<VerificationLog> pendingLogs = logRepository.findPendingWhatsAppMessages(threshold);

    for (VerificationLog vLog : pendingLogs) {
      String countryCode = vLog.getCountryCode();
      if (countryCode == null || countryCode.isBlank()) {
        log.warn("VerificationLog {} has no country code, skipping timeout check", vLog.getId());
        continue;
      }

      SmsRoutingConfig config = routingService.getRoutingConfig(vLog.getTenantId(), countryCode);

      Instant marketThreshold = Instant.now().minus(config.getTimeoutSeconds(), ChronoUnit.SECONDS);

      if (vLog.getCreatedAt().isBefore(marketThreshold)) {
        log.warn(
            "WhatsApp message {} timed out. Marking as TIMEOUT and triggering fallback to {}",
            vLog.getId(),
            config.getFallbackChannel());

        vLog.setDeliveryStatus(DeliveryStatus.TIMEOUT);
        logRepository.save(vLog);

        if (config.getFallbackChannel() != null) {
          triggerFallback(vLog, config.getFallbackChannel());
        } else {
          log.error(
              "No fallback channel defined for tenant {} during WA timeout.", vLog.getTenantId());
        }
      }
    }
  }

  /**
   * Trigger fallback channel when primary channel times out.
   *
   * <p>Generates a new verification code and sends it via the fallback channel.
   *
   * @param originalLog Original verification log that timed out
   * @param fallbackChannel Fallback channel to use (SMS, EMAIL, etc.)
   */
  private void triggerFallback(VerificationLog originalLog, DeliveryChannel fallbackChannel) {
    log.info(
        "Executing Fallback to {} for user {}, originalChannel={}",
        fallbackChannel,
        originalLog.getUserId(),
        originalLog.getDeliveryChannel());

    try {
      // Generate new verification code for fallback
      // Note: We generate a new code because the original is hashed and cannot be
      // retrieved
      verificationDispatcher.sendVerificationCode(
          originalLog.getContactValue(),
          originalLog.getVerificationType(),
          originalLog.getTenantId(),
          originalLog.getUserId());

      log.info(
          "✅ Fallback verification code sent via {} to user {}",
          fallbackChannel,
          originalLog.getUserId());

      if (originalLog.getUserId() != null) {
        mfaEventService.pushFallbackEvent(originalLog.getUserId(), fallbackChannel.name());
      }

    } catch (Exception e) {
      log.error(
          "❌ Failed to send fallback verification code: userId={}, channel={}, error={}",
          originalLog.getUserId(),
          fallbackChannel,
          e.getMessage(),
          e);

      // Create a failed VerificationLog entry for the fallback attempt
      VerificationLog fallbackLog =
          VerificationLog.builder()
              .tenantId(originalLog.getTenantId())
              .userId(originalLog.getUserId())
              .contactValue(originalLog.getContactValue())
              .countryCode(originalLog.getCountryCode())
              .verificationType(originalLog.getVerificationType())
              .deliveryChannel(fallbackChannel)
              .deliveryStatus(DeliveryStatus.FAILED)
              .errorMessage("Fallback failed: " + e.getMessage())
              .build();

      logRepository.save(fallbackLog);
    }
  }
}
