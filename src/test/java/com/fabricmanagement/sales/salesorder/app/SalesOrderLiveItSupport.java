package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.realtime.app.LiveConnectionRegistry;
import com.fabricmanagement.platform.realtime.app.LiveStreamProperties;
import com.fabricmanagement.platform.realtime.app.LiveStreamService;
import com.fabricmanagement.platform.realtime.dto.LiveCloseReason;
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
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Shared fixture of the live-channel ITs (CEDIT-05 §8): the safe-edit fixture (tenant, actors
 * A/B/C, order O with L1/L2, real PostgreSQL), served by a real HTTP server on a random port, with
 * the channel switched on and fast technical timings. Streams are read through a real socket
 * ({@link LiveSse}); writes go through the real services and commit for real.
 *
 * <p>Permissions: the fixture's evaluator answers from {@link #scopes} for fresh evaluations; a
 * test may pin a stale cached answer for one user in {@link #cachedScopes} to prove that the stream
 * uses fresh evaluation while the HTTP guard still sees the cache. Real permission rows and a
 * NOBYPASSRLS application role are proven in {@link SalesOrderLiveMultiInstanceIT}.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "application.realtime.enabled=true",
      "application.realtime.poll-interval=100ms",
      "application.realtime.heartbeat-interval=300ms",
      "application.realtime.max-connection-lifetime=30s",
      "application.realtime.send-timeout=2s",
      "application.realtime.read-timeout=1s",
      "application.realtime.retry-after=5s",
      "spring.jpa.properties.hibernate.generate_statistics=true",
      "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"
    })
@Import({
  SalesOrderEditItSupport.ClockConfig.class,
  SalesOrderLiveItSupport.SmallSocketBuffers.class
})
public abstract class SalesOrderLiveItSupport extends SalesOrderEditItSupport {

  /**
   * Small server socket buffers (test only), so that a client which stops reading fills them in
   * seconds instead of minutes: the slow-consumer regression (L18) then runs on a real socket.
   */
  @TestConfiguration
  static class SmallSocketBuffers {
    @Bean
    TomcatConnectorCustomizer liveSmallSocketBuffers() {
      return connector -> {
        connector.setProperty("socket.txBufSize", "4096");
        connector.setProperty("socket.appWriteBufSize", "4096");
      };
    }
  }

  protected static final Duration WAIT = Duration.ofSeconds(10);

  @LocalServerPort protected int port;
  @Autowired protected LiveStreamService liveStreams;
  @Autowired protected LiveStreamProperties liveProperties;
  @Autowired protected LiveConnectionRegistry liveRegistry;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private DepartmentRepository departmentRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private UserDepartmentRepository userDepartmentRepository;
  @Autowired private TradingPartnerRegistryRepository registryRepository;
  @Autowired private TradingPartnerRepository partnerRepository;

  /** Stock read by the quantity-acceptance writer; stubbed by the test that records one. */
  @MockitoBean protected ProposalStockQueryService proposalStock;

  @Value("${application.jwt.secret}")
  private String jwtSecret;

  /** A stale cached permission answer per user: empty optional = no permission in the cache. */
  protected final Map<UUID, Optional<DataScope>> cachedScopes = new ConcurrentHashMap<>();

  /** Users whose sales permission is read-only. */
  protected final Set<UUID> readOnlyUsers = ConcurrentHashMap.newKeySet();

  private final List<LiveSse> streams = new CopyOnWriteArrayList<>();
  private Settings defaults;

  @BeforeEach
  void liveStart() {
    cachedScopes.clear();
    readOnlyUsers.clear();
    defaults = Settings.of(liveProperties);
    when(permissionEvaluator.evaluateFresh(any(), any(), any(), any()))
        .thenAnswer(invocation -> permissionsOf(invocation.getArgument(3)));
    when(permissionEvaluator.evaluate(any(), any(), any(), any()))
        .thenAnswer(invocation -> cachedPermissionsOf(invocation.getArgument(3)));
  }

