package com.fabricmanagement.common.infrastructure.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.IncompleteEventPublications;
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
@Import(DurableEventDeliveryIT.TestEventListener.class)
class DurableEventDeliveryIT {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
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

  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TestEventListener testEventListener;
  @Autowired private IncompleteEventPublications incompletePublications;

  private static final UUID TENANT_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    testEventListener.reset();
    jdbcTemplate.execute("DELETE FROM processed_event");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("E4-1: Normal Flow - Event published, consumed once, idempotency recorded")
  void normalFlow_consumedOnceAndRecorded() {
    DummyEvent event = new DummyEvent(TENANT_ID, "Test Payload");

    // Act
    transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

    // Assert
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(testEventListener.getInvocationCount()).isEqualTo(1);
            });

    Integer processedCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM processed_event WHERE event_id = ? AND listener_id = ?",
            Integer.class,
            event.getEventId(),
            TestEventListener.class.getSimpleName() + "#handleDummyEvent");
    assertThat(processedCount).isEqualTo(1);
  }

  @Test
  @DisplayName("E4-2: Idempotency - Same event processed multiple times only runs once")
  void idempotency_preventsMultipleExecutions() {
    DummyEvent event = new DummyEvent(TENANT_ID, "Test Payload");

    // Act 1
    transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(testEventListener.getInvocationCount()).isEqualTo(1);
            });

    // Act 2 - Publish same event again
    transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

    // Wait a bit to ensure it had time to process if it were going to
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
    }

    // Assert still 1
    assertThat(testEventListener.getInvocationCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("E4-3: Error Flow - Transaction rolled back if handler fails")
  void errorFlow_transactionRolledBack() {
    DummyEvent event = new DummyEvent(TENANT_ID, "Error"); // Trigger error

    // Act
    transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

    // Assert
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              // Modulith marks it as incomplete
              assertThat(testEventListener.getInvocationCount()).isEqualTo(1); // Tried once, failed
            });

    // Processed event should NOT exist because transaction rolled back
    Integer processedCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM processed_event WHERE event_id = ?",
            Integer.class,
            event.getEventId());
    assertThat(processedCount).isEqualTo(0);
  }

  @Test
  @DisplayName("E4-4: Resubmission - Poison message resubmitted by Outbox Poller")
  void poisonMessage_resubmittedSuccessfully() {
    DummyEvent event =
        new DummyEvent(TENANT_ID, "TransientError"); // Fails first time, succeeds next

    // Act 1: Publish, it will fail (simulating @Retryable exhaustion)
    transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(testEventListener.getInvocationCount()).isEqualTo(1); // Tried once, failed
            });

    // Act 2: Manually trigger the EventResubmissionJob logic
    incompletePublications.resubmitIncompletePublicationsOlderThan(Duration.ZERO);

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(testEventListener.getInvocationCount())
                  .isEqualTo(2); // Retried and succeeded
            });

    // Processed event should NOW exist because it succeeded on retry
    Integer processedCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM processed_event WHERE event_id = ?",
            Integer.class,
            event.getEventId());
    assertThat(processedCount).isEqualTo(1);
  }

  // Dummy event class
  public static class DummyEvent extends DomainEvent {
    private final String payload;

    public DummyEvent(UUID tenantId, String payload) {
      super(tenantId, "DUMMY_EVENT");
      this.payload = payload;
    }

    public String getPayload() {
      return payload;
    }
  }

  // Dummy listener
  @Component
  @Slf4j
  public static class TestEventListener {
    private final AtomicInteger invocationCount = new AtomicInteger(0);

    @Autowired private IdempotentEventHandler idempotentHandler;

    @ApplicationModuleListener
    public void handleDummyEvent(DummyEvent event) {
      log.info("TestEventListener received event: {}", event.getEventId());
      idempotentHandler.executeOnce(
          event.getEventId(),
          this.getClass(),
          "handleDummyEvent",
          () -> {
            log.info("TestEventListener executing handler for event: {}", event.getEventId());
            invocationCount.incrementAndGet();
            if ("Error".equals(event.getPayload())) {
              throw new RuntimeException("Simulated error");
            }
            if ("TransientError".equals(event.getPayload()) && invocationCount.get() == 1) {
              throw new RuntimeException("Simulated transient error on first attempt");
            }
          });
    }

    public int getInvocationCount() {
      return invocationCount.get();
    }

    public void reset() {
      invocationCount.set(0);
    }
  }
}
