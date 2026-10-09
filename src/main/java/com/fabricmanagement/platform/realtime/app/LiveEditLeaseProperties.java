package com.fabricmanagement.platform.realtime.app;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Technical defaults of field leases (CEDIT-07 §3.1). Not business rules and not measured; each one
 * is published to the client, which never hard-codes them.
 *
 * <ul>
 *   <li>{@code ttl} 90 s, {@code renewAfter} 30 s: the same rhythm as edit sessions (CEDIT-06). A
 *       browser runs a background tab's timers about once a minute, so one missed renewal is
 *       survived; a closed tab or a lost network frees the field within one lifetime.
 *   <li>{@code idleAfter} 5 min, {@code idleWarning} 60 s: the client renews only while the person
 *       really works in the form (typing, choosing, focusing a field); after {@code idleAfter}
 *       without such input it stops renewing, and it warns {@code idleWarning} before that. An open
 *       connection, a presence renewal or a stream keepalive is never activity. The server cannot
 *       see input, so it cannot enforce this; what it enforces is that nothing lives longer than
 *       {@code ttl} after the last renewal and that no lease outlives its edit session.
 *   <li>Bounds keep one session or one resource from growing rows or work without limit: {@code
 *       maxKeysPerRequest} keys per acquire, {@code maxLeasesPerSession} held by one tab (as many
 *       as one save may change line operations: 200), {@code maxLeasesPerResource} held on one
 *       record by everybody.
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "application.realtime.edit-lease")
@Getter
@Setter
public class LiveEditLeaseProperties implements InitializingBean {

  private Duration ttl = Duration.ofSeconds(90);

  private Duration renewAfter = Duration.ofSeconds(30);

  private Duration idleAfter = Duration.ofMinutes(5);

  private Duration idleWarning = Duration.ofSeconds(60);

  private int maxKeysPerRequest = 50;

  private int maxLeasesPerSession = 200;

  private int maxLeasesPerResource = 1000;

  /** Ended rows (released, expired or of an old generation) are deleted after this long. */
  private Duration retention = Duration.ofDays(1);

  private boolean cleanupEnabled = true;

  private int cleanupBatchSize = 500;

  @Override
  public void afterPropertiesSet() {
    requirePositive("ttl", ttl);
    requirePositive("renew-after", renewAfter);
    requirePositive("idle-after", idleAfter);
    requirePositive("idle-warning", idleWarning);
    requirePositive("retention", retention);
    if (renewAfter.compareTo(ttl) >= 0) {
      throw new IllegalStateException(
          "application.realtime.edit-lease.renew-after must be shorter than ttl");
    }
    if (idleWarning.compareTo(idleAfter) >= 0) {
      throw new IllegalStateException(
          "application.realtime.edit-lease.idle-warning must be shorter than idle-after");
    }
    requirePositive("max-keys-per-request", maxKeysPerRequest);
    requirePositive("max-leases-per-session", maxLeasesPerSession);
    requirePositive("max-leases-per-resource", maxLeasesPerResource);
    requirePositive("cleanup-batch-size", cleanupBatchSize);
    if (maxKeysPerRequest > maxLeasesPerSession || maxLeasesPerSession > maxLeasesPerResource) {
      throw new IllegalStateException(
          "application.realtime.edit-lease bounds must grow: request <= session <= resource");
    }
  }

  public long ttlSeconds() {
    return Math.max(1, ttl.toSeconds());
  }

  public long renewAfterSeconds() {
    return Math.max(1, renewAfter.toSeconds());
  }

  public long idleAfterSeconds() {
    return Math.max(1, idleAfter.toSeconds());
  }

  public long idleWarningSeconds() {
    return Math.max(1, idleWarning.toSeconds());
  }

  private static void requirePositive(String name, Duration value) {
    if (value == null || value.isZero() || value.isNegative()) {
      throw new IllegalStateException(
          "application.realtime.edit-lease." + name + " must be positive");
    }
  }

  private static void requirePositive(String name, int value) {
    if (value < 1) {
      throw new IllegalStateException(
          "application.realtime.edit-lease." + name + " must be positive");
    }
  }
}
