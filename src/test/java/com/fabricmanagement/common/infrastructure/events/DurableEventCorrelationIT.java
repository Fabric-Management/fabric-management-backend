package com.fabricmanagement.common.infrastructure.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.micrometer.tracing.Tracer;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Import(DurableEventCorrelationIT.TestDurableListener.class)
@Slf4j
class DurableEventCorrelationIT {

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
  @Autowired private TestDurableListener listener;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private Tracer tracer;

  @BeforeEach
  void setup() {
    listener.clear();
  }

  @Test
  void durableListener_inheritsCorrelationIdFromDomainEvent() {
    // Arrange
    UUID expectedTenantId = UUID.fromString("55555555-5555-5555-5555-555555555555");

    // Set up MDC artificially as if a request is incoming
    String expectedCorrelationId = "test-correlation-" + UUID.randomUUID().toString();
    MDC.put("traceId", expectedCorrelationId);

    TestDurableEvent event;
    try {
      event = new TestDurableEvent(expectedTenantId, "DURABLE_EVENT");
    } finally {
      MDC.remove("traceId");
    }

    // Act
    transactionTemplate.executeWithoutResult(status -> publisher.publish(event));

    // Assert
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(listener.getReceivedCorrelations()).hasSize(1);
              TestDurableListener.CorrelationResult result =
                  listener.getReceivedCorrelations().get(0);

              // Verify it ran in a different thread
              assertThat(result.threadName()).isNotEqualTo(Thread.currentThread().getName());

              // Verify correlationId propagated correctly to MDC during the listener execution
              assertThat(result.mdcCorrelationId()).isEqualTo(expectedCorrelationId);
            });
  }

  public static class TestDurableEvent extends DomainEvent {
    public TestDurableEvent(UUID tenantId, String type) {
      super(tenantId, type);
    }
  }

  @Slf4j
  @Component
  public static class TestDurableListener {

    @Getter
    private final CopyOnWriteArrayList<CorrelationResult> receivedCorrelations =
        new CopyOnWriteArrayList<>();

    @ApplicationModuleListener
    public void handle(TestDurableEvent event) {
      String mdcCorrelationId = MDC.get("correlationId");

      log.info(
          "Received event: {} in thread {} with correlationId: {}",
          event.getEventType(),
          Thread.currentThread().getName(),
          mdcCorrelationId);

      receivedCorrelations.add(
          new CorrelationResult(mdcCorrelationId, Thread.currentThread().getName()));
    }

    public void clear() {
      receivedCorrelations.clear();
    }

    public record CorrelationResult(String mdcCorrelationId, String threadName) {}
  }
}
