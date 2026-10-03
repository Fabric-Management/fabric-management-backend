package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerRegistry;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRegistryRepository;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.dto.CreateSalesOrderRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.DeliveriesView;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.DeliveryContentInput;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.SaveDeliveryRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.OrderPartiesView;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.SetReleasePolicyRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderDto;
import com.fabricmanagement.testsupport.PostgresImage;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ADR-0014 D2/D10 on a real database: repeated and concurrent creation with one key, the order
 * version moving with section writes, stale writes rejected, and tenant isolation of the new reads.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisabledIf(value = "dockerNotAvailable", disabledReason = "Docker is not available")
class OrderIntakeCoreIT {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      PostgresImage.container()
          .withDatabaseName("order_intake_core_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  static boolean dockerNotAvailable() {
    return !org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
  }

  @Autowired private TenantRepository tenants;
  @Autowired private TradingPartnerRegistryRepository registries;
  @Autowired private TradingPartnerRepository partners;
  @Autowired private SalesOrderService orderService;
  @Autowired private OrderPartiesService parties;
  @Autowired private OrderDeliveryService deliveries;
  @Autowired private JdbcTemplate jdbc;

  /** Access scopes are covered elsewhere; here every user may read and write in its tenant. */
  @MockitoBean private SalesOrderAccessPolicy accessPolicy;

  private final UUID user = UUID.randomUUID();
  private UUID tenantId;
  private UUID customerId;

  @BeforeEach
  void setUp() {
    when(accessPolicy.canRead(any(), any(), any())).thenReturn(true);
    when(accessPolicy.canWrite(any(), any(), any())).thenReturn(true);
    when(accessPolicy.readRestriction(any(), any()))
        .thenReturn((root, query, criteriaBuilder) -> criteriaBuilder.conjunction());
    tenantId = newTenant();
    customerId = inTenant(tenantId, this::newCustomer);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void concurrentCreatesWithOneKeyMakeOneOrderAndBothGetIt() throws Exception {
    UUID key = UUID.randomUUID();
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      CompletableFuture<Object> first =
          CompletableFuture.supplyAsync(
              () -> attempt(tenantId, () -> awaitThen(start, () -> create(key, "PO-1"))), pool);
      CompletableFuture<Object> second =
          CompletableFuture.supplyAsync(
              () -> attempt(tenantId, () -> awaitThen(start, () -> create(key, "PO-1"))), pool);
      start.countDown();
      List<Object> results =
          List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));

