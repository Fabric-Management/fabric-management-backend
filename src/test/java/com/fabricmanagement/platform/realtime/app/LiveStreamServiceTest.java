package com.fabricmanagement.platform.realtime.app;

import static com.fabricmanagement.platform.realtime.app.LiveTestDoubles.await;
import static com.fabricmanagement.platform.realtime.app.LiveTestDoubles.visible;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.realtime.app.LiveTestDoubles.FakeChannel;
import com.fabricmanagement.platform.realtime.app.LiveTestDoubles.FakeSource;
import com.fabricmanagement.platform.realtime.app.LiveTestDoubles.ManualExecutor;
import com.fabricmanagement.platform.realtime.app.LiveTestDoubles.RecordingTransactionManager;
import com.fabricmanagement.platform.realtime.app.LiveTestDoubles.TestClock;
import com.fabricmanagement.platform.realtime.domain.LiveActor;
import com.fabricmanagement.platform.realtime.domain.LiveReadResult;
import com.fabricmanagement.platform.realtime.domain.exception.LiveStreamRejectedException;
import com.fabricmanagement.platform.realtime.domain.exception.LiveStreamRejectedException.Rejection;
import com.fabricmanagement.platform.realtime.dto.LiveCloseReason;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The live channel's lifecycle with a hand-moved clock, a hand-run worker queue, a scripted source
 * and a scripted non-blocking transport (CEDIT-05 §5, §8, R1, R2): no sleeps decide an outcome;
 * waits exist only where a real thread must reach a point, and they are bounded.
 */
class LiveStreamServiceTest {

  private static final Instant START = Instant.parse("2026-10-07T09:00:00Z");

  private final UUID tenantA = UUID.randomUUID();
  private final UUID tenantB = UUID.randomUUID();
  private final UUID userA = UUID.randomUUID();
  private final UUID userB = UUID.randomUUID();
  private final UUID resource = UUID.randomUUID();
  private final AtomicBoolean tenantHasAccess = new AtomicBoolean(true);

  private LiveStreamProperties properties;
  private TestClock clock;
  private LiveConnectionRegistry registry;
  private RecordingTransactionManager transactions;
  private LiveTenantScope scope;
  private SimpleMeterRegistry meters;
  private ManualExecutor workers;
  private LiveStreamService service;
  private final List<ExecutorService> pools = new ArrayList<>();

  @BeforeEach
  void setUp() {
    properties = new LiveStreamProperties();
    properties.setEnabled(true);
    properties.setPollInterval(Duration.ofSeconds(2));
    properties.setHeartbeatInterval(Duration.ofSeconds(15));
    properties.setMaxConnectionLifetime(Duration.ofSeconds(60));
    properties.setSendTimeout(Duration.ofSeconds(5));
    properties.afterPropertiesSet();
    clock = new TestClock(START);
    registry = new LiveConnectionRegistry(properties);
    transactions = new RecordingTransactionManager();
    scope = new LiveTenantScope(transactions, properties);
    meters = new SimpleMeterRegistry();
    workers = new ManualExecutor();
    service = service(workers);
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    for (ExecutorService pool : pools) {
      pool.shutdownNow();
      pool.awaitTermination(5, TimeUnit.SECONDS);
    }
    TenantContext.clear();
    SecurityContextHolder.clearContext();
  }

  // ── opening ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("L01: ready is the first frame, with the revision read; version 0 is valid")
  void readyIsTheFirstFrame() {
    FakeChannel channel = new FakeChannel();
    UUID connection = open(service, tenantA, userA, new FakeSource(visible("0")), channel);

    assertThat(channel.labels()).containsExactly("ready:0");
    LiveFrame.Ready ready = (LiveFrame.Ready) channel.frames.getFirst();
    assertThat(ready.data().connectionId()).isEqualTo(connection);
    assertThat(ready.data().resourceId()).isEqualTo(resource);
    assertThat(registry.size()).isEqualTo(1);
    assertThat(registry.reservedCount()).isEqualTo(1);
    assertThat(service.openTransports()).isEqualTo(1);
  }

