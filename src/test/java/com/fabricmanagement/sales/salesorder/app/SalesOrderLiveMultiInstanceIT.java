package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.FabricManagementApplication;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.CurrentTenantAccessPort;
import com.fabricmanagement.platform.realtime.app.LiveStreamService;
import com.fabricmanagement.platform.realtime.app.LiveTenantScope;
import com.fabricmanagement.platform.realtime.domain.LiveActor;
import com.fabricmanagement.platform.realtime.dto.LiveCloseReason;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import com.fabricmanagement.platform.user.infra.repository.PermissionTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * CEDIT-05 L13, L14, L15, the real-permission half of L10, the tenant-access regressions of review
 * R2 and CEDIT-06's edit-session presence (review R4), across two backend instances.
 *
 * <p>Instance A is the test's own context (port {@link #port}). Instance B is a second, independent
 * application context started here, with its own HTTP port, registry, scheduler and worker (one
 * worker, so every B stream is checked on the same thread), on the same PostgreSQL. B connects as
 * {@code fabric_app} (NOSUPERUSER, NOBYPASSRLS): every live read on B goes through RLS, and B's
 * permission evaluator is the real one, reading the permission rows written below. Nothing links
 * the two instances but the database: no shared registry, no event bus, no sticky session.
 */
class SalesOrderLiveMultiInstanceIT extends SalesOrderLiveItSupport {

  private static final HttpClient HTTP =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

  private static ConfigurableApplicationContext instanceB;
  private static int portB;

  @Autowired private PermissionTemplateRepository permissionTemplates;

  @BeforeEach
  void startInstanceB() throws Exception {
    if (instanceB == null) {
      instanceB =
          new SpringApplicationBuilder(FabricManagementApplication.class)
              // A plain application, as deployed: test configurations are not scanned into it.
              .initializers(
                  context ->
                      context
                          .getBeanFactory()
                          .registerSingleton(
                              "liveTestClassesExcludeFilter", new TestClassesExcludeFilter()))
              .run(
                  "--spring.profiles.active=test",
                  "--server.port=0",
                  "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                  "--spring.datasource.username=fabric_app",
                  "--spring.datasource.password=app_test",
                  "--spring.datasource.driver-class-name=org.postgresql.Driver",
                  "--spring.datasource.hikari.maximum-pool-size=4",
                  "--spring.flyway.enabled=false",
                  "--spring.flyway.url=" + POSTGRES.getJdbcUrl(),
                  "--spring.flyway.user=" + POSTGRES.getUsername(),
                  "--spring.flyway.password=" + POSTGRES.getPassword(),
                  "--application.system-datasource.username=" + POSTGRES.getUsername(),
                  "--application.system-datasource.password=" + POSTGRES.getPassword(),
                  "--application.realtime.enabled=true",
                  "--application.realtime.poll-interval=100ms",
                  "--application.realtime.heartbeat-interval=300ms",
                  "--application.realtime.read-timeout=1s",
                  "--application.realtime.worker-count=1",
                  "--spring.modulith.events.republish-outstanding-events-on-restart=false",
                  "--modulith.events.stuck-monitor.enabled=false");
      portB = ((WebServerApplicationContext) instanceB).getWebServer().getPort();
      // B's start-up backfill copied the template tenant's permission rows into every existing
      // tenant, this test's own included. Only the rows written below may decide B's answers.
      try (Connection owner = ownerConnection();
          PreparedStatement clear =
              owner.prepareStatement(
                  "DELETE FROM common_user.permission_template WHERE tenant_id = ?")) {
        clear.setObject(1, tenantId);
        clear.executeUpdate();
      }
    }
    grantSalesRead(tenantId, DataScope.GLOBAL);
    // Streams of earlier tests may still be registered on B until B notices their client left.
    LiveStreamService streamsOnB = instanceB.getBean(LiveStreamService.class);
    streamsOnB.closeAll(LiveCloseReason.RECONNECT_REQUIRED);
    awaitCondition(() -> streamsOnB.activeConnections() == 0);
  }

  @AfterAll
  static void stopInstanceB() {
    if (instanceB != null) {
      instanceB.close();
      instanceB = null;
    }
  }

