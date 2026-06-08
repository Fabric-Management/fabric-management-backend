package com.fabricmanagement.common.infrastructure.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Import(AsyncTracePropagationIT.TestTraceListener.class)
@Slf4j
class AsyncTracePropagationIT {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("fabric_test")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @DynamicPropertySource
  static void registerPgProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("spring.flyway.enabled", () -> "true");
  }

  @Autowired private DomainEventPublisher publisher;
  @Autowired private TestTraceListener listener;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private Tracer tracer;
  @Autowired private ObservationRegistry observationRegistry;

  @BeforeEach
  void setup() {
    listener.clear();
  }

  @Test
  void asyncListener_inheritsTraceIdFromPublisherThread() {
    // Arrange
    UUID expectedTenantId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    TestTraceEvent event = new TestTraceEvent(expectedTenantId, "TRACE_PROPAGATION_EVENT");

    // Start a manual observation to simulate an active incoming request
    Observation observation =
        Observation.createNotStarted("test-publisher-observation", observationRegistry).start();
    String expectedTraceId;
    String parentSpanId;

    try (Observation.Scope scope = observation.openScope()) {
      expectedTraceId = tracer.currentSpan().context().traceId();
      parentSpanId = tracer.currentSpan().context().spanId();
      // Act
      transactionTemplate.executeWithoutResult(status -> publisher.publish(event));
    } finally {
      observation.stop();
    }

    // Assert
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(listener.getReceivedTraces()).hasSize(1);
              TestTraceListener.TraceResult result = listener.getReceivedTraces().get(0);

              // Verify it ran in a different thread
              assertThat(result.threadName()).isNotEqualTo(Thread.currentThread().getName());

              // Verify traceID propagated correctly
              assertThat(result.traceId()).isEqualTo(expectedTraceId);

              assertThat(result.spanId()).isNotEqualTo("none");
            });
  }

  public static class TestTraceEvent extends DomainEvent {
    public TestTraceEvent(UUID tenantId, String type) {
      super(tenantId, type);
    }
  }

  @Slf4j
  @Component
  public static class TestTraceListener {
    @Autowired private Tracer tracer;
    @Autowired private ObservationRegistry observationRegistry;

    @Getter
    private final CopyOnWriteArrayList<TraceResult> receivedTraces = new CopyOnWriteArrayList<>();

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(TestTraceEvent event) {
      Span currentSpan = tracer.currentSpan();
      String traceId = currentSpan != null ? currentSpan.context().traceId() : "none";
      String spanId = currentSpan != null ? currentSpan.context().spanId() : "none";

      log.info(
          "Received event: {} in thread {} with traceId: {}",
          event.getEventType(),
          Thread.currentThread().getName(),
          traceId);

      receivedTraces.add(new TraceResult(traceId, spanId, Thread.currentThread().getName()));
    }

    public void clear() {
      receivedTraces.clear();
    }

    public record TraceResult(String traceId, String spanId, String threadName) {}
  }
}