  @Test
  @DisplayName("L03/L11/L17/L19: every refusal opens no transport and gives the capacity back")
  void refusalsOpenNothingAndReleaseCapacity() {
    assertRefused(new FakeSource(LiveReadResult.Hidden.FORBIDDEN), Rejection.FORBIDDEN, 0);
    assertRefused(new FakeSource(LiveReadResult.Hidden.NOT_FOUND), Rejection.NOT_FOUND, 0);
    assertRefused(
        new FakeSource(new IllegalStateException("database down")), Rejection.UNAVAILABLE, 0);

    properties.setEnabled(false);
    assertRefused(new FakeSource(visible("1")), Rejection.FEATURE_DISABLED, 0);
    properties.setEnabled(true);

    AtomicBoolean opened = new AtomicBoolean();
    LiveActor expiredActor = new LiveActor(tenantA, userA, START);
    assertThatThrownBy(
            () ->
                service.open(
                    expiredActor,
                    new FakeSource(visible("1")),
                    resource,
                    () -> {
                      opened.set(true);
                      return new FakeChannel();
                    }))
        .isInstanceOfSatisfying(
            LiveStreamRejectedException.class,
            rejected -> assertThat(rejected.rejection()).isEqualTo(Rejection.UNAUTHENTICATED));
    assertThat(opened).isFalse();
    assertThat(registry.reservedCount()).isZero();
    assertThat(service.openTransports()).isZero();
  }

  @Test
  @DisplayName("L11: a transport that cannot be opened is 503 and returns the slot")
  void failingTransportOpenIsUnavailable() {
    assertThatThrownBy(
            () ->
                service.open(
                    actor(tenantA, userA),
                    new FakeSource(visible("1")),
                    resource,
                    () -> {
                      throw new IOException("client gone before the headers");
                    }))
        .isInstanceOfSatisfying(
            LiveStreamRejectedException.class,
            rejected -> assertThat(rejected.rejection()).isEqualTo(Rejection.UNAVAILABLE));
    assertThat(registry.reservedCount()).isZero();
  }

  @Test
  @DisplayName("R2: a tenant without access is refused 403 and its source is never read")
  void tenantWithoutAccessIsRefused() {
    tenantHasAccess.set(false);
    FakeSource source = new FakeSource(visible("1"));

    assertRefused(source, Rejection.FORBIDDEN, 0);
    assertThat(source.reads.get()).isZero();
  }

  @Test
  @DisplayName("R2: a tenant losing access closes running streams with ACCESS_REVOKED")
  void tenantLosingAccessRevokes() {
    FakeChannel channel = new FakeChannel();
    FakeSource source = new FakeSource(visible("1"));
    open(service, tenantA, userA, source, channel);

    tenantHasAccess.set(false);
    clock.advance(Duration.ofSeconds(2));
    service.tick();
    workers.runAll();

    assertThat(channel.labels()).containsExactly("ready:1", "closed:ACCESS_REVOKED");
    assertThat(source.reads.get()).isEqualTo(1);
    assertThat(registry.reservedCount()).isZero();
  }

  @Test
  @DisplayName("L17: the user limit refuses with CAPACITY; an ended transport frees its slot")
  void capacityIsBoundedAndReturned() {
    properties.setMaxConnectionsPerUserPerTenantPerInstance(2);
    FakeSource source = new FakeSource(visible("1"));
    open(service, tenantA, userA, source, new FakeChannel());
    FakeChannel second = new FakeChannel();
    open(service, tenantA, userA, source, second);

    assertRefused(source, Rejection.CAPACITY, 2);
    // Another user of the same tenant is not limited by A's tabs.
    open(service, tenantA, userB, source, new FakeChannel());

    second.listener.completed();
    assertThat(registry.reservedCount()).isEqualTo(2);
    open(service, tenantA, userA, source, new FakeChannel());
    assertThat(registry.reservedCount()).isEqualTo(3);
  }

  @Test
  @DisplayName("L12: a first read that outlives the token opens no stream")
  void slowFirstReadPastExpiryOpensNothing() {
    LiveActor actor = new LiveActor(tenantA, userA, START.plusSeconds(1));
    FakeSource source = new FakeSource(visible("4"));
    source.duringRead = () -> clock.advance(Duration.ofSeconds(2));
    AtomicBoolean opened = new AtomicBoolean();

    assertThatThrownBy(
            () ->
                service.open(
                    actor,
                    source,
                    resource,
                    () -> {
                      opened.set(true);
                      return new FakeChannel();
                    }))
        .isInstanceOfSatisfying(
            LiveStreamRejectedException.class,
            rejected -> assertThat(rejected.rejection()).isEqualTo(Rejection.UNAUTHENTICATED));
    assertThat(opened).isFalse();
    assertThat(registry.reservedCount()).isZero();
    assertThat(registry.size()).isZero();
  }