  @Test
  @DisplayName("L14: a save through instance A reaches a stream held by instance B")
  void saveOnAReachesStreamOnB() throws Exception {
    LiveSse onB = subscribe(portB, orderId, bearer(token(actorB)));
    assertThat(ready(onB).revision()).isEqualTo(Long.toString(orderVersion()));
    assertThat(instanceB.getBean(LiveStreamService.class).activeConnections()).isEqualTo(1);
    assertThat(liveStreams.activeConnections()).isZero();

    saveThroughHttp(port, actorA, "Saved on A");

    expectInvalidated(onB, orderVersion());
  }

  @Test
  @DisplayName("L15: a stream lost on B reconnects to A from the current state, with a new id")
  void reconnectToTheOtherInstance() throws Exception {
    LiveSse onB = subscribe(portB, orderId, bearer(token(actorB)));
    String lostId = ready(onB).body().path("connectionId").asText();
    onB.close();

    saveThroughHttp(port, actorA, "While reconnecting");
    long current = orderVersion();

    LiveSse onA =
        subscribe(
            port,
            orderId,
            Map.of("Authorization", "Bearer " + token(actorB), "Last-Event-ID", lostId));
    LiveSse.Frame ready = ready(onA);
    assertThat(ready.body().path("connectionId").asText()).isNotEqualTo(lostId);
    assertThat(ready.revision()).isEqualTo(Long.toString(current));
  }

  @Test
  @DisplayName(
      "L10 (real rows): narrowing the permission row ends B's stream; B's cache still allows the"
          + " guard, the fresh check refuses the object")
  void realPermissionChangeRevokesOnB() {
    LiveSse onB = subscribe(portB, orderId, bearer(token(actorB)));
    ready(onB);

    jdbc.update(
        "UPDATE common_user.permission_template SET data_scope = 'OWN'"
            + " WHERE tenant_id = ? AND role_code = 'WORKER' AND resource = 'sales'"
            + " AND action = 'read'",
        tenantId);

    expectClosed(onB, LiveCloseReason.ACCESS_REVOKED);
    // The guard reads B's cache (still GLOBAL); the stream's own fresh check says not found.
    LiveSse again = subscribe(portB, orderId, bearer(token(actorB)));
    assertThat(again.status()).isEqualTo(404);
  }

  @Test
  @DisplayName(
      "L13: on B's single worker two tenants' streams each see only their own order, through RLS"
          + " with the application role")
  void twoTenantsOnOneWorkerThroughRls() throws Exception {
    OtherTenant other = otherTenantWithOrder();
    grantSalesRead(other.tenantId(), DataScope.GLOBAL);

    LiveSse first = subscribe(portB, orderId, bearer(token(actorB)));
    LiveSse second = subscribe(portB, other.orderId(), bearer(token(other.actor())));
    ready(first);
    ready(second);
    // The other tenant's user cannot reach this tenant's order on B either.
    assertThat(subscribe(portB, orderId, bearer(token(other.actor()))).status()).isEqualTo(404);

    try (Connection owner = ownerConnection();
        PreparedStatement bump =
            owner.prepareStatement(
                "UPDATE sales_ord.sales_order SET version = version + 1 WHERE id = ?")) {
      bump.setObject(1, other.orderId());
      bump.executeUpdate();
    }
    expectInvalidated(second, orderVersion(other.orderId()));
    expectQuiet(first);

    saveThroughHttp(port, actorA, "Tenant one only");
    expectInvalidated(first, orderVersion());
    expectQuiet(second);
  }

