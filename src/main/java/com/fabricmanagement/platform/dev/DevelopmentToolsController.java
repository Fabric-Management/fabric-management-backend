package com.fabricmanagement.platform.dev;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.auth.infra.repository.RegistrationTokenRepository;
import com.fabricmanagement.platform.auth.infra.repository.VerificationCodeRepository;
import com.fabricmanagement.platform.communication.app.EmailDiagnosticService;
import com.fabricmanagement.platform.communication.domain.EmailOutboxStatus;
import com.fabricmanagement.platform.communication.infra.repository.EmailOutboxRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.policy.infra.repository.PolicyRepository;
import com.fabricmanagement.platform.subscription.infra.repository.SubscriptionRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Development Tools Controller - Cleanup and testing utilities.
 *
 * <p><b>⚠️ CRITICAL:</b> Only available in LOCAL profile!
 *
 * <p><b>⚠️ NEVER enable in production!</b>
 *
 * <h2>Endpoints:</h2>
 *
 * <ul>
 *   <li>POST /api/dev/reset-all - Delete ALL data (use with caution!)
 *   <li>POST /api/dev/clean-tokens - Clean expired tokens
 *   <li>POST /api/dev/clean-codes - Clean expired verification codes
 *   <li>GET /api/dev/stats - Database statistics
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
@Slf4j
@Profile("local") // ⚠️ ONLY in local profile
@Tag(name = "Development Tools", description = "Development Tools operations")
public class DevelopmentToolsController {

  private final OrganizationRepository organizationRepository;
  private final UserRepository userRepository;
  private final AuthUserRepository authUserRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final PolicyRepository policyRepository;
  private final RegistrationTokenRepository tokenRepository;
  private final VerificationCodeRepository verificationCodeRepository;
  private final EmailOutboxRepository emailOutboxRepository;
  private final EmailDiagnosticService emailDiagnosticService;

  /**
   * ⚠️ DANGER: Reset ALL data in database.
   *
   * <p>Deletes everything - useful for fresh start in development.
   *
   * @return Success message
   */
  @PostMapping("/reset-all")
  @Transactional
  public ResponseEntity<ApiResponse<String>> resetAllData() {
    log.warn("🔥 RESET ALL DATA - Deleting everything!");

    long authUsers = authUserRepository.count();
    long registrationTokens = tokenRepository.count();
    long verificationCodes = verificationCodeRepository.count();
    long subscriptions = subscriptionRepository.count();
    long users = userRepository.count();
    long companies = organizationRepository.count();
    long policies = policyRepository.count();

    authUserRepository.deleteAll();
    tokenRepository.deleteAll();
    verificationCodeRepository.deleteAll();
    subscriptionRepository.deleteAll();
    userRepository.deleteAll();
    organizationRepository.deleteAll();
    policyRepository.deleteAll();

    String summary =
        String.format(
            "✅ Deleted: %d companies, %d users, %d auth users, %d subscriptions, %d policies, %d tokens, %d codes",
            companies,
            users,
            authUsers,
            subscriptions,
            policies,
            registrationTokens,
            verificationCodes);

    log.warn(summary);

    return ResponseEntity.ok(ApiResponse.success("Database reset successful", summary));
  }

  /**
   * Clean expired registration tokens.
   *
   * @return Cleanup summary
   */
  @PostMapping("/clean-tokens")
  @Transactional
  public ResponseEntity<ApiResponse<String>> cleanExpiredTokens() {
    log.info("Cleaning expired registration tokens...");

    tokenRepository.deleteByExpiresAtBefore(Instant.now());

    long remaining = tokenRepository.count();

    return ResponseEntity.ok(
        ApiResponse.success("Expired tokens cleaned", "Remaining tokens: " + remaining));
  }

  /**
   * Clean expired verification codes.
   *
   * @return Cleanup summary
   */
  @PostMapping("/clean-codes")
  @Transactional
  public ResponseEntity<ApiResponse<String>> cleanExpiredCodes() {
    log.info("Cleaning expired verification codes...");

    verificationCodeRepository.deleteByExpiresAtBefore(Instant.now());

    long remaining = verificationCodeRepository.count();

    return ResponseEntity.ok(
        ApiResponse.success("Expired codes cleaned", "Remaining codes: " + remaining));
  }