  // ── checks ──────────────────────────────────────────────────────────────

  @Test
  @DisplayName(
      "L08: a commit between the ready read and the registration is seen by the first check")
  void commitBeforeRegistrationIsSeenByFirstCheck() {
    FakeChannel channel = new FakeChannel();
    open(service, tenantA, userA, new FakeSource(visible("1"), visible("2")), channel);

    advanceAndRun(Duration.ofSeconds(2));

    assertThat(channel.labels()).containsExactly("ready:1", "invalidated:2");
  }

  @Test
  @DisplayName("L05/L06: an unchanged revision sends nothing until the heartbeat is due")
  void unchangedRevisionOnlyHeartbeats() {
    FakeChannel channel = new FakeChannel();
    FakeSource source = new FakeSource(visible("5"));
    open(service, tenantA, userA, source, channel);

    for (int i = 0; i < 7; i++) {
      advanceAndRun(Duration.ofSeconds(2));
    }
    assertThat(channel.labels()).containsExactly("ready:5");

    advanceAndRun(Duration.ofSeconds(2));
    assertThat(channel.labels()).containsExactly("ready:5", "heartbeat");
    assertThat(source.reads.get()).isEqualTo(9);
  }

  @Test
  @DisplayName("L09: changes may coalesce, the latest wins and an older revision never follows it")
  void coalescedChangesNeverRegress() {
    FakeChannel channel = new FakeChannel();
    open(
        service,
        tenantA,
        userA,
        new FakeSource(visible("1"), visible("3"), visible("3"), visible("4")),
        channel);

    for (int i = 0; i < 3; i++) {
      advanceAndRun(Duration.ofSeconds(2));
    }

    assertThat(channel.labels()).containsExactly("ready:1", "invalidated:3", "invalidated:4");
  }

  @Test
  @DisplayName("L09/L17: a due connection never has two jobs; the queue never stacks its checks")
  void checksNeverOverlap() {
    open(service, tenantA, userA, new FakeSource(visible("1")), new FakeChannel());

    clock.advance(Duration.ofSeconds(2));
    service.tick();
    service.tick();
    clock.advance(Duration.ofSeconds(2));
    service.tick();

    assertThat(workers.queued()).isEqualTo(1);
  }

  @Test
  @DisplayName("L17: a full worker queue skips the check; the next tick tries again")
  void fullQueueRetriesOnNextTick() {
    FakeChannel channel = new FakeChannel();
    open(service, tenantA, userA, new FakeSource(visible("1"), visible("2")), channel);
    clock.advance(Duration.ofSeconds(2));

    workers.full(true);
    service.tick();
    assertThat(workers.queued()).isZero();

    workers.full(false);
    service.tick();
    workers.runAll();
    assertThat(channel.labels()).containsExactly("ready:1", "invalidated:2");
    assertThat(meters.counter("live.stream.workers.rejected", "pool", "live").count())
        .isEqualTo(1.0);
  }

  @Test
  @DisplayName("L10: lost access closes with ACCESS_REVOKED; nothing follows and capacity returns")
  void hiddenClosesWithAccessRevoked() {
    FakeChannel channel = new FakeChannel();
    FakeSource source = new FakeSource(visible("1"), LiveReadResult.Hidden.NOT_FOUND, visible("2"));
    open(service, tenantA, userA, source, channel);

    for (int i = 0; i < 3; i++) {
      advanceAndRun(Duration.ofSeconds(2));
    }

    assertThat(channel.labels()).containsExactly("ready:1", "closed:ACCESS_REVOKED");
    assertThat(channel.completions.get()).isEqualTo(1);
    assertThat(registry.size()).isZero();
    assertThat(registry.reservedCount()).isZero();
    assertThat(service.openTransports()).isZero();
    assertThat(source.reads.get()).isEqualTo(2);
  }

