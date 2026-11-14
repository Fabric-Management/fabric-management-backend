package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.infra.repository.PendingSignupRepository;
import com.fabricmanagement.platform.auth.infra.repository.SignupLinkPublicationRepository;
import com.fabricmanagement.platform.communication.app.EmailOutboxService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retention for self-service signups (SIGNUP-DEFER-1).
 *
 * <ul>
 *   <li>Daily: removes pending signups whose link expired unused some days ago, and completed ones
 *       after a month. The submitted form of someone who never confirmed their address is not kept;
 *       a completed signup lives on in its company and its lead.
 *   <li>Every few minutes: removes the raw signup link from the places it is stored besides the
 *       recipient's mailbox — the Modulith publication of the event that carried it, and the outbox
 *       copy of the mail — once it cannot be needed there. {@code pending_signup} itself only ever
 *       holds the link's hash.
 * </ul>
 *
 * <p>{@code pending_signup} and {@code event_publication} have no row-level security, so the
 * tenant-less scheduler thread deletes from them as {@code fabric_app}. The outbox is tenant-scoped
 * (signup mails are queued under the platform tenant); its redaction runs through {@link
 * EmailOutboxService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PendingSignupRetentionJob {

  /**
   * A signup link in a mail body: exactly 43 Base64url characters after {@code /setup?token=}, so a
   * UUID token (36 characters) or a longer token is never touched.
   */
  static final String SIGNUP_LINK_PATTERN = "(/setup\\?token=)[A-Za-z0-9_-]{43}(?![A-Za-z0-9_-])";

  static final String SIGNUP_LINK_REPLACEMENT = "\\1removed";

  /** A link is dead this long after it was issued: its lifetime plus a margin for clock skew. */
  static final Duration LINK_UNUSABLE_AFTER = SignupRequestService.LINK_LIFETIME.plusHours(1);

  /** How far back the frequent link removal looks in the outbox; older mails were done before. */
  static final Duration OUTBOX_LOOKBACK = Duration.ofDays(7);

  private final PendingSignupRepository pendingSignups;
  private final SignupLinkPublicationRepository linkPublications;
  private final EmailOutboxService emailOutbox;
  private final Clock clock;

  @Value("${retention.signup.enabled:true}")
  private boolean enabled;

  @Value("${retention.signup.expired-days:7}")
  private int expiredDays;

  @Value("${retention.signup.completed-days:30}")
  private int completedDays;

  /**
   * Minutes a sent mail keeps its link in the outbox copy. Long enough for an operator command (the
   * e2e setup-token reader) to read a freshly queued link, short enough to matter.
   */
  @Value("${retention.signup.link-minutes-after-sending:10}")
  private int linkMinutesAfterSending;

  // The scheduled entry points carry the transaction themselves: they call the methods below on
  // this instance, which does not pass through the transactional proxy.
  @Scheduled(cron = "${retention.signup.cron:0 40 3 * * ?}")
  @Transactional
  public void scheduledPurge() {
    if (!enabled) {
      log.debug("PendingSignupRetentionJob disabled; skipping the purge.");
      return;
    }
    purgePendingSignups(Instant.now(clock));
  }

  @Scheduled(
      fixedDelayString = "${retention.signup.link-interval-ms:300000}",
      initialDelayString = "${retention.signup.link-initial-delay-ms:60000}")
  @Transactional
  public void scheduledLinkRemoval() {
    if (!enabled) {
      log.debug("PendingSignupRetentionJob disabled; skipping the link removal.");
      return;
    }
    removeSpentLinks(Instant.now(clock));
  }

  /** Removes expired-unused and old completed signups. */
  @Transactional
  public PurgeCounts purgePendingSignups(Instant now) {
    int expired = pendingSignups.deleteExpiredBefore(now.minus(Duration.ofDays(expiredDays)));
    int completed = pendingSignups.deleteCompletedBefore(now.minus(Duration.ofDays(completedDays)));
    PurgeCounts counts = new PurgeCounts(expired, completed, 0, 0);
    if (counts.any()) {
      log.info("Pending signup retention: expired={}, completed={}", expired, completed);
    }
    return counts;
  }

  /** Removes raw signup links from the publication log and from sent mails' outbox copies. */
  @Transactional
  public PurgeCounts removeSpentLinks(Instant now) {
    int delivered = linkPublications.deleteDelivered();
    int undelivered = linkPublications.deleteUndeliveredBefore(now.minus(LINK_UNUSABLE_AFTER));
    if (undelivered > 0) {
      // The verification mail of these signups was never queued; the stuck-event monitor will
      // report their markers as resolved because the publications are gone.
      log.warn(
          "Signup link removal: dropped {} undelivered verification publications whose link expired",
          undelivered);
    }
    int mails =
        emailOutbox.redactLinks(
            TenantContext.SYSTEM_TENANT_ID,
            SIGNUP_LINK_PATTERN,
            SIGNUP_LINK_REPLACEMENT,
            linkMinutesAfterSending,
            (int) LINK_UNUSABLE_AFTER.toMinutes(),
            (int) OUTBOX_LOOKBACK.toMinutes());
    int publications = delivered + undelivered;
    PurgeCounts counts = new PurgeCounts(0, 0, publications, mails);
    if (counts.any()) {
      log.info("Signup link removal: eventPublications={}, outboxMails={}", publications, mails);
    }
    return counts;
  }

  /** What one run removed. */
  public record PurgeCounts(int expired, int completed, int publications, int mails) {
    boolean any() {
      return expired + completed + publications + mails > 0;
    }
  }
}
