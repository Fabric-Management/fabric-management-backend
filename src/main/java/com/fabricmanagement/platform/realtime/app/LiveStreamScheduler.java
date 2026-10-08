package com.fabricmanagement.platform.realtime.app;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Drives the live channel: one daemon thread calls {@link LiveStreamService#tick()} at a short
 * fixed delay. The tick only decides which connections are due and hands them to the bounded worker
 * pool; it never reads the database or writes to a socket. Runs in every profile; when the channel
 * is switched off it closes running connections and otherwise has nothing to do. On shutdown it
 * stops before the web server, closes every stream with RECONNECT_REQUIRED and stops the workers.
 */
@Component
@Slf4j
public class LiveStreamScheduler implements SmartLifecycle {

  private static final Duration MIN_TICK = Duration.ofMillis(50);
  private static final Duration MAX_TICK = Duration.ofMillis(500);

  private final LiveStreamService service;
  private final LiveStreamProperties properties;
  private ScheduledExecutorService ticker;
  private volatile boolean running;

  public LiveStreamScheduler(LiveStreamService service, LiveStreamProperties properties) {
    this.service = service;
    this.properties = properties;
  }

  @Override
  public synchronized void start() {
    if (running) {
      return;
    }
    ticker =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "live-stream-tick");
              thread.setDaemon(true);
              return thread;
            });
    long period = tick().toMillis();
    ticker.scheduleWithFixedDelay(this::tickSafely, period, period, TimeUnit.MILLISECONDS);
    running = true;
  }

  @Override
  public synchronized void stop() {
    if (!running) {
      return;
    }
    running = false;
    ticker.shutdownNow();
    service.shutdown();
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  /** A quarter of the poll interval, within 50–500 ms. */
  Duration tick() {
    Duration quarter = properties.getPollInterval().dividedBy(4);
    if (quarter.compareTo(MIN_TICK) < 0) {
      return MIN_TICK;
    }
    return quarter.compareTo(MAX_TICK) > 0 ? MAX_TICK : quarter;
  }

  private void tickSafely() {
    try {
      service.tick();
    } catch (RuntimeException failure) {
      // A failed pass must not cancel the schedule; the next pass sees the same connections.
      log.warn("Live stream tick failed: {}", failure.getClass().getSimpleName(), failure);
    }
  }
}