  @Test
  @DisplayName(
      "L13: B's tenant scope binds RLS for the application role; a failed read leaves nothing for"
          + " the next tenant's read on the same thread")
  void tenantScopeOnTheApplicationRole() throws Exception {
    OtherTenant other = otherTenantWithOrder();
    LiveTenantScope scope = instanceB.getBean(LiveTenantScope.class);
    JdbcTemplate appJdbc = new JdbcTemplate(instanceB.getBean("dataSource", DataSource.class));
    LiveActor first = new LiveActor(tenantId, actorB.id(), Instant.now().plusSeconds(600));
    LiveActor second =
        new LiveActor(other.tenantId(), other.actor().id(), Instant.now().plusSeconds(600));
    ExecutorService thread = Executors.newSingleThreadExecutor();
    try {
      Map<String, Object> role =
          onThread(
              thread,
              () ->
                  scope.read(
                      first,
                      () ->
                          appJdbc.queryForMap(
                              "SELECT current_user AS usr, r.rolsuper AS sup,"
                                  + " r.rolbypassrls AS bypass FROM pg_roles r"
                                  + " WHERE r.rolname = current_user")));
      assertThat(role.get("usr")).isEqualTo("fabric_app");
      assertThat(role.get("sup")).isEqualTo(false);
      assertThat(role.get("bypass")).isEqualTo(false);

      assertThat(onThread(thread, () -> scope.read(first, () -> visibleOrders(appJdbc))))
          .isEqualTo(Map.of("tenant", tenantId.toString(), "orders", 1L));

      assertThatThrownBy(
              () ->
                  onThread(
                      thread,
                      () ->
                          scope.read(
                              second, () -> appJdbc.queryForObject("SELECT 1 / 0", Integer.class))))
          .hasRootCauseMessage("ERROR: division by zero");

      assertThat(onThread(thread, () -> scope.read(second, () -> visibleOrders(appJdbc))))
          .isEqualTo(Map.of("tenant", other.tenantId().toString(), "orders", 1L));
      assertThat(onThread(thread, TenantContext::getCurrentTenantIdOrNull)).isNull();
    } finally {
      thread.shutdownNow();
    }
  }

  @Test
  @DisplayName(
      "R2: suspending the tenant ends B's stream; the same still-valid token cannot open a new one")
  void suspendedTenantIsRevokedOnB() throws Exception {
    LiveSse onB = subscribe(portB, orderId, bearer(token(actorB)));
    ready(onB);

    updateOwnTenant("status = 'SUSPENDED'");

    expectClosed(onB, LiveCloseReason.ACCESS_REVOKED);
    assertThat(subscribe(portB, orderId, bearer(token(actorB))).status()).isIn(401, 403);
  }

  @Test
  @DisplayName(
      "R2: an EXPIRED (read-only) or ACTIVE tenant keeps streaming on B; deactivating it ends the"
          + " stream and refuses a new one")
  void tenantStatusesOnB() throws Exception {
    updateOwnTenant("status = 'EXPIRED'");
    LiveSse expired = subscribe(portB, orderId, bearer(token(actorB)));
    ready(expired);
    expectQuiet(expired);

    updateOwnTenant("status = 'ACTIVE'");
    ready(subscribe(portB, orderId, bearer(token(actorB))));
    expectQuiet(expired);

    updateOwnTenant("is_active = false");
    expectClosed(expired, LiveCloseReason.ACCESS_REVOKED);
    assertThat(subscribe(portB, orderId, bearer(token(actorB))).status()).isIn(401, 403);
  }

  @Test
  @DisplayName(
      "R2: B's tenant check sees only the bound tenant's own row through RLS, and nothing without"
          + " a bound tenant")
  void tenantAccessReadsOnlyTheOwnRow() throws Exception {
    OtherTenant other = otherTenantWithOrder();
    LiveTenantScope scope = instanceB.getBean(LiveTenantScope.class);
    CurrentTenantAccessPort access = instanceB.getBean(CurrentTenantAccessPort.class);
    JdbcTemplate appJdbc = new JdbcTemplate(instanceB.getBean("dataSource", DataSource.class));
    LiveActor actor = new LiveActor(tenantId, actorB.id(), Instant.now().plusSeconds(600));
    String byId = "SELECT count(*) FROM common_tenant.common_tenant WHERE id = ?";
    ExecutorService thread = Executors.newSingleThreadExecutor();
    try {
      Map<String, Object> seen =
          onThread(
              thread,
              () ->
                  scope.read(
                      actor,
                      () ->
                          Map.<String, Object>of(
                              "rows",
                              appJdbc.queryForObject(
                                  "SELECT count(*) FROM common_tenant.common_tenant", Long.class),
                              "own",
                              appJdbc.queryForObject(byId, Long.class, tenantId),
                              "other",
                              appJdbc.queryForObject(byId, Long.class, other.tenantId()),
                              "access",
                              access.currentTenantHasAccess())));
      assertThat(seen)
          .isEqualTo(Map.of("rows", 1L, "own", 1L, "other", 0L, "access", Boolean.TRUE));

      assertThat(onThread(thread, access::currentTenantHasAccess)).isFalse();
      assertThat(onThread(thread, TenantContext::getCurrentTenantIdOrNull)).isNull();
    } finally {
      thread.shutdownNow();
    }
  }