  @AfterEach
  void liveEnd() {
    streams.forEach(LiveSse::close);
    streams.clear();
    defaults.restore(liveProperties);
    liveStreams.closeAll(LiveCloseReason.RECONNECT_REQUIRED);
    awaitCondition(() -> liveRegistry.size() == 0 && liveRegistry.reservedCount() == 0);
  }

  // ── permissions ───────────────────────────────────────────────────────────

  private PermissionResult permissionsOf(UUID userId) {
    return result(userId, userId == null ? null : scopes.get(userId));
  }

  private PermissionResult cachedPermissionsOf(UUID userId) {
    Optional<DataScope> stale = userId == null ? null : cachedScopes.get(userId);
    return stale != null ? result(userId, stale.orElse(null)) : permissionsOf(userId);
  }

  private PermissionResult result(UUID userId, DataScope scope) {
    if (scope == null) {
      return new PermissionResult(Map.of(), false);
    }
    Map<String, DataScope> actions =
        readOnlyUsers.contains(userId)
            ? Map.of("read", scope)
            : Map.of("read", scope, "write", scope);
    return new PermissionResult(Map.of("sales", actions), false);
  }

  // ── tokens ────────────────────────────────────────────────────────────────

  /** A real signed access token of the actor, valid for ten minutes. */
  protected String token(Actor actor) {
    return token(actor, Instant.now().plus(Duration.ofMinutes(10)), claims -> {});
  }

