package com.fabricmanagement.production.execution.stockunit.app.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.procurement.subcontract.api.query.SubcontractOrderQueryService;
import com.fabricmanagement.procurement.subcontract.api.query.SubcontractOrderQueryService.SubcontractOutputInfo;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import com.fabricmanagement.production.execution.goodsreceipt.domain.event.GoodsReceiptConfirmedEvent;
import com.fabricmanagement.production.execution.stockunit.app.StockUnitService;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    properties = {
      "modulith.events.stuck-monitor.enabled=false",
      "modulith.events.resubmit.interval-ms=3600000"
    })
@ActiveProfiles("test")
@Testcontainers
class GoodsReceiptConfirmedEventListenerIT {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final String PROCESSED_LISTENER_ID =
      GoodsReceiptConfirmedEventListener.class.getSimpleName() + "#onGoodsReceiptConfirmed";

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
  @Autowired private MeterRegistry meterRegistry;

  @MockBean private StockUnitService stockUnitService;
  @MockBean private BatchRepository batchRepository;
  @MockBean private StockUnitRepository stockUnitRepository;
  @MockBean private SubcontractOrderQueryService subcontractOrderQueryService;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    jdbcTemplate.update("DELETE FROM processed_event WHERE listener_id = ?", PROCESSED_LISTENER_ID);
    jdbcTemplate.update(
        "DELETE FROM event_publication WHERE listener_id LIKE ?",
        "%GoodsReceiptConfirmedEventListener%");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void purchaseOrderFailureLeavesPublicationIncompleteAndProcessedEventAbsent() {
    GoodsReceiptConfirmedEvent event = event(GoodsReceiptSourceType.PURCHASE_ORDER, oneItem());

    publishAndAwaitFailure(event);

    verify(stockUnitService, never()).createBulk(any(), any(), any());
  }

  @Test
  void emptyReceiptFailureLeavesPublicationIncompleteAndProcessedEventAbsent() {
    GoodsReceiptConfirmedEvent event = event(GoodsReceiptSourceType.BATCH, List.of());

    publishAndAwaitFailure(event);

    verify(stockUnitRepository, never())
        .existsByTenantIdAndSourceTypeAndSourceId(any(), any(), any());
    verify(stockUnitService, never()).createBulk(any(), any(), any());
  }

  @Test
  void missingSubcontractOutputTypeLeavesPublicationIncompleteAndProcessedEventAbsent() {
    GoodsReceiptConfirmedEvent event = event(GoodsReceiptSourceType.SUBCONTRACT_ORDER, oneItem());
    when(stockUnitRepository.existsByTenantIdAndSourceTypeAndSourceId(
            eq(TENANT_ID), eq(StockUnitSourceType.GOODS_RECEIPT), any()))
        .thenReturn(false);
    when(subcontractOrderQueryService.getSubcontractOutputInfo(TENANT_ID, event.getSourceId()))
        .thenReturn(new SubcontractOutputInfo("SC-001", UUID.randomUUID(), null, "KG", null));

    publishAndAwaitFailure(event);

    verify(stockUnitService, never()).createBulk(any(), any(), any());
  }

  @Test
  void missingBatchLeavesPublicationIncompleteAndProcessedEventAbsent() {
    GoodsReceiptConfirmedEvent event = event(GoodsReceiptSourceType.BATCH, oneItem());
    when(stockUnitRepository.existsByTenantIdAndSourceTypeAndSourceId(
            eq(TENANT_ID), eq(StockUnitSourceType.GOODS_RECEIPT), any()))
        .thenReturn(false);
    when(batchRepository.findByIdAndTenantId(event.getSourceId(), TENANT_ID))
        .thenReturn(Optional.empty());

    publishAndAwaitFailure(event);

    verify(stockUnitService, never()).createBulk(any(), any(), any());
  }

  @Test
  void duplicateGuardCompletesPublicationWithoutCreatingUnits() {
    GoodsReceiptConfirmedEvent event = event(GoodsReceiptSourceType.BATCH, oneItem());
    when(stockUnitRepository.existsByTenantIdAndSourceTypeAndSourceId(
            TENANT_ID, StockUnitSourceType.GOODS_RECEIPT, event.getItems().get(0).itemId()))
        .thenReturn(true);

    publishAndAwaitSuccess(event);

    verify(batchRepository, never()).findByIdAndTenantId(any(), any());
    verify(stockUnitService, never()).createBulk(any(), any(), any());
  }