  @Test
  @DisplayName("L11: a failed read closes TEMPORARILY_UNAVAILABLE and never looks healthy")
  void failedReadClosesWithoutHeartbeat() {
    FakeChannel channel = new FakeChannel();
    open(
        service,
        tenantA,
        userA,
        new FakeSource(visible("1"), new IllegalStateException("timeout")),
        channel);

    // The heartbeat is due on this very check; a failed read must not send it.
    advanceAndRun(Duration.ofSeconds(16));

    assertThat(channel.labels()).containsExactly("ready:1", "closed:TEMPORARILY_UNAVAILABLE");
    assertThat(registry.reservedCount()).isZero();
  }

  @Test
  @DisplayName("L12: after token expiry no read and no business frame, only AUTH_EXPIRED")
  void expiryClosesAuthExpired() {
    FakeChannel channel = new FakeChannel();
    FakeSource source = new FakeSource(visible("1"), visible("2"));
    service.open(
        new LiveActor(tenantA, userA, START.plusSeconds(30)), source, resource, () -> channel);

    advanceAndRun(Duration.ofSeconds(30));

    assertThat(channel.labels()).containsExactly("ready:1", "closed:AUTH_EXPIRED");
    assertThat(source.reads.get()).isEqualTo(1);
    assertThat(registry.reservedCount()).isZero();
  }

  @Test
  @DisplayName("L12: a token expiring during the check's read stops the frame right before writing")
  void expiryDuringReadStopsTheWrite() {
    FakeChannel channel = new FakeChannel();
    FakeSource source = new FakeSource(visible("1"), visible("2"));
    service.open(
        new LiveActor(tenantA, userA, START.plusSeconds(3)), source, resource, () -> channel);
    source.duringRead = () -> clock.advance(Duration.ofSeconds(2));

    advanceAndRun(Duration.ofSeconds(2));

    assertThat(channel.labels()).containsExactly("ready:1", "closed:AUTH_EXPIRED");
  }

  @Test
  @DisplayName("L12: the maximum lifetime closes RECONNECT_REQUIRED")
  void lifetimeClosesReconnectRequired() {
    FakeChannel channel = new FakeChannel();
    open(service, tenantA, userA, new FakeSource(visible("1")), channel);

    advanceAndRun(Duration.ofSeconds(60));

    assertThat(channel.labels()).containsExactly("ready:1", "closed:RECONNECT_REQUIRED");
  }

  // ── transport ending and cleanup ────────────────────────────────────────

