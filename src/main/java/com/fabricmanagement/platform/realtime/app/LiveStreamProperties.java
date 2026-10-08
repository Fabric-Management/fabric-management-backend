package com.fabricmanagement.platform.realtime.app;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Technical defaults of the live channel (CEDIT-05 §5). They bound one backend instance's work and
 * resources; they are not an SLA and not a global quota across instances. Values are read on every
 * check, so switching the channel off takes effect on running connections too.
 */
@Component
@ConfigurationProperties(prefix = "application.realtime")
@Getter
@Setter
public class LiveStreamProperties implements InitializingBean {

  /** Whether new streams may open and running ones may continue. Off unless configured. */
  private volatile boolean enabled = false;

  /** How often each connection re-reads its resource's committed revision and access. */
  private volatile Duration pollInterval = Duration.ofSeconds(2);

  /** Longest quiet period before a keepalive comment is sent after a successful check. */
  private volatile Duration heartbeatInterval = Duration.ofSeconds(15);

  /** A connection is closed with RECONNECT_REQUIRED at the latest after this long. */
  private volatile Duration maxConnectionLifetime = Duration.ofSeconds(60);

  private volatile int maxConnectionsPerInstance = 128;

  private volatile int maxConnectionsPerTenantPerInstance = 64;

  /** Per user and tenant on this instance; leaves room for several tabs of one person. */
  private volatile int maxConnectionsPerUserPerTenantPerInstance = 8;

  /** Threads that run checks and writes; one connection never has more than one job. */
  private volatile int workerCount = 4;

  /** Checks waiting for a worker; a full queue skips the check until the next tick. */
  private volatile int workerQueueCapacity = 128;

  /** A write still running after this long marks the client as a slow consumer. */
  private volatile Duration sendTimeout = Duration.ofSeconds(5);

  /** Transaction timeout of one revision read; a slower read closes the connection. */
  private volatile Duration readTimeout = Duration.ofSeconds(3);

  /** Sent as Retry-After when a stream is refused for capacity or availability. */
  private volatile Duration retryAfter = Duration.ofSeconds(5);

  @Override
  public void afterPropertiesSet() {
    requirePositive("poll-interval", pollInterval);
    requirePositive("heartbeat-interval", heartbeatInterval);
    requirePositive("max-connection-lifetime", maxConnectionLifetime);
    requirePositive("send-timeout", sendTimeout);
    requirePositive("read-timeout", readTimeout);
    requirePositive("retry-after", retryAfter);
    requirePositive("max-connections-per-instance", maxConnectionsPerInstance);
    requirePositive("max-connections-per-tenant-per-instance", maxConnectionsPerTenantPerInstance);
    requirePositive(
        "max-connections-per-user-per-tenant-per-instance",
        maxConnectionsPerUserPerTenantPerInstance);
    requirePositive("worker-count", workerCount);
    requirePositive("worker-queue-capacity", workerQueueCapacity);
  }

  /** The emitter's own timeout: a backstop past every bound the channel closes on by itself. */
  public Duration emitterTimeout() {
    return maxConnectionLifetime
        .plus(pollInterval)
        .plus(readTimeout)
        .plus(sendTimeout)
        .plusSeconds(5);
  }

  /** Retry-After in whole seconds, never zero. */
  public long retryAfterSeconds() {
    return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
  }

  private static void requirePositive(String name, Duration value) {
    if (value == null || value.isZero() || value.isNegative()) {
      throw new IllegalStateException("application.realtime." + name + " must be positive");
    }
  }

  private static void requirePositive(String name, int value) {
    if (value < 1) {
      throw new IllegalStateException("application.realtime." + name + " must be positive");
    }
  }
}
