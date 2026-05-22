package com.fabricmanagement.common.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DisabledIf(value = "dockerNotAvailable", disabledReason = "Docker is not available")
@DisplayName("DocumentNumberGenerator Concurrency & Transaction IT")
class DocumentNumberGeneratorConcurrencyIT {

  static boolean dockerNotAvailable() {
    return !org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
  }

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
          .withDatabaseName("fabric_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @Autowired private DocumentNumberGenerator documentNumberGenerator;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private UUID tenantId;

  @BeforeEach
  void setUpTenant() {
    long timestamp = System.currentTimeMillis();
    String uid = "TEN-" + timestamp % 100000;

    Tenant tenant = Tenant.create("DocGen IT " + timestamp, uid);
    tenant.activate("test");
    tenant = tenantRepository.save(tenant);
    tenantId = tenant.getId();

    TenantContext.setCurrentTenantId(tenantId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("Fails early if no active transaction (MANDATORY propagation)")
  void failsIfNoTransaction() {
    assertThatThrownBy(
            () -> documentNumberGenerator.generate(tenantId, "TEST", "TT", LocalDate.now(), 5))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  @DisplayName("Sequential calls within same transaction increment correctly (L2)")
  void sequentialCallsWithinSameTxIncrement() {
    transactionTemplate.executeWithoutResult(
        status -> {
          LocalDate date = LocalDate.of(2026, 5, 21);
          String n1 = documentNumberGenerator.generate(tenantId, "TEST_SEQ", "TX", date, 5);
          String n2 = documentNumberGenerator.generate(tenantId, "TEST_SEQ", "TX", date, 5);

          assertThat(n1).isEqualTo("TX-20260521-00001");
          assertThat(n2).isEqualTo("TX-20260521-00002");
        });
  }

  @Test
  @DisplayName("Concurrent generation guarantees unique, gap-free sequence numbers")
  void concurrentGenerationGuaranteesUniqueness() throws InterruptedException {
    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    Set<String> generatedNumbers = ConcurrentHashMap.newKeySet();
    LocalDate date = LocalDate.of(2026, 5, 21);

    Callable<String> task =
        () -> {
          TenantContext.setCurrentTenantId(tenantId);
          try {
            return transactionTemplate.execute(
                status -> documentNumberGenerator.generate(tenantId, "TEST_CONC", "CC", date, 5));
          } finally {
            TenantContext.clear();
          }
        };

    List<Callable<String>> tasks = Collections.nCopies(threadCount, task);
    List<Future<String>> futures = executor.invokeAll(tasks);

    futures.forEach(
        f -> {
          try {
            generatedNumbers.add(f.get());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });

    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);

    assertThat(generatedNumbers).hasSize(threadCount);
    // Should contain exactly CC-20260521-00001 to CC-20260521-00010
    for (int i = 1; i <= threadCount; i++) {
      assertThat(generatedNumbers).contains("CC-20260521-" + String.format("%05d", i));
    }
  }

  @Test
  @DisplayName("Transaction rollback guarantees sequence rollback (gap-free) (L1)")
  void rollbackGuaranteesSequenceRollback() {
    // Note: document_sequence is a regular table row (not a PostgreSQL SEQUENCE),
    // so TX rollback reverts the row update, making the same counter available again.
    LocalDate date = LocalDate.of(2026, 5, 21);

    // Call 1: Success
    transactionTemplate.executeWithoutResult(
        status -> {
          String n1 = documentNumberGenerator.generate(tenantId, "TEST_ROLLBACK", "RB", date, 5);
          assertThat(n1).isEqualTo("RB-20260521-00001");
        });

    // Call 2: Fails and rolls back
    try {
      transactionTemplate.executeWithoutResult(
          status -> {
            documentNumberGenerator.generate(tenantId, "TEST_ROLLBACK", "RB", date, 5);
            throw new RuntimeException("Simulated failure to trigger rollback");
          });
    } catch (RuntimeException ignored) {
      // Expected
    }

    // Call 3: Success, should get the sequence number that was rolled back
    transactionTemplate.executeWithoutResult(
        status -> {
          String n3 = documentNumberGenerator.generate(tenantId, "TEST_ROLLBACK", "RB", date, 5);
          assertThat(n3).isEqualTo("RB-20260521-00002");
        });
  }
}