  @Test
  @DisplayName("L16: a failed write ends the connection once and frees its capacity")
  void failedWriteEndsConnection() {
    FakeChannel channel = new FakeChannel();
    open(service, tenantA, userA, new FakeSource(visible("1"), visible("2")), channel);
    channel.failure = new IOException("Broken pipe");

    advanceAndRun(Duration.ofSeconds(2));

    assertThat(channel.labels()).containsExactly("ready:1");
    assertThat(registry.size()).isZero();
    assertThat(registry.reservedCount()).isZero();
    assertThat(channel.completions.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("L16: racing end callbacks release once; an old callback cannot end a new stream")
  void endCallbacksAreIdempotentAndBound() throws InterruptedException {
    FakeChannel first = new FakeChannel();
    FakeSource source = new FakeSource(visible("1"));
    open(service, tenantA, userA, source, first);

    CountDownLatch go = new CountDownLatch(1);
    List<Thread> racers = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      int kind = i % 3;
      Thread racer =
          new Thread(
              () -> {
                await(go);
                switch (kind) {
                  case 0 -> first.listener.completed();
                  case 1 -> first.listener.failed(new IOException("reset"));
                  default -> first.listener.timedOut();
                }
              });
      racer.start();
      racers.add(racer);
    }
    go.countDown();
    for (Thread racer : racers) {
      racer.join(5000);
    }
    assertThat(registry.reservedCount()).isZero();
    assertThat(service.openTransports()).isZero();
    double closed =
        meters.find("live.stream.connections.closed").counters().stream()
            .mapToDouble(counter -> counter.count())
            .sum();
    assertThat(closed).isEqualTo(1.0);

    FakeChannel second = new FakeChannel();
    open(service, tenantA, userA, source, second);
    first.listener.completed();
    first.listener.failed(new IOException("late"));
    first.listener.timedOut();

    assertThat(registry.size()).isEqualTo(1);
    assertThat(registry.reservedCount()).isEqualTo(1);
    advanceAndRun(Duration.ofSeconds(16));
    assertThat(second.labels()).containsExactly("ready:1", "heartbeat");
  }

  // ── R1: slow consumers never hold a thread ──────────────────────────────

  @Test
  @DisplayName(
      "L18/R1: a stalled client is closed and aborted after the send timeout, never completed into"
          + " a flush; its slot returns only when the container reports the end")
  void stalledClientIsAbortedAndCapacityFollowsTheContainer() {
    FakeChannel slow = new FakeChannel();
    slow.containerEndsOnAbort = false;
    open(service, tenantA, userA, new FakeSource(visible("1"), visible("2")), slow);
    slow.stalledSince = clock.instant();

    clock.advance(Duration.ofSeconds(4));
    service.tick();
    assertThat(registry.size()).isEqualTo(1);
    assertThat(service.stalledConnections()).isEqualTo(1);
    assertThat(slow.aborts.get()).isZero();

    clock.advance(Duration.ofSeconds(1));
    service.tick();
    workers.runAll();

    assertThat(registry.size()).isZero();
    assertThat(slow.aborts.get()).isEqualTo(1);
    assertThat(slow.completions.get()).as("a stalled stream is never completed normally").isZero();
    assertThat(slow.labels()).containsExactly("ready:1");
    assertThat(meters.counter("live.stream.connections.closed", "reason", "SLOW_CONSUMER").count())
        .isEqualTo(1.0);
    assertThat(meters.counter("live.stream.transports.aborted", "pool", "live").count())
        .isEqualTo(1.0);
    // Until the container reports the end, the slot stands for the response it still holds.
    assertThat(registry.reservedCount()).isEqualTo(1);
    assertThat(service.openTransports()).isEqualTo(1);
    assertThat(service.closingTransports()).isEqualTo(1);

    clock.advance(Duration.ofSeconds(10));
    service.tick();
    assertThat(slow.aborts.get()).as("aborted once").isEqualTo(1);

    slow.listener.failed(new IOException("closed without flush"));
    assertThat(registry.reservedCount()).isZero();
    assertThat(service.openTransports()).isZero();
    assertThat(service.closingTransports()).isZero();
  }

  @Test
  @DisplayName(
      "L18/R1: a closing stream whose client stops taking the closed frame is aborted after the"
          + " send timeout, keeping its own close reason")
  void closingStalledTransportIsAborted() {
    FakeChannel slow = new FakeChannel();
    slow.containerEndsOnComplete = false;
    open(
        service,
        tenantA,
        userA,
        new FakeSource(visible("1"), LiveReadResult.Hidden.NOT_FOUND),
        slow);

    advanceAndRun(Duration.ofSeconds(2));
    // The closed frame is waiting in the transport: the normal end waits for the client.
    slow.stalledSince = clock.instant();
    assertThat(slow.labels()).containsExactly("ready:1", "closed:ACCESS_REVOKED");
    assertThat(slow.completions.get()).isEqualTo(1);
    assertThat(registry.size()).isZero();
    assertThat(registry.reservedCount()).isEqualTo(1);
    assertThat(service.closingTransports()).isEqualTo(1);
    assertThat(service.stalledConnections()).isEqualTo(1);

    clock.advance(Duration.ofSeconds(5));
    service.tick();

    assertThat(slow.aborts.get()).isEqualTo(1);
    assertThat(registry.reservedCount()).isZero();
    assertThat(service.openTransports()).isZero();
    assertThat(
            meters.find("live.stream.connections.closed").tag("reason", "SLOW_CONSUMER").counter())
        .isNull();
    assertThat(meters.counter("live.stream.connections.closed", "reason", "ACCESS_REVOKED").count())
        .isEqualTo(1.0);
  }

  @Test
  @DisplayName(
      "L16/R1: the container's timeout closes the connection but keeps the slot until the"
          + " transport reports its end")
  void containerTimeoutKeepsCapacityUntilTheEnd() {
    FakeChannel channel = new FakeChannel();
    open(service, tenantA, userA, new FakeSource(visible("1")), channel);

    channel.listener.timedOut();

    assertThat(registry.size()).isZero();
    assertThat(registry.reservedCount()).isEqualTo(1);
    assertThat(service.openTransports()).isEqualTo(1);
    assertThat(meters.counter("live.stream.transports.timeouts", "pool", "live").count())
        .isEqualTo(1.0);
    assertThat(
            meters.counter("live.stream.connections.closed", "reason", "TRANSPORT_TIMEOUT").count())
        .isEqualTo(1.0);

    channel.listener.completed();
    assertThat(registry.reservedCount()).isZero();
    assertThat(service.openTransports()).isZero();
  }

  @Test
  @DisplayName(
      "L18/R1: a client stalled from its ready frame on is closed the same way; nothing waits on it")
  void clientStalledFromReadyIsClosed() {
    FakeChannel slow = new FakeChannel();
    slow.stalledSince = START;
    open(service, tenantA, userA, new FakeSource(visible("1")), slow);

    clock.advance(Duration.ofSeconds(5));
    service.tick();

    assertThat(registry.size()).isZero();
    assertThat(registry.reservedCount()).isZero();
    assertThat(workers.queued()).isZero();
  }

  @Test
  @DisplayName(
      "L18/R1: with one worker and several stalled clients, a healthy client is still served and"
          + " the terminal frame of a stalled client never blocks")
  void stalledClientsNeverHoldTheOnlyWorker() throws Exception {
    ExecutorService single = Executors.newSingleThreadExecutor();
    pools.add(single);
    LiveStreamService threaded = service(single);
    List<FakeChannel> stalled = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      FakeChannel channel = new FakeChannel();
      channel.containerEndsOnComplete = false;
      open(
          threaded,
          tenantA,
          UUID.randomUUID(),
          new FakeSource(visible("1"), visible("2")),
          channel);
      channel.stalledSince = clock.instant();
      stalled.add(channel);
    }
    FakeChannel healthy = new FakeChannel();
    open(
        threaded,
        tenantA,
        userB,
        new FakeSource(visible("1"), visible("2"), visible("3")),
        healthy);

    clock.advance(Duration.ofSeconds(2));
    await(() -> tickedUntil(threaded, () -> healthy.labels().contains("invalidated:2")));

    // Switching off writes a closed frame to every client, stalled ones included: never waits.
    properties.setEnabled(false);
    await(
        () ->
            tickedUntil(
                threaded,
                () ->
                    healthy.labels().contains("closed:FEATURE_DISABLED")
                        && stalled.stream().allMatch(channel -> channel.completions.get() == 1)));
    assertThat(stalled)
        .allSatisfy(channel -> assertThat(channel.labels()).contains("closed:FEATURE_DISABLED"));
    single.submit(() -> null).get(5, TimeUnit.SECONDS);
    assertThat(registry.size()).isZero();
    assertThat(registry.reservedCount()).as("the stalled ones are still closing").isEqualTo(3);

    // Their clients never take the closed frame: the scheduler aborts them, without a worker.
    clock.advance(Duration.ofSeconds(5));
    threaded.tick();
    assertThat(stalled).allSatisfy(channel -> assertThat(channel.aborts.get()).isEqualTo(1));
    assertThat(healthy.aborts.get()).isZero();
    assertThat(registry.reservedCount()).isZero();
    assertThat(threaded.openTransports()).isZero();
  }

