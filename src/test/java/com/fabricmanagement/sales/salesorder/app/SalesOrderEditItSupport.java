package com.fabricmanagement.sales.salesorder.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerRegistry;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRegistryRepository;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.UserDepartment;
import com.fabricmanagement.platform.user.infra.repository.RoleRepository;
import com.fabricmanagement.platform.user.infra.repository.UserDepartmentRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.product.core.app.ProductEvidenceQueryService;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.ProductSalesDefinitionDto;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTermStatus;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerms;
import com.fabricmanagement.sales.salesorder.domain.IncotermsVersion;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditBase;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditResult;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import com.fabricmanagement.testsupport.PostgresImage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared fixture of the safe-edit integration tests (CEDIT-02 scenarios, "Ortak başlangıç"): one
 * PostgreSQL container and one Spring context for every safe-edit IT, a fresh tenant per test with
 * actors A, B and C, and order O with lines L1 (P1, 1000 M, GBP 4.0000, tolerance 5/5) and L2 (P2,
 * 500 M, GBP 6.5000, 300 M allocated to delivery D1).
 *
 * <p>The application connects as the container's superuser, which RLS does not restrict; tenant
 * isolation of the new tables is proven separately through {@link #appConnection(UUID)}, a
 * NOBYPASSRLS {@code fabric_app} role created before Flyway so the grant migrations cover it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisabledIf(value = "dockerNotAvailable", disabledReason = "Docker is not available")
@Import(SalesOrderEditItSupport.ClockConfig.class)
public abstract class SalesOrderEditItSupport {

  protected static final String PATH = "/api/v1/sales/orders/%s/edit-operations";
  protected static final LocalDate ORDER_DATE = LocalDate.of(2026, 10, 1);

  @SuppressWarnings("resource")
  protected static final PostgreSQLContainer<?> POSTGRES =
      PostgresImage.container()
          .withDatabaseName("sales_edit_test")
          .withUsername("test")
          .withPassword("test");

  static {
    if (!dockerNotAvailable()) {
      POSTGRES.start();
    }
  }

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    // The app role exists before Flyway runs, so the grant migrations include it.
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var sql = connection.createStatement()) {
      sql.execute(
          "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN"
              + " CREATE ROLE fabric_app LOGIN NOSUPERUSER NOCREATEDB NOBYPASSRLS"
              + " PASSWORD 'app_test'; END IF; END $$");
    } catch (SQLException failure) {
      throw new IllegalStateException("Cannot create the fabric_app role", failure);
    }
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
    registry.add("spring.flyway.user", POSTGRES::getUsername);
    registry.add("spring.flyway.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    // Enough connections for two overlapping saves plus the test's own reads.
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> "12");
  }

  static boolean dockerNotAvailable() {
    return !org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
  }

  /** A clock the tests move forward; otherwise the system clock in UTC. */
  public static final class MutableClock extends Clock {
    private volatile Duration offset = Duration.ZERO;

    public void advance(Duration duration) {
      offset = offset.plus(duration);
    }

    public void reset() {
      offset = Duration.ZERO;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return Clock.offset(Clock.system(zone), offset);
    }

    @Override
    public Instant instant() {
      return Instant.now().plus(offset);
    }
  }

  @TestConfiguration
  static class ClockConfig {
    @Bean
    @Primary
    MutableClock salesOrderEditTestClock() {
      return new MutableClock();
    }
  }

  @Autowired protected SalesOrderEditService edits;
  @Autowired protected SalesOrderRepository orders;
  @Autowired protected SalesOrderLineRepository lines;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected TransactionTemplate transactions;
  @Autowired protected ObjectMapper objectMapper;
  @Autowired protected MutableClock clock;
  @Autowired protected CacheManager cacheManager;
  @Autowired private TenantRepository tenants;
  @Autowired private OrganizationRepository organizations;
  @Autowired private RoleRepository roles;
  @Autowired private DepartmentRepository departments;
  @Autowired private UserRepository users;
  @Autowired private UserDepartmentRepository userDepartments;
  @Autowired private TradingPartnerRegistryRepository registries;
  @Autowired private TradingPartnerRepository partners;

  @MockitoBean protected PermissionEvaluator permissionEvaluator;
  @MockitoBean protected ProductSalesDefinitionQueryService productDefinitions;
  @MockitoBean protected ProductEvidenceQueryService productEvidence;

  /** Write scope per user; a user missing here has no sales permission at all. */
  protected final Map<UUID, DataScope> scopes = new ConcurrentHashMap<>();

  protected UUID tenantId;
  protected Actor actorA;
  protected Actor actorB;
  protected Actor actorC;
  protected UUID partnerId;
  protected UUID orderId;
  protected UUID l1;
  protected UUID l2;
  protected UUID d1;
  protected UUID p1;
  protected UUID p2;

  /** A user of a tenant, as the security context carries it. */
  public record Actor(UUID tenantId, UUID id, List<String> departmentCodes) {
    public Authentication authentication() {
      AuthenticatedUserContext context =
          new AuthenticatedUserContext(id, "WORKER", departmentCodes, null, tenantId);
      UsernamePasswordAuthenticationToken token =
          new UsernamePasswordAuthenticationToken(context, "n/a", List.of());
      token.setDetails(context);
      return token;
    }
  }

  @BeforeEach
  void createCommonStart() {
    reset(permissionEvaluator, productDefinitions, productEvidence);
    scopes.clear();
    clock.reset();
    stubPermissions();
    stubCatalogue();

    String suffix = UUID.randomUUID().toString().substring(0, 8);
    tenantId = tenants.save(Tenant.create("Edit " + suffix, "SE-" + suffix)).getId();
    TenantContext.setCurrentTenantId(tenantId);
    Organization organization =
        organizations.save(
            Organization.create("Edit Org " + suffix, "TAX-" + suffix, OrganizationType.WEAVER));
    Role role = roles.save(Role.create("Seller " + suffix, "WORKER", "Safe edit test"));
    Department sales =
        departments.save(Department.create(organization.getId(), "Sales", "SALES", "Sales"));
    actorA = user(organization, role, sales, "Avery");
    actorB = user(organization, role, sales, "Blake");
    actorC = user(organization, role, sales, "Casey");
    List.of(actorA, actorB, actorC).forEach(actor -> scopes.put(actor.id(), DataScope.GLOBAL));

    TenantContext.setCurrentUserId(actorA.id());
    partnerId = partner("Edit customer " + suffix);
    p1 = UUID.randomUUID();
    p2 = UUID.randomUUID();
    SalesOrder order =
        SalesOrder.builder()
            .tradingPartnerId(partnerId)
            .orderNumber("SO-E-" + suffix)
            .status(OrderStatus.DRAFT)
            .orderDate(ORDER_DATE)
            .paymentTerms("30 days")
            .contactName("Jane Hill")
            .contactEmail("jane@example.com")
            .build();
    order.applyDeliveryTerms(
        DeliveryTerms.of(DeliveryTerm.FCA, "Leeds", IncotermsVersion.INCOTERMS_2020));
    order.applyDeliveryTermStatus(DeliveryTermStatus.PROPOSED, null);
    orderId = orders.saveAndFlush(order).getId();
    l1 = line(p1, "1000", "4.0000", new BigDecimal("5"));
    l2 = line(p2, "500", "6.5000", null);
    d1 = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO sales_ord.order_delivery (id, tenant_id, created_at, updated_at, is_active,"
            + " version, sales_order_id, sequence_no) VALUES (?, ?, now(), now(), true, 0, ?, 1)",
        d1,
        tenantId,
        orderId);
    jdbc.update(
        "INSERT INTO sales_ord.order_line_allocation (id, tenant_id, created_at, updated_at,"
            + " is_active, version, sales_order_id, line_id, delivery_id, quantity)"
            + " VALUES (?, ?, now(), now(), true, 0, ?, ?, ?, 300)",
        UUID.randomUUID(),
        tenantId,
        orderId,
        l2,
        d1);
    TenantContext.clear();
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
    clock.reset();
  }

  // ── fixture ───────────────────────────────────────────────────────────────

  private Actor user(Organization organization, Role role, Department department, String name) {
    User user = User.create(name, "Seller", organization.getId());
    user.setRole(role);
    user = users.save(user);
    userDepartments.save(UserDepartment.create(user, department, true, user.getId()));
    return new Actor(tenantId, user.getId(), List.of(department.getDepartmentCode()));
  }

  private UUID partner(String name) {
    TradingPartnerRegistry registry = TradingPartnerRegistry.create(null, name, "GBR");
    registry.setUid("REG-" + UUID.randomUUID());
    return partners
        .saveAndFlush(TradingPartner.create(registries.save(registry), PartnerType.CUSTOMER, name))
        .getId();
  }

  private UUID line(UUID product, String quantity, String price, BigDecimal tolerance) {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .salesOrderId(orderId)
            .productId(product)
            .requestedQty(new BigDecimal(quantity))
            .unit("M")
            .currency("GBP")
            .unitPriceAmount(new BigDecimal(price))
            .build();
    if (tolerance != null) {
      line.recordTolerance(tolerance, tolerance, actorA.id(), Instant.now());
    }
    return lines.saveAndFlush(line).getId();
  }

  private void stubPermissions() {
    when(permissionEvaluator.evaluate(any(), any(), any(), any()))
        .thenAnswer(invocation -> permissions(invocation.getArgument(3)));
    when(permissionEvaluator.evaluateFresh(any(), any(), any(), any()))
        .thenAnswer(invocation -> permissions(invocation.getArgument(3)));
  }

  private PermissionResult permissions(UUID userId) {
    DataScope scope = userId == null ? null : scopes.get(userId);
    if (scope == null) {
      return new PermissionResult(Map.of(), false);
    }
    return new PermissionResult(Map.of("sales", Map.of("read", scope, "write", scope)), false);
  }

  private void stubCatalogue() {
    when(productDefinitions.find(any(), any()))
        .thenAnswer(
            invocation -> {
              // Null while a test re-stubs this method with when(...): Mockito calls it once.
              UUID productId = invocation.getArgument(1);
              if (productId == null) {
                return Optional.empty();
              }
              return Optional.of(
                  new ProductSalesDefinitionDto(
                      productId,
                      "PRD-" + productId.toString().substring(0, 8),
                      "Test fabric",
                      ProductType.FABRIC,
                      "M",
                      true,
                      List.of(),
                      List.of()));
            });
    when(productEvidence.findReferences(any()))
        .thenAnswer(
            invocation -> {
              java.util.Collection<UUID> ids = invocation.getArgument(0);
              if (ids == null) {
                return List.of();
              }
              return ids.stream()
                  .map(
                      id ->
                          new ProductEvidenceQueryService.Reference(
                              id, ProductType.YARN, 1L, Instant.EPOCH, true))
                  .toList();
            });
  }

  /** Takes away a user's sales permission; the permission cache is emptied too. */
  protected void revoke(Actor actor) {
    scopes.remove(actor.id());
    Cache cache = cacheManager.getCache("permissions");
    if (cache != null) {
      cache.clear();
    }
  }

  // ── calls as a user ───────────────────────────────────────────────────────

  /** Runs a step in the user's tenant; a failure is returned rather than thrown. */
  protected <T> Object as(Actor actor, Supplier<T> step) {
    TenantContext.setCurrentTenantId(actor.tenantId());
    TenantContext.setCurrentUserId(actor.id());
    try {
      return step.get();
    } catch (RuntimeException failure) {
      return failure;
    } finally {
      TenantContext.clear();
    }
  }

  /** Opens the edit form for the user; fails the test if it cannot. */
  protected SalesOrderEditBase open(Actor actor) {
    Object result = as(actor, () -> edits.openBase(orderId, actor.id(), actor.authentication()));
    if (result instanceof RuntimeException failure) {
      throw failure;
    }
    return (SalesOrderEditBase) result;
  }

  /** Saves a request as the user: a result, or the failure it raised. */
  protected Object save(Actor actor, Map<String, Object> body) {
    SalesOrderEditRequest request = request(body);
    return as(
        actor,
        () ->
            edits.save(
                orderId, request, actor.id(), actor.authentication(), PATH.formatted(orderId)));
  }

  /** Saves and expects a saved result. */
  protected SalesOrderEditResult saved(Actor actor, Map<String, Object> body) {
    Object result = save(actor, body);
    if (result instanceof RuntimeException failure) {
      throw new AssertionError("Expected a saved result but got " + failure, failure);
    }
    return (SalesOrderEditResult) result;
  }

  /** The problem body of a save that conflicted; fails the test otherwise. */
  protected JsonNode conflicted(Actor actor, Map<String, Object> body) {
    Object result = save(actor, body);
    if (result instanceof SalesOrderEditConflictException conflict) {
      return conflict.body();
    }
    throw new AssertionError("Expected a conflict but got " + result);
  }

  /** The error code of a failed save; fails the test when the save did not fail. */
  protected String failureCode(Object result) {
    if (result
        instanceof
        com.fabricmanagement.common.infrastructure.web.exception.DomainException failure) {
      return failure.getErrorCode();
    }
    throw new AssertionError("Expected a domain failure but got " + result);
  }

  protected SalesOrderEditRequest request(Map<String, Object> body) {
    try {
      return objectMapper.readValue(
          objectMapper.writeValueAsString(body), SalesOrderEditRequest.class);
    } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
      throw new IllegalArgumentException(failure);
    }
  }

  // ── request builders ──────────────────────────────────────────────────────

  protected static Map<String, Object> set(Object value) {
    Map<String, Object> field = new LinkedHashMap<>();
    field.put("operation", "SET");
    field.put("value", value);
    return field;
  }

  protected static Map<String, Object> clear() {
    return Map.of("operation", "CLEAR");
  }

  /** A save body: operation id, base id and the header changes given as key/value pairs. */
  protected static Map<String, Object> body(UUID operationId, UUID baseId, Object... header) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("operationId", operationId);
    body.put("baseId", baseId);
    if (header.length > 0) {
      body.put("header", pairs(header));
    }
    return body;
  }

  protected static Map<String, Object> pairs(Object... keyValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put((String) keyValues[i], keyValues[i + 1]);
    }
    return map;
  }

  protected static Map<String, Object> withLines(
      Map<String, Object> body, List<Map<String, Object>> lineOperations) {
    body.put("lines", lineOperations);
    return body;
  }

  protected static Map<String, Object> withResolutions(
      Map<String, Object> body, List<Map<String, Object>> resolutions) {
    body.put("resolutions", resolutions);
    return body;
  }

  protected static Map<String, Object> update(UUID lineId, Object... fields) {
    Map<String, Object> operation = new LinkedHashMap<>();
    operation.put("operation", "UPDATE");
    operation.put("lineId", lineId);
    operation.put("fields", pairs(fields));
    return operation;
  }

  protected static Map<String, Object> remove(UUID lineId) {
    Map<String, Object> operation = new LinkedHashMap<>();
    operation.put("operation", "REMOVE");
    operation.put("lineId", lineId);
    return operation;
  }

  protected static Map<String, Object> add(UUID clientLineId, UUID productId, Object... fields) {
    Map<String, Object> operation = new LinkedHashMap<>();
    operation.put("operation", "ADD");
    operation.put("clientLineId", clientLineId);
    operation.put("productId", productId);
    operation.put("fields", pairs(fields));
    return operation;
  }

  protected static Map<String, Object> quantity(String requestedQty, String unit) {
    return pairs("requestedQty", new BigDecimal(requestedQty), "unit", unit);
  }

  protected static Map<String, Object> pricing(String currency, String unitPrice) {
    return pairs("currency", currency, "unitPrice", new BigDecimal(unitPrice));
  }

  protected static Map<String, Object> resolution(String key, UUID lineId, String choice) {
    Map<String, Object> resolution = new LinkedHashMap<>();
    resolution.put("key", key);
    if (lineId != null) {
      resolution.put("lineId", lineId);
    }
    resolution.put("choice", choice);
    return resolution;
  }

  // ── database reads (owner connection: sees every tenant) ──────────────────

  protected long orderVersion() {
    return jdbc.queryForObject(
        "SELECT version FROM sales_ord.sales_order WHERE id = ?", Long.class, orderId);
  }

  protected long lineVersion(UUID lineId) {
    return jdbc.queryForObject(
        "SELECT version FROM sales_ord.sales_order_line WHERE id = ?", Long.class, lineId);
  }

  protected String orderText(String column) {
    return jdbc.queryForObject(
        "SELECT " + column + " FROM sales_ord.sales_order WHERE id = ?", String.class, orderId);
  }

  protected int receipts() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales_ord.order_edit_operation WHERE sales_order_id = ?",
        Integer.class,
        orderId);
  }

  protected int receipts(String outcome) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales_ord.order_edit_operation"
            + " WHERE sales_order_id = ? AND outcome = ?",
        Integer.class,
        orderId,
        outcome);
  }

  protected int historyRows() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales_ord.order_field_change WHERE sales_order_id = ?",
        Integer.class,
        orderId);
  }

  protected List<Map<String, Object>> history() {
    return jdbc.queryForList(
        "SELECT edit_key, change_kind, line_id, old_value::text AS old_value,"
            + " new_value::text AS new_value, resolution, resolution_scope, actor_id,"
            + " order_version, operation_id"
            + " FROM sales_ord.order_field_change WHERE sales_order_id = ?"
            + " ORDER BY changed_at, edit_key, id",
        orderId);
  }

  protected int activeLines() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales_ord.sales_order_line"
            + " WHERE sales_order_id = ? AND is_active = true",
        Integer.class,
        orderId);
  }

  protected int profileVersions() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales_ord.requirement_profile_version v"
            + " JOIN sales_ord.sales_order_line l ON l.id = v.sales_order_line_id"
            + " WHERE l.sales_order_id = ?",
        Integer.class,
        orderId);
  }

  /** Moves a base's whole life into the past, as if it had been taken long ago. */
  protected void ageBase(UUID baseId, Duration age) {
    jdbc.update(
        "UPDATE sales_ord.order_edit_base SET captured_at = captured_at - ?::interval,"
            + " expires_at = expires_at - ?::interval WHERE id = ?",
        age.toSeconds() + " seconds",
        age.toSeconds() + " seconds",
        baseId);
  }

  /** Puts the order with planning, as sending it to planning does. */
  protected void sendToPlanning() {
    jdbc.update(
        "UPDATE sales_ord.sales_order SET flow_stage = 'AWAITING_PLANNING', planning_round = 1"
            + " WHERE id = ?",
        orderId);
  }

  /** A connection as the NOBYPASSRLS application role, in a tenant or in none. */
  protected static Connection appConnection(UUID tenant) throws SQLException {
    Connection connection =
        DriverManager.getConnection(POSTGRES.getJdbcUrl(), "fabric_app", "app_test");
    if (tenant != null) {
      try (var statement =
          connection.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
        statement.setString(1, tenant.toString());
        statement.execute();
      }
    }
    return connection;
  }

  // ── concurrency helpers ───────────────────────────────────────────────────

  /** Waits for a latch within a bound; a timeout fails the step. */
  protected static void await(CountDownLatch latch) {
    try {
      if (!latch.await(20, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for the other transaction");
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  /**
   * True while a backend of this database waits for a lock: the deterministic signal that a second
   * transaction is blocked behind the first.
   */
  protected boolean someoneWaitsForALock() {
    Integer waiting =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()"
                + " AND wait_event_type = 'Lock'",
            Integer.class);
    return waiting != null && waiting > 0;
  }

  /** Polls until another backend waits for a lock, within a bound. */
  protected void awaitLockWaiter() {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (!someoneWaitsForALock()) {
      if (System.nanoTime() > deadline) {
        throw new IllegalStateException("No transaction started waiting for the lock");
      }
      Thread.onSpinWait();
    }
  }

  protected static List<Object> list(Object... values) {
    return new ArrayList<>(List.of(values));
  }

  protected static Map<String, Object> map() {
    return new HashMap<>();
  }
}
