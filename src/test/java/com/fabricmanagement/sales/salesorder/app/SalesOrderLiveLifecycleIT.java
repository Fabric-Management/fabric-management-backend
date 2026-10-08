package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.platform.realtime.dto.LiveCloseReason;
import com.fabricmanagement.sales.salesorder.domain.RequirementProfileVersion;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditOutcome;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * CEDIT-05 L11, L12, L15–L17, L19, L22: how streams end, reconnect and stay within their bounds on
 * a real server, real sockets and real PostgreSQL. A blocked database is a table lock held by
 * another connection; the read times out after the configured read timeout.
 */
class SalesOrderLiveLifecycleIT extends SalesOrderLiveItSupport {

  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private MeterRegistry meterRegistry;

  @Autowired
  @Qualifier("dataSource")
  private DataSource dataSource;

  // ── L11: database trouble ────────────────────────────────────────────────

  @Test
  @DisplayName("L11: a poll that cannot read closes TEMPORARILY_UNAVAILABLE; no healthy keepalive")
  void blockedPollCloses() throws Exception {
    LiveSse stream = subscribe(actorB);
    ready(stream);

    try (Connection locker = lockOrders()) {
      List<LiveSse.Frame> rest = stream.awaitEnd(WAIT);
      assertThat(rest).isNotEmpty();
      LiveSse.Frame last = rest.getLast();
      assertThat(last.event()).isEqualTo("closed");
      assertThat(last.reason()).isEqualTo(LiveCloseReason.TEMPORARILY_UNAVAILABLE.name());
      // At most a keepalive whose check finished before the lock; never data, never after.
      assertThat(rest.subList(0, rest.size() - 1)).allMatch(LiveSse.Frame::isHeartbeat);
      locker.rollback();
    }
    awaitCondition(() -> liveRegistry.reservedCount() == 0);
  }

  @Test
  @DisplayName("L11: an opening that cannot read is 503 with Retry-After; the slot is returned")
  void blockedOpeningIs503() throws Exception {
    try (Connection locker = lockOrders()) {
      LiveSse refused = subscribe(actorB);
      assertThat(refused.status()).isEqualTo(503);
      assertThat(refused.headers().firstValue("Retry-After")).contains("5");
      assertThat(refused.errorBody()).contains("LIVE_STREAM_UNAVAILABLE");
      assertThat(liveRegistry.reservedCount()).isZero();
      locker.rollback();
    }
    ready(subscribe(actorB));
  }

  // ── L12: token and lifetime ──────────────────────────────────────────────

  @Test
  @DisplayName("L12: after the token's expiry the stream closes AUTH_EXPIRED without a data frame")
  void tokenExpiryCloses() {
    LiveSse stream = subscribe(actorB);
    ready(stream);

    clock.advance(Duration.ofMinutes(11));

    expectClosed(stream, LiveCloseReason.AUTH_EXPIRED);
    assertThat(stream.received()).noneMatch(frame -> "invalidated".equals(frame.event()));
    awaitCondition(() -> liveRegistry.reservedCount() == 0);
  }

  @Test
  @DisplayName("L12: the maximum lifetime closes RECONNECT_REQUIRED")
  void lifetimeCloses() {
    LiveSse stream =
        subscribe(
            port,
            orderId,
            bearer(token(actorB, Instant.now().plus(Duration.ofHours(1)), claims -> {})));
    ready(stream);

    clock.advance(Duration.ofSeconds(31));

    expectClosed(stream, LiveCloseReason.RECONNECT_REQUIRED);
  }

  // ── L15–L16: reconnect and cleanup ───────────────────────────────────────

