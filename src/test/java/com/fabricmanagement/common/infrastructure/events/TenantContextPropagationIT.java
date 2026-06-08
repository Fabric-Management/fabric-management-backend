package com.fabricmanagement.common.infrastructure.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
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
@Import(TenantContextPropagationIT.TestListener.class)
@Slf4j
class TenantContextPropagationIT {

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
  @Autowired private TestListener listener;
  @Autowired private TransactionTemplate transactionTemplate;

  @BeforeEach
  void setup() {
    TenantContext.clear();
    listener.clear();
  }

  @AfterEach
  void cleanup() {
    TenantContext.clear();
  }

  @Test
  void happyPath_withContext_restoresTenantInAsyncListener() {
    // Arrange
    UUID expectedTenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    String expectedTenantUid = "TENANT-UID-123";
    UUID expectedUserId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    String expectedCountry = "TR";

    TenantContext.setCurrentTenantId(expectedTenantId);
    TenantContext.setCurrentTenantUid(expectedTenantUid);
    TenantContext.setCurrentUserId(expectedUserId);
    TenantContext.setCurrentTenantCountry(expectedCountry);

    TestEvent event = new TestEvent(expectedTenantId, "HAPPY_PATH_EVENT");

    // Act
    transactionTemplate.executeWithoutResult(status -> publisher.publish(event));

    // Assert
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(listener.getReceivedEvents()).hasSize(1);
              TestListener.EventResult result = listener.getReceivedEvents().get(0);
              assertThat(result.eventId()).isEqualTo(event.getEventId());
              assertThat(result.threadName()).isNotEqualTo(Thread.currentThread().getName());
              assertThat(result.dbTenantId()).isEqualTo(expectedTenantId.toString());

              // Validate all 4 fields of TenantContext are propagated via ThreadLocalAccessor
              assertThat(result.threadTenantId()).isEqualTo(expectedTenantId);
              assertThat(result.threadTenantUid()).isEqualTo(expectedTenantUid);
              assertThat(result.threadUserId()).isEqualTo(expectedUserId);
              assertThat(result.threadCountry()).isEqualTo(expectedCountry);
            });
  }

  @Test
  void failClosedPath_emptyContext_aspectRestoresTenantInAsyncListener() {
    // Arrange
    UUID expectedTenantId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    // Ensure ThreadLocal is EMPTY
    TenantContext.clear();
    TestEvent event = new TestEvent(expectedTenantId, "FAIL_CLOSED_EVENT");

    // Act
    transactionTemplate.executeWithoutResult(status -> publisher.publish(event));

    // Assert
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(listener.getReceivedEvents()).hasSize(1);
              TestListener.EventResult result = listener.getReceivedEvents().get(0);
              assertThat(result.eventId()).isEqualTo(event.getEventId());
              assertThat(result.threadName()).isNotEqualTo(Thread.currentThread().getName());
              // Aspect restores TenantContext -> MTCP binds connection correctly
              assertThat(result.dbTenantId()).isEqualTo(expectedTenantId.toString());
            });
  }

  public static class TestEvent extends DomainEvent {
    public TestEvent(UUID tenantId, String type) {
      super(tenantId, type);
    }
  }

  @Slf4j
  @Component
  public static class TestListener {
    @PersistenceContext private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

    @Getter
    private final CopyOnWriteArrayList<EventResult> receivedEvents = new CopyOnWriteArrayList<>();

    // We use TransactionTemplate programmatically to avoid annotation conflicts
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(TestEvent event) {
      log.info(
          "Received event: {} in thread {}",
          event.getEventType(),
          Thread.currentThread().getName());

      TransactionTemplate tt = new TransactionTemplate(transactionManager);
      tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      tt.setReadOnly(true);

      try {
        String dbTenantId =
            tt.execute(
                status ->
                    (String)
                        entityManager
                            .createNativeQuery("SELECT current_setting('app.current_tenant', true)")
                            .getSingleResult());
        receivedEvents.add(
            new EventResult(
                event.getEventId(),
                dbTenantId,
                Thread.currentThread().getName(),
                TenantContext.getCurrentTenantIdOrNull(),
                TenantContext.getCurrentTenantUid(),
                TenantContext.getCurrentUserId(),
                TenantContext.getCurrentTenantCountry()));
      } catch (Exception e) {
        log.error("Failed to execute DB query in listener", e);
        receivedEvents.add(
            new EventResult(
                event.getEventId(),
                "ERROR: " + e.getMessage(),
                Thread.currentThread().getName(),
                TenantContext.getCurrentTenantIdOrNull(),
                TenantContext.getCurrentTenantUid(),
                TenantContext.getCurrentUserId(),
                TenantContext.getCurrentTenantCountry()));
      }
    }

    public void clear() {
      receivedEvents.clear();
    }

    public record EventResult(
        UUID eventId,
        String dbTenantId,
        String threadName,
        UUID threadTenantId,
        String threadTenantUid,
        UUID threadUserId,
        String threadCountry) {}
  }
}
