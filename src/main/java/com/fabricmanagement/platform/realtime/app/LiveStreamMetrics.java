package com.fabricmanagement.platform.realtime.app;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.IntSupplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Live channel measurements (CEDIT-05 §5). Labels are fixed reasons and resource kinds only: no
 * tenant, user, resource or connection id ever becomes a metric label.
 */
@Component
public class LiveStreamMetrics {

  private final MeterRegistry registry;

  @Autowired
  public LiveStreamMetrics(ObjectProvider<MeterRegistry> registry) {
    this(registry.getIfAvailable());
  }

  LiveStreamMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  void bind(
      IntSupplier connections,
      IntSupplier transports,
      IntSupplier closing,
      IntSupplier stalled,
      ThreadPoolExecutor workers) {
    if (registry == null) {
      return;
    }
    Gauge.builder("live.stream.connections.active", connections, IntSupplier::getAsInt)
        .description("Live connections registered on this instance")
        .register(registry);
    Gauge.builder("live.stream.transports.open", transports, IntSupplier::getAsInt)
        .description("Stream responses the container has not reported ended yet")
        .register(registry);
    Gauge.builder("live.stream.transports.closing", closing, IntSupplier::getAsInt)
        .description("Stream responses whose connection is closed but whose end is not reported")
        .register(registry);
    Gauge.builder("live.stream.transports.stalled", stalled, IntSupplier::getAsInt)
        .description("Stream responses, open or closing, whose client is not taking bytes")
        .register(registry);
    if (workers != null) {
      Gauge.builder("live.stream.workers.queue", workers, executor -> executor.getQueue().size())
          .description("Live checks waiting for a worker")
          .register(registry);
    }
  }

  void opened(String resourceType) {
    count("live.stream.connections.opened", "resource", resourceType);
  }

  void closed(String cause) {
    count("live.stream.connections.closed", "reason", cause);
  }

  void rejected(String reason) {
    count("live.stream.connections.rejected", "reason", reason);
  }

  void checked(String resourceType, Duration took, boolean failed) {
    if (registry == null) {
      return;
    }
    Timer.builder("live.stream.checks")
        .tag("resource", resourceType)
        .tag("outcome", failed ? "failed" : "ok")
        .register(registry)
        .record(took);
  }

  /** A transport ended at once, with unsent bytes dropped where the container held any. */
  void transportAborted() {
    count("live.stream.transports.aborted", "pool", "live");
  }

  /** The container's async timeout fired: the backstop behind the scheduler. */
  void transportTimedOut() {
    count("live.stream.transports.timeouts", "pool", "live");
  }

  void workerRejected() {
    count("live.stream.workers.rejected", "pool", "live");
  }

  private void count(String name, String tag, String value) {
    if (registry != null) {
      registry.counter(name, tag, value).increment();
    }
  }
}