  @Test
  @DisplayName(
      "L15: a reconnect gets a new id and ready at the current version; nothing is replayed")
  void reconnectStartsFromTheCurrentState() {
    LiveSse first = subscribe(actorB);
    String firstId = ready(first).body().path("connectionId").asText();
    first.close();
    awaitCondition(() -> liveRegistry.size() == 0);

    saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "notes", set("While away")));
    long current = orderVersion();

    LiveSse again =
        subscribe(
            port,
            orderId,
            Map.of("Authorization", "Bearer " + token(actorB), "Last-Event-ID", firstId));
    LiveSse.Frame ready = ready(again);
    assertThat(ready.body().path("connectionId").asText()).isNotEqualTo(firstId);
    assertThat(ready.revision()).isEqualTo(Long.toString(current));
    expectQuiet(again);
  }

  @Test
  @DisplayName(
      "L16: a client that disconnects is cleaned up; shutdown-style close ends every stream")
  void disconnectAndCloseAllCleanUp() {
    LiveSse leaving = subscribe(actorB);
    ready(leaving);
    leaving.close();
    awaitCondition(() -> liveRegistry.size() == 0 && liveRegistry.reservedCount() == 0);

    LiveSse first = subscribe(actorB);
    LiveSse second = subscribe(actorC);
    ready(first);
    ready(second);

    liveStreams.closeAll(LiveCloseReason.RECONNECT_REQUIRED);

    expectClosed(first, LiveCloseReason.RECONNECT_REQUIRED);
    expectClosed(second, LiveCloseReason.RECONNECT_REQUIRED);
    awaitCondition(() -> liveRegistry.size() == 0 && liveRegistry.reservedCount() == 0);
  }

  // ── L17: capacity ────────────────────────────────────────────────────────

  @Test
  @DisplayName("L17: the user limit answers 429 with Retry-After; a closed stream frees the slot")
  void userLimitAndRelease() {
    liveProperties.setMaxConnectionsPerUserPerTenantPerInstance(2);
    LiveSse first = subscribe(actorB);
    LiveSse second = subscribe(actorB);
    ready(first);
    ready(second);

    LiveSse third = subscribe(actorB);
    assertThat(third.status()).isEqualTo(429);
    assertThat(third.headers().firstValue("Retry-After")).contains("5");
    assertThat(third.errorBody()).contains("LIVE_STREAM_CAPACITY");
    // Another user is not limited by B's tabs.
    ready(subscribe(actorC));

    first.close();
    awaitCondition(() -> liveRegistry.reservedCount() == 2);
    ready(subscribe(actorB));
  }

  @Test
  @DisplayName("L17: openings racing for the last slots never exceed the limit")
  void concurrentOpeningsStayWithinTheLimit() throws Exception {
    liveProperties.setMaxConnectionsPerUserPerTenantPerInstance(3);
    String token = token(actorB);
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<LiveSse>> attempts = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
        attempts.add(
            pool.submit(
                () -> {
                  start.await();
                  return subscribe(port, orderId, bearer(token));
                }));
      }
      start.countDown();
      int opened = 0;
      int refused = 0;
      for (Future<LiveSse> attempt : attempts) {
        LiveSse stream = attempt.get(20, TimeUnit.SECONDS);
        if (stream.status() == 200) {
          opened++;
        } else {
          assertThat(stream.status()).isEqualTo(429);
          refused++;
        }
      }
      assertThat(opened).isEqualTo(3);
      assertThat(refused).isEqualTo(5);
      assertThat(liveRegistry.reservedCount()).isEqualTo(3);
    } finally {
      pool.shutdownNow();
    }
  }

  // ── L19: switch ──────────────────────────────────────────────────────────

  @Test
  @DisplayName("L19: switching off closes FEATURE_DISABLED, refuses 503; editing still works")
  void switchingOff() {
    LiveSse stream = subscribe(actorB);
    ready(stream);

    liveProperties.setEnabled(false);

    expectClosed(stream, LiveCloseReason.FEATURE_DISABLED);
    awaitCondition(() -> liveRegistry.reservedCount() == 0);
    LiveSse refused = subscribe(actorB);
    assertThat(refused.status()).isEqualTo(503);
    assertThat(refused.headers().firstValue("Retry-After")).contains("5");
    assertThat(refused.errorBody()).contains("LIVE_STREAM_DISABLED");
    assertThat(
            saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "notes", set("Off")))
                .outcome())
        .isEqualTo(SalesOrderEditOutcome.APPLIED);
  }

  // ── L22: resources ───────────────────────────────────────────────────────

  @Test
  @DisplayName(
      "L22: checks read neither the order entity nor its lines or profiles, use a bounded number"
          + " of statements, and an idle stream holds no connection or transaction")
  void checksAreCheapAndHoldNothing() throws Exception {
    LiveSse stream = subscribe(actorB);
    ready(stream);
    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    long ordersLoaded = statistics.getEntityStatistics(SalesOrder.class.getName()).getLoadCount();
    long linesLoaded =
        statistics.getEntityStatistics(SalesOrderLine.class.getName()).getLoadCount();
    long profilesLoaded =
        statistics.getEntityStatistics(RequirementProfileVersion.class.getName()).getLoadCount();
    long statements = statistics.getPrepareStatementCount();
    long checks = okChecks();

    stream.eventsUntilHeartbeats(4, WAIT);

    long checked = okChecks() - checks;
    assertThat(checked).isGreaterThanOrEqualTo(4);
    assertThat(statistics.getEntityStatistics(SalesOrder.class.getName()).getLoadCount())
        .isEqualTo(ordersLoaded);
    assertThat(statistics.getEntityStatistics(SalesOrderLine.class.getName()).getLoadCount())
        .isEqualTo(linesLoaded);
    assertThat(
            statistics
                .getEntityStatistics(RequirementProfileVersion.class.getName())
                .getLoadCount())
        .isEqualTo(profilesLoaded);
    // User, permission identity, fresh permissions and the order projection: a fixed handful.
    assertThat(statistics.getPrepareStatementCount() - statements)
        .isLessThanOrEqualTo(checked * 10);

    // Park the poll: once the check in flight is done the stream is idle on the socket only.
    liveProperties.setPollInterval(Duration.ofHours(1));
    Thread.sleep(500);
    HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
    awaitCondition(() -> pool.getHikariPoolMXBean().getActiveConnections() == 0);
    assertThat(liveRegistry.size()).isEqualTo(1);
    Integer idleInTransaction =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()"
                + " AND state LIKE 'idle in transaction%' AND pid <> pg_backend_pid()",
            Integer.class);
    assertThat(idleInTransaction).isZero();
  }

  private long okChecks() {
    return Math.round(
        meterRegistry.find("live.stream.checks").tag("outcome", "ok").timers().stream()
            .mapToDouble(Timer::count)
            .sum());
  }

  /** Another connection holds an exclusive lock on the order table until it rolls back. */
  private Connection lockOrders() throws Exception {
    Connection locker = ownerConnection();
    locker.setAutoCommit(false);
    try (Statement lock = locker.createStatement()) {
      lock.execute("LOCK TABLE sales_ord.sales_order IN ACCESS EXCLUSIVE MODE");
    }
    return locker;
  }
}