  @Test
  @DisplayName(
      "CEDIT-06 (CE-18): edit sessions on either instance change the presence of B's stream; B"
          + " lists them through RLS with real permission rows and hides others' session ids")
  void presenceAcrossInstances() throws Exception {
    grantSales(tenantId, "write", DataScope.GLOBAL);
    LiveSse onB = subscribe(portB, orderId, bearer(token(actorB)));
    LiveSse.Frame first = ready(onB);
    String version = first.revision();
    assertThat(presence(first)).isEqualTo("0");

    // Opened on A, seen on B.
    JsonNode opened = sessions(port, "POST", "", actorA, 201);
    String onA = opened.path("data").path("editSessionId").asText();
    LiveSse.Frame seen = onB.nextEvent(WAIT);
    assertThat(seen.event()).isEqualTo("invalidated");
    assertThat(seen.revision()).isEqualTo(version);
    String present = presence(seen);
    assertThat(present).isNotEqualTo("0");

    // B lists it with its real permission evaluator; another person's session id stays hidden.
    JsonNode editors = sessions(portB, "GET", "", actorB, 200).path("data").path("editors");
    assertThat(editors).hasSize(1);
    assertThat(editors.get(0).path("userId").asText()).isEqualTo(actorA.id().toString());
    assertThat(editors.get(0).path("mine").asBoolean()).isFalse();
    assertThat(editors.get(0).path("editSessionId").isNull()).isTrue();

    // Renewed on B: nobody's presence changed. Closed on B: gone.
    sessions(portB, "PUT", "/" + onA, actorA, 200);
    expectQuiet(onB);
    sessions(portB, "DELETE", "/" + onA, actorA, 204);
    assertThat(presence(onB.nextEvent(WAIT))).isEqualTo("0");

    // Opened on B, then expired without any write: B notices.
    String onBSession =
        sessions(portB, "POST", "", actorA, 201).path("data").path("editSessionId").asText();
    assertThat(presence(onB.nextEvent(WAIT))).isNotEqualTo("0");
    try (Connection owner = ownerConnection();
        PreparedStatement expire =
            owner.prepareStatement(
                "UPDATE common_infrastructure.live_edit_session SET last_seen_at = now() -"
                    + " interval '10 minutes', expires_at = now() - interval '5 minutes'"
                    + " WHERE id = ?")) {
      expire.setObject(1, UUID.fromString(onBSession));
      assertThat(expire.executeUpdate()).isEqualTo(1);
    }
    assertThat(presence(onB.nextEvent(WAIT))).isEqualTo("0");

    // A reconnect to B starts from the current presence.
    onB.close();
    String reopened =
        sessions(port, "POST", "", actorA, 201).path("data").path("editSessionId").asText();
    assertThat(presence(ready(subscribe(portB, orderId, bearer(token(actorB)))))).isNotEqualTo("0");

    // Another tenant reaches neither the list nor the rows, even on the application role.
    OtherTenant other = otherTenantWithOrder();
    grantSales(other.tenantId(), "read", DataScope.GLOBAL);
    sessions(portB, "GET", "", other.actor(), 404);
    LiveTenantScope scope = instanceB.getBean(LiveTenantScope.class);
    JdbcTemplate appJdbc = new JdbcTemplate(instanceB.getBean("dataSource", DataSource.class));
    String count =
        "SELECT count(*) FROM common_infrastructure.live_edit_session WHERE id = ?::uuid";
    ExecutorService thread = Executors.newSingleThreadExecutor();
    try {
      LiveActor own = new LiveActor(tenantId, actorB.id(), Instant.now().plusSeconds(600));
      LiveActor foreign =
          new LiveActor(other.tenantId(), other.actor().id(), Instant.now().plusSeconds(600));
      assertThat(
              onThread(
                  thread,
                  () -> scope.read(own, () -> appJdbc.queryForObject(count, Long.class, reopened))))
          .isEqualTo(1L);
      assertThat(
              onThread(
                  thread,
                  () ->
                      scope.read(
                          foreign, () -> appJdbc.queryForObject(count, Long.class, reopened))))
          .isZero();
    } finally {
      thread.shutdownNow();
    }
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  /** Keeps classes compiled from the test sources out of instance B's component scan. */
  static final class TestClassesExcludeFilter extends TypeExcludeFilter {

    @Override
    public boolean match(MetadataReader reader, MetadataReaderFactory factory) throws IOException {
      return reader.getResource().getURL().toString().contains("/test-classes/");
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof TestClassesExcludeFilter;
    }

    @Override
    public int hashCode() {
      return TestClassesExcludeFilter.class.hashCode();
    }
  }

  private static Map<String, Object> visibleOrders(JdbcTemplate appJdbc) {
    String tenant =
        appJdbc.queryForObject("SELECT current_setting('app.current_tenant', true)", String.class);
    Long orders = appJdbc.queryForObject("SELECT count(*) FROM sales_ord.sales_order", Long.class);
    return Map.of("tenant", tenant, "orders", orders);
  }

  private static <T> T onThread(ExecutorService thread, java.util.concurrent.Callable<T> step)
      throws Exception {
    try {
      return thread.submit(step).get(20, TimeUnit.SECONDS);
    } catch (java.util.concurrent.ExecutionException failure) {
      if (failure.getCause() instanceof Exception cause) {
        throw cause;
      }
      throw failure;
    }
  }

  /** Changes this test's tenant row as the owner role, outside both applications. */
  private void updateOwnTenant(String assignment) throws Exception {
    try (Connection owner = ownerConnection();
        PreparedStatement update =
            owner.prepareStatement(
                "UPDATE common_tenant.common_tenant SET " + assignment + " WHERE id = ?")) {
      update.setObject(1, tenantId);
      assertThat(update.executeUpdate()).isEqualTo(1);
    }
  }

  /** A real permission row: the tenant's WORKER role may read sales with this scope. */
  private void grantSalesRead(UUID tenant, DataScope scope) {
    grantSales(tenant, "read", scope);
  }

  /** A real permission row: the tenant's WORKER role may do this sales action with this scope. */
  private void grantSales(UUID tenant, String action, DataScope scope) {
    TenantContext.setCurrentTenantId(tenant);
    try {
      permissionTemplates.saveAndFlush(
          PermissionTemplate.builder()
              .roleCode("WORKER")
              .resource("sales")
              .action(action)
              .dataScope(scope)
              .build());
    } finally {
      TenantContext.clear();
    }
  }

  private static String presence(LiveSse.Frame frame) {
    return frame.body().path("presenceRevision").asText();
  }

  /** One edit-session call through the HTTP API of the given instance; checks the status. */
  private JsonNode sessions(int serverPort, String method, String suffix, Actor actor, int status)
      throws Exception {
    URI uri =
        URI.create(
            "http://localhost:"
                + serverPort
                + "/api/v1/sales/orders/"
                + orderId
                + "/edit-sessions"
                + suffix);
    HttpResponse<String> response =
        HTTP.send(
            HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + token(actor))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(15))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
    String body = response.body();
    return body == null || body.isBlank() ? objectMapper.nullNode() : objectMapper.readTree(body);
  }

  /** Opens an edit base and saves the notes through the HTTP API of the given instance. */
  private void saveThroughHttp(int serverPort, Actor actor, String notes) throws Exception {
    String token = token(actor);
    String base = "http://localhost:" + serverPort + "/api/v1/sales/orders/" + orderId;
    HttpResponse<String> opened =
        HTTP.send(
            HttpRequest.newBuilder(URI.create(base + "/edit-bases"))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(15))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(opened.statusCode()).as(opened.body()).isEqualTo(201);
    UUID baseId =
        UUID.fromString(objectMapper.readTree(opened.body()).path("data").path("baseId").asText());

    String body =
        objectMapper.writeValueAsString(body(UUID.randomUUID(), baseId, "notes", set(notes)));
    HttpResponse<String> saved =
        HTTP.send(
            HttpRequest.newBuilder(URI.create(base + "/edit-operations"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(15))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
    JsonNode result = objectMapper.readTree(saved.body()).path("data");
    assertThat(result.path("outcome").asText()).isEqualTo("APPLIED");
  }
}
