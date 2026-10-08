package com.fabricmanagement.platform.realtime.app;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Technical defaults of edit sessions (CEDIT-06 §2.1). Not business rules and not measured: the
 * lifetime survives a background tab whose timers a browser runs only once a minute, and a closed
 * tab drops out of presence within one lifetime.
 */
@Component
@ConfigurationProperties(prefix = "application.realtime.edit-session")
@Getter
@Setter
public class LiveEditSessionProperties implements InitializingBean {

  /** How long a session stays live after it was opened or last renewed. */
  private Duration ttl = Duration.ofSeconds(90);

  /** When the client renews; well inside the lifetime so one missed renewal is survived. */
  private Duration renewAfter = Duration.ofSeconds(30);

  /** Ended sessions (closed or expired) are deleted after this long. */
  private Duration retention = Duration.ofDays(7);

  private boolean cleanupEnabled = true;

  private int cleanupBatchSize = 500;

  @Override
  public void afterPropertiesSet() {
    requirePositive("ttl", ttl);
    requirePositive("renew-after", renewAfter);
    requirePositive("retention", retention);
    if (renewAfter.compareTo(ttl) >= 0) {
      throw new IllegalStateException(
          "application.realtime.edit-session.renew-after must be shorter than ttl");
    }
    if (cleanupBatchSize < 1) {
      throw new IllegalStateException(
          "application.realtime.edit-session.cleanup-batch-size must be positive");
    }
  }

  /** Renewal interval in whole seconds, never zero. */
  public long renewAfterSeconds() {
    return Math.max(1, renewAfter.toSeconds());
  }

  private static void requirePositive(String name, Duration value) {
    if (value == null || value.isZero() || value.isNegative()) {
      throw new IllegalStateException(
          "application.realtime.edit-session." + name + " must be positive");
    }
  }
}