      assertThat(results).allMatch(SalesOrderDto.class::isInstance);
      assertThat(results)
          .extracting(result -> ((SalesOrderDto) result).getId())
          .containsOnly(((SalesOrderDto) results.get(0)).getId());
    } finally {
      pool.shutdownNow();
    }
    assertThat(ordersWithKey(key)).isEqualTo(1);
  }

  @Test
  void aRepeatedCreateReturnsTheOrderItCreated() {
    UUID key = UUID.randomUUID();
    SalesOrderDto created = inTenant(tenantId, () -> create(key, "PO-1"));
    SalesOrderDto repeated = inTenant(tenantId, () -> create(key, "PO-1"));

    assertThat(repeated.getId()).isEqualTo(created.getId());
    assertThat(ordersWithKey(key)).isEqualTo(1);
  }

  @Test
  void aKeyReusedWithOtherContentIsAConflict() {
    UUID key = UUID.randomUUID();
    inTenant(tenantId, () -> create(key, "PO-1"));

    Object result = attempt(tenantId, () -> create(key, "PO-2"));

    assertThat(result)
        .isInstanceOf(OrderDomainException.class)
        .extracting(error -> ((OrderDomainException) error).getErrorCode())
        .isEqualTo("IDEMPOTENCY_KEY_REUSED");
    assertThat(ordersWithKey(key)).isEqualTo(1);
  }

  @Test
  void sectionWritesMoveTheVersionAndAStaleWriteIsRejected() {
    UUID orderId = inTenant(tenantId, () -> create(null, "PO-3")).getId();
    long initial = inTenant(tenantId, () -> parties.read(orderId, user)).orderVersion();

    OrderPartiesView afterPolicy =
        inTenant(
            tenantId,
            () ->
                parties.setReleasePolicy(
                    orderId, new SetReleasePolicyRequest(initial, true), user));
    assertThat(afterPolicy.orderVersion())
        .isGreaterThan(initial)
        .isEqualTo(versionInDatabase(orderId));

    Object stale =
        attempt(
            tenantId,
            () ->
                parties.setReleasePolicy(
                    orderId, new SetReleasePolicyRequest(initial, false), user));
    assertThat(stale).isInstanceOf(ObjectOptimisticLockingFailureException.class);

    DeliveriesView afterDelivery =
        inTenant(
            tenantId,
            () ->
                deliveries.create(
                    orderId,
                    new SaveDeliveryRequest(afterPolicy.orderVersion(), emptyDelivery()),
                    user));
    assertThat(afterDelivery.orderVersion())
        .as("a delivery change moves the order's version, so other sections see it")
        .isGreaterThan(afterPolicy.orderVersion())
        .isEqualTo(versionInDatabase(orderId));
    assertThat(afterDelivery.deliveries()).hasSize(1);

    Object staleDelivery =
        attempt(
            tenantId,
            () ->
                deliveries.create(
                    orderId,
                    new SaveDeliveryRequest(afterPolicy.orderVersion(), emptyDelivery()),
                    user));
    assertThat(staleDelivery).isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }

  @Test
  void anotherTenantNeitherReadsNorWritesTheOrder() {
    UUID orderId = inTenant(tenantId, () -> create(null, "PO-4")).getId();
    long version = inTenant(tenantId, () -> parties.read(orderId, user)).orderVersion();
    UUID otherTenant = newTenant();

    assertThat(attempt(otherTenant, () -> parties.read(orderId, user)))
        .isInstanceOf(NotFoundException.class);
    assertThat(attempt(otherTenant, () -> deliveries.list(orderId, user)))
        .isInstanceOf(NotFoundException.class);
    assertThat(
            attempt(
                otherTenant,
                () ->
                    parties.setReleasePolicy(
                        orderId, new SetReleasePolicyRequest(version, true), user)))
        .isInstanceOf(NotFoundException.class);
    assertThat(versionInDatabase(orderId)).isEqualTo(version);
  }

  // ─── helpers ───────────────────────────────────────────────────────────────

  private static DeliveryContentInput emptyDelivery() {
    return new DeliveryContentInput(null, null, null, null, null, null, null, false);
  }

  private SalesOrderDto create(UUID key, String reference) {
    CreateSalesOrderRequest request = new CreateSalesOrderRequest();
    request.setPartnerId(customerId);
    request.setOrderDate(LocalDate.of(2026, 10, 2));
    request.setCustomerReference(reference);
    request.setIdempotencyKey(key);
    return orderService.createOrder(request);
  }

  private long ordersWithKey(UUID key) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales_ord.sales_order WHERE tenant_id = ? AND creation_key = ?",
        Long.class,
        tenantId,
        key);
  }

  private long versionInDatabase(UUID orderId) {
    return jdbc.queryForObject(
        "SELECT version FROM sales_ord.sales_order WHERE tenant_id = ? AND id = ?",
        Long.class,
        tenantId,
        orderId);
  }

  private UUID newTenant() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    return tenants.save(Tenant.create("Intake " + suffix, "OI-" + suffix)).getId();
  }

  private UUID newCustomer() {
    TradingPartnerRegistry registry =
        TradingPartnerRegistry.create(null, "Intake customer " + UUID.randomUUID(), "GBR");
    registry.setUid("REG-" + UUID.randomUUID());
    return partners
        .saveAndFlush(
            TradingPartner.create(registries.save(registry), PartnerType.CUSTOMER, "Customer"))
        .getId();
  }

  private <T> T inTenant(UUID tenant, Supplier<T> action) {
    try {
      TenantContext.setCurrentTenantId(tenant);
      TenantContext.setCurrentUserId(user);
      return action.get();
    } finally {
      TenantContext.clear();
    }
  }

  private Object attempt(UUID tenant, Supplier<?> action) {
    try {
      return inTenant(tenant, action);
    } catch (Throwable error) {
      return error;
    }
  }

  private static <T> T awaitThen(CountDownLatch start, Supplier<T> action) {
    try {
      start.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    }
    return action.get();
  }
}