  /** A real signed token; the editor may add, change or remove claims. */
  protected String token(Actor actor, Instant expiresAt, Consumer<Map<String, Object>> editor) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("tenant_id", actor.tenantId().toString());
    claims.put("user_id", actor.id().toString());
    claims.put("role_code", "WORKER");
    claims.put("department_codes", actor.departmentCodes());
    editor.accept(claims);
    return Jwts.builder()
        .subject("live-" + actor.id() + "@example.test")
        .claims(claims)
        .issuedAt(Date.from(Instant.now().minusSeconds(5)))
        .expiration(Date.from(expiresAt))
        .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
        .compact();
  }

  protected static Map<String, String> bearer(String token) {
    return Map.of("Authorization", "Bearer " + token);
  }

  // ── streams ───────────────────────────────────────────────────────────────

  protected static URI liveUri(int serverPort, UUID order) {
    return URI.create(
        "http://localhost:" + serverPort + "/api/v1/sales/orders/" + order + "/live-events");
  }

  protected LiveSse subscribe(Actor actor) {
    return subscribe(port, orderId, bearer(token(actor)));
  }

  protected LiveSse subscribe(int serverPort, UUID order, Map<String, String> headers) {
    LiveSse stream = LiveSse.open(liveUri(serverPort, order), headers);
    streams.add(stream);
    return stream;
  }

  /** The stream opened and its first frame is ready; returns that frame. */
  protected static LiveSse.Frame ready(LiveSse stream) {
    assertThat(stream.status()).as(String.valueOf(stream.errorBody())).isEqualTo(200);
    LiveSse.Frame first = stream.next(WAIT);
    assertThat(first.event()).as("the first frame").isEqualTo("ready");
    assertThat(first.envelope().path("success").asBoolean()).isTrue();
    return first;
  }

  /** The next data frame says the committed version changed to this one. */
  protected static void expectInvalidated(LiveSse stream, long version) {
    LiveSse.Frame frame = stream.nextEvent(WAIT);
    assertThat(frame.event()).isEqualTo("invalidated");
    assertThat(frame.revision()).isEqualTo(Long.toString(version));
  }

  /** Two keepalives pass without any data frame: nothing committed changed the revision. */
  protected static void expectQuiet(LiveSse stream) {
    assertThat(stream.eventsUntilHeartbeats(2, WAIT)).isEmpty();
  }

  /** The stream ends with a closed frame of this reason and nothing after it. */
  protected static void expectClosed(LiveSse stream, LiveCloseReason reason) {
    LiveSse.Frame frame = stream.nextEvent(WAIT);
    assertThat(frame.event()).isEqualTo("closed");
    assertThat(frame.reason()).isEqualTo(reason.name());
    assertThat(stream.awaitEnd(WAIT)).isEmpty();
  }

  protected static void awaitCondition(BooleanSupplier condition) {
    awaitCondition(Duration.ofSeconds(15), condition);
  }

  protected static void awaitCondition(Duration bound, BooleanSupplier condition) {
    long deadline = System.nanoTime() + bound.toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("Condition not reached in time");
      }
      try {
        Thread.sleep(20);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError(interrupted);
      }
    }
  }

  /** A connection as the container's owner role, outside the application. */
  protected static Connection ownerConnection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  // ── another tenant ────────────────────────────────────────────────────────

  /** A second tenant with its own seller, sales department and draft order. */
  protected record OtherTenant(UUID tenantId, Actor actor, UUID orderId) {}

  protected OtherTenant otherTenantWithOrder() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    UUID tenant = tenantRepository.save(Tenant.create("Live " + suffix, "LV-" + suffix)).getId();
    TenantContext.setCurrentTenantId(tenant);
    try {
      Organization organization =
          organizationRepository.save(
              Organization.create("Live Org " + suffix, "TAXL-" + suffix, OrganizationType.WEAVER));
      Role role = roleRepository.save(Role.create("Seller " + suffix, "WORKER", "Live test"));
      Department sales =
          departmentRepository.save(
              Department.create(organization.getId(), "Sales", "SALES", "Sales"));
      User user = User.create("Morgan", "Seller", organization.getId());
      user.setRole(role);
      user = userRepository.save(user);
      userDepartmentRepository.save(UserDepartment.create(user, sales, true, user.getId()));
      TenantContext.setCurrentUserId(user.getId());
      TradingPartnerRegistry registry =
          TradingPartnerRegistry.create(null, "Live customer " + suffix, "GBR");
      registry.setUid("REG-" + UUID.randomUUID());
      UUID partner =
          partnerRepository
              .saveAndFlush(
                  TradingPartner.create(
                      registryRepository.save(registry),
                      PartnerType.CUSTOMER,
                      "Live customer " + suffix))
              .getId();
      UUID order =
          orders
              .saveAndFlush(
                  SalesOrder.builder()
                      .tradingPartnerId(partner)
                      .orderNumber("SO-L-" + suffix)
                      .status(OrderStatus.DRAFT)
                      .orderDate(ORDER_DATE)
                      .build())
              .getId();
      scopes.put(user.getId(), DataScope.GLOBAL);
      return new OtherTenant(tenant, new Actor(tenant, user.getId(), List.of("SALES")), order);
    } finally {
      TenantContext.clear();
    }
  }

  protected long orderVersion(UUID order) {
    return jdbc.queryForObject(
        "SELECT version FROM sales_ord.sales_order WHERE id = ?", Long.class, order);
  }

  /** The channel settings a test may change at runtime, restored after each test. */
  private record Settings(
      boolean enabled,
      Duration pollInterval,
      Duration heartbeatInterval,
      Duration lifetime,
      Duration sendTimeout,
      Duration retryAfter,
      int perInstance,
      int perTenant,
      int perUser) {

    static Settings of(LiveStreamProperties properties) {
      return new Settings(
          properties.isEnabled(),
          properties.getPollInterval(),
          properties.getHeartbeatInterval(),
          properties.getMaxConnectionLifetime(),
          properties.getSendTimeout(),
          properties.getRetryAfter(),
          properties.getMaxConnectionsPerInstance(),
          properties.getMaxConnectionsPerTenantPerInstance(),
          properties.getMaxConnectionsPerUserPerTenantPerInstance());
    }

    void restore(LiveStreamProperties properties) {
      properties.setEnabled(enabled);
      properties.setPollInterval(pollInterval);
      properties.setHeartbeatInterval(heartbeatInterval);
      properties.setMaxConnectionLifetime(lifetime);
      properties.setSendTimeout(sendTimeout);
      properties.setRetryAfter(retryAfter);
      properties.setMaxConnectionsPerInstance(perInstance);
      properties.setMaxConnectionsPerTenantPerInstance(perTenant);
      properties.setMaxConnectionsPerUserPerTenantPerInstance(perUser);
    }
  }
}