  @Test
  void batchHappyPathCreatesUnitsAndCompletesPublication() {
    GoodsReceiptConfirmedEvent event = event(GoodsReceiptSourceType.BATCH, oneItem());
    Batch batch =
        Batch.builder()
            .productType(ProductType.FABRIC)
            .unit("KG")
            .locationId(UUID.randomUUID())
            .build();
    when(stockUnitRepository.existsByTenantIdAndSourceTypeAndSourceId(
            eq(TENANT_ID), eq(StockUnitSourceType.GOODS_RECEIPT), any()))
        .thenReturn(false);
    when(batchRepository.findByIdAndTenantId(event.getSourceId(), TENANT_ID))
        .thenReturn(Optional.of(batch));

    publishAndAwaitSuccess(event);

    verify(stockUnitService)
        .createBulk(eq(event.getSourceId()), any(), eq(TenantContext.SYSTEM_ACTOR_ID));
  }

  @Test
  void subcontractHappyPathCreatesUnitsAndCompletesPublication() {
    GoodsReceiptConfirmedEvent event = event(GoodsReceiptSourceType.SUBCONTRACT_ORDER, oneItem());
    UUID batchId = UUID.randomUUID();
    when(stockUnitRepository.existsByTenantIdAndSourceTypeAndSourceId(
            eq(TENANT_ID), eq(StockUnitSourceType.GOODS_RECEIPT), any()))
        .thenReturn(false);
    when(subcontractOrderQueryService.getSubcontractOutputInfo(TENANT_ID, event.getSourceId()))
        .thenReturn(
            new SubcontractOutputInfo(
                "SC-001", UUID.randomUUID(), ProductType.YARN, "KG", batchId));

    publishAndAwaitSuccess(event);

    verify(stockUnitService).createBulk(eq(batchId), any(), eq(TenantContext.SYSTEM_ACTOR_ID));
  }

  private void publishAndAwaitFailure(GoodsReceiptConfirmedEvent event) {
    Counter failureCounter = processingCounter("failure");
    double initialFailures = failureCounter.count();

    publish(event);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(failureCounter.count()).isGreaterThan(initialFailures));
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(countProcessedEvents(event.getEventId())).isZero();
              assertThat(countIncompletePublications(event.getEventId())).isEqualTo(1);
            });
  }

  private void publishAndAwaitSuccess(GoodsReceiptConfirmedEvent event) {
    Counter successCounter = processingCounter("success");
    double initialSuccesses = successCounter.count();

    publish(event);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(successCounter.count()).isGreaterThan(initialSuccesses));
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(countProcessedEvents(event.getEventId())).isEqualTo(1);
              assertThat(countIncompletePublications(event.getEventId())).isZero();
              assertThat(countCompletedPublications(event.getEventId())).isEqualTo(1);
            });
  }

  private Counter processingCounter(String outcome) {
    return meterRegistry.counter("events.processing." + outcome, "listener", PROCESSED_LISTENER_ID);
  }

  private void publish(GoodsReceiptConfirmedEvent event) {
    transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
  }

  private int countProcessedEvents(UUID eventId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM processed_event WHERE event_id = ? AND listener_id = ?",
            Integer.class,
            eventId,
            PROCESSED_LISTENER_ID);
    return count == null ? 0 : count;
  }

  private int countIncompletePublications(UUID eventId) {
    return countPublications(eventId, "completion_date IS NULL");
  }

  private int countCompletedPublications(UUID eventId) {
    return countPublications(eventId, "completion_date IS NOT NULL");
  }

  private int countPublications(UUID eventId, String completionPredicate) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM event_publication "
                + "WHERE listener_id LIKE ? AND serialized_event LIKE ? AND "
                + completionPredicate,
            Integer.class,
            "%GoodsReceiptConfirmedEventListener%",
            "%" + eventId + "%");
    return count == null ? 0 : count;
  }

  private GoodsReceiptConfirmedEvent event(
      GoodsReceiptSourceType sourceType, List<GoodsReceiptConfirmedEvent.ReceiptItemData> items) {
    return GoodsReceiptConfirmedEvent.builder()
        .tenantId(TENANT_ID)
        .receiptId(UUID.randomUUID())
        .receiptNumber("GR-" + UUID.randomUUID())
        .sourceType(sourceType)
        .sourceId(UUID.randomUUID())
        .items(items)
        .build();
  }

  private List<GoodsReceiptConfirmedEvent.ReceiptItemData> oneItem() {
    return List.of(
        new GoodsReceiptConfirmedEvent.ReceiptItemData(
            UUID.randomUUID(), "UNIT-001", BigDecimal.TEN, BigDecimal.valueOf(11)));
  }
}