  @Test
  @DisplayName("L19: switching the channel off closes running streams and refuses new ones")
  void switchingOffClosesRunningStreams() {
    FakeChannel channel = new FakeChannel();
    open(service, tenantA, userA, new FakeSource(visible("1")), channel);

    properties.setEnabled(false);
    service.tick();
    workers.runAll();

    assertThat(channel.labels()).containsExactly("ready:1", "closed:FEATURE_DISABLED");
    assertThat(registry.reservedCount()).isZero();
    assertRefused(new FakeSource(visible("1")), Rejection.FEATURE_DISABLED, 0);
  }

  @Test
  @DisplayName("L16: shutdown closes every stream with RECONNECT_REQUIRED, once")
  void shutdownClosesEverything() {
    FakeChannel first = new FakeChannel();
    FakeChannel second = new FakeChannel();
    open(service, tenantA, userA, new FakeSource(visible("1")), first);
    open(service, tenantB, userB, new FakeSource(visible("7")), second);

    service.closeAll(LiveCloseReason.RECONNECT_REQUIRED);
    workers.runAll();
    service.shutdown();
    workers.runAll();

    assertThat(first.labels()).containsExactly("ready:1", "closed:RECONNECT_REQUIRED");
    assertThat(second.labels()).containsExactly("ready:7", "closed:RECONNECT_REQUIRED");
    assertThat(first.completions.get()).isEqualTo(1);
    assertThat(second.completions.get()).isEqualTo(1);
    assertThat(registry.size()).isZero();
    assertThat(registry.reservedCount()).isZero();
  }