  /**
   * Get database statistics.
   *
   * @return Database stats
   */
  @GetMapping("/stats")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getDatabaseStats() {
    log.debug("Fetching database statistics...");

    Map<String, Object> stats =
        Map.of(
            "companies", organizationRepository.count(),
            "users", userRepository.count(),
            "authUsers", authUserRepository.count(),
            "subscriptions", subscriptionRepository.count(),
            "policies", policyRepository.count(),
            "registrationTokens", tokenRepository.count(),
            "verificationCodes", verificationCodeRepository.count(),
            "activeTokens",
                tokenRepository.existsByContactValueAndIsUsedFalseAndExpiresAtAfter(
                        "dummy@test.com", Instant.now())
                    ? "N/A"
                    : "0",
            "timestamp", Instant.now().toString());

    return ResponseEntity.ok(ApiResponse.success(stats, "Database statistics"));
  }

  /**
   * Check email outbox status.
   *
   * @return Email outbox statistics
   */
  @GetMapping("/email-outbox")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getEmailOutboxStatus() {
    log.debug("Fetching email outbox status...");

    long pending = emailOutboxRepository.countByStatusAndIsActiveTrue(EmailOutboxStatus.PENDING);
    long sending = emailOutboxRepository.countByStatusAndIsActiveTrue(EmailOutboxStatus.SENDING);
    long sent = emailOutboxRepository.countByStatusAndIsActiveTrue(EmailOutboxStatus.SENT);
    long failed = emailOutboxRepository.countByStatusAndIsActiveTrue(EmailOutboxStatus.FAILED);

    // Get failed email details
    var failedEmails =
        emailOutboxRepository.findByStatusAndIsActiveTrueOrderByCreatedAtDesc(
            EmailOutboxStatus.FAILED);
    var failedDetails =
        failedEmails.stream()
            .limit(5)
            .map(
                e ->
                    Map.of(
                        "id", e.getId().toString(),
                        "recipient", e.getRecipient(),
                        "retryCount", e.getRetryCount(),
                        "maxRetries", e.getMaxRetries(),
                        "lastError",
                            e.getLastError() != null
                                ? e.getLastError()
                                    .substring(0, Math.min(200, e.getLastError().length()))
                                : "N/A",
                        "createdAt", e.getCreatedAt().toString()))
            .toList();

    Map<String, Object> stats =
        Map.of(
            "pending", pending,
            "sending", sending,
            "sent", sent,
            "failed", failed,
            "total", pending + sending + sent + failed,
            "failedDetails", failedDetails,
            "timestamp", Instant.now().toString());

    return ResponseEntity.ok(ApiResponse.success(stats, "Email outbox status"));
  }

  /**
   * Retry failed emails (reset to PENDING).
   *
   * @return Retry summary
   */
  @PostMapping("/email-outbox/retry-failed")
  @Transactional
  public ResponseEntity<ApiResponse<String>> retryFailedEmails() {
    log.info("Retrying failed emails...");

    var failedEmails =
        emailOutboxRepository.findByStatusAndIsActiveTrueOrderByCreatedAtDesc(
            EmailOutboxStatus.FAILED);
    int count = 0;

    for (var email : failedEmails) {
      email.setStatus(EmailOutboxStatus.PENDING);
      email.setRetryCount(0);
      email.setNextRetryAt(null);
      email.setLastError(null);
      emailOutboxRepository.save(email);
      count++;
    }

    return ResponseEntity.ok(
        ApiResponse.success(
            "Failed emails reset to PENDING",
            String.format("Reset %d failed email(s) to PENDING status", count)));
  }

  /**
   * Comprehensive email system diagnostics.
   *
   * <p>Provides systematic analysis of email delivery system health.
   *
   * @return Comprehensive email diagnostic report
   */
  @GetMapping("/email-diagnostics")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getEmailDiagnostics() {
    log.info("Running comprehensive email system diagnostics...");

    Map<String, Object> diagnostics = emailDiagnosticService.diagnoseEmailSystem();

    return ResponseEntity.ok(ApiResponse.success(diagnostics, "Email system diagnostics"));
  }

  /** Check if running in local profile. */
  @GetMapping("/health")
  public ResponseEntity<ApiResponse<String>> devHealth() {
    return ResponseEntity.ok(
        ApiResponse.success(
            "Development tools active",
            "⚠️ LOCAL PROFILE ONLY - These endpoints will NOT work in production"));
  }
}