  // ── tenant scope ────────────────────────────────────────────────────────

  @Test
  @DisplayName(
      "L13: on one shared worker, tenant A then B, with a failure between: each read sees only its"
          + " own tenant, bound before the transaction, and nothing stays on the thread")
  void sharedWorkerNeverLeaksTenantContext() throws Exception {
    ExecutorService single = Executors.newSingleThreadExecutor();
    pools.add(single);
    LiveStreamService threaded = service(single);
    FakeSource sourceA = new FakeSource(visible("1"), new IllegalStateException("tenant A fails"));
    FakeSource sourceB = new FakeSource(visible("1"), visible("2"));
    FakeChannel channelA = new FakeChannel();
    FakeChannel channelB = new FakeChannel();

    // The opening request's own context is restored after the opening read.
    Authentication requestUser = new UsernamePasswordAuthenticationToken("request-user", "n/a");
    SecurityContextHolder.getContext().setAuthentication(requestUser);
    UUID requestTenant = UUID.randomUUID();
    TenantContext.setCurrentTenantId(requestTenant);
    open(threaded, tenantA, userA, sourceA, channelA);
    open(threaded, tenantB, userB, sourceB, channelB);
    assertThat(TenantContext.getCurrentTenantIdOrNull()).isEqualTo(requestTenant);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(requestUser);
    TenantContext.clear();
    SecurityContextHolder.clearContext();

    clock.advance(Duration.ofSeconds(2));
    threaded.tick();
    await(() -> channelA.labels().contains("closed:TEMPORARILY_UNAVAILABLE"));
    await(() -> channelB.labels().contains("invalidated:2"));

    assertThat(sourceA.tenantsSeen).containsOnly(tenantA);
    assertThat(sourceB.tenantsSeen).containsOnly(tenantB);
    assertThat(transactions.tenantsAtBegin).doesNotContainNull();
    assertThat(transactions.authenticationsAtBegin).isEmpty();
    assertThat(transactions.rollbacks.get()).isEqualTo(1);

    AtomicReference<Object> leftOnWorker = new AtomicReference<>("unset");
    single
        .submit(
            () ->
                leftOnWorker.set(
                    TenantContext.getCurrentTenantIdOrNull() == null
                            && SecurityContextHolder.getContext().getAuthentication() == null
                        ? "clean"
                        : "leaked"))
        .get(5, TimeUnit.SECONDS);
    assertThat(leftOnWorker.get()).isEqualTo("clean");
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private LiveStreamService service(Executor executor) {
    return new LiveStreamService(
        registry,
        scope,
        tenantHasAccess::get,
        properties,
        clock,
        new LiveStreamMetrics(meters),
        new ObjectMapper(),
        executor);
  }

  private UUID open(
      LiveStreamService target, UUID tenant, UUID user, FakeSource source, FakeChannel channel) {
    return target.open(actor(tenant, user), source, resource, () -> channel);
  }

  /** One more scheduler pass, then whether the condition holds (for real worker threads). */
  private static boolean tickedUntil(
      LiveStreamService target, java.util.function.BooleanSupplier condition) {
    target.tick();
    return condition.getAsBoolean();
  }

  private void advanceAndRun(Duration duration) {
    clock.advance(duration);
    service.tick();
    workers.runAll();
  }

  private LiveActor actor(UUID tenant, UUID user) {
    return new LiveActor(tenant, user, START.plus(Duration.ofMinutes(15)));
  }

  private void assertRefused(FakeSource source, Rejection expected, int reservedAfter) {
    AtomicBoolean opened = new AtomicBoolean();
    assertThatThrownBy(
            () ->
                service.open(
                    actor(tenantA, userA),
                    source,
                    resource,
                    () -> {
                      opened.set(true);
                      return new FakeChannel();
                    }))
        .isInstanceOfSatisfying(
            LiveStreamRejectedException.class,
            rejected -> assertThat(rejected.rejection()).isEqualTo(expected));
    assertThat(opened).as("no transport is opened for a refusal").isFalse();
    assertThat(registry.reservedCount()).isEqualTo(reservedAfter);
  }
}
