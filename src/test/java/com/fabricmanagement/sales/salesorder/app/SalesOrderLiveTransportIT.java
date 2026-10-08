package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.platform.realtime.app.LiveStreamService;
import com.fabricmanagement.platform.realtime.domain.LiveActor;
import com.fabricmanagement.platform.realtime.domain.LiveReadResult;
import com.fabricmanagement.platform.realtime.domain.LiveRevision;
import com.fabricmanagement.platform.realtime.domain.LiveRevisionSource;
import com.fabricmanagement.platform.realtime.dto.LiveCloseReason;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.ProtocolHandler;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * CEDIT-05 L18 and review R1 on the real Tomcat connector: clients that stop reading must never
 * hold a connector thread, a live worker or a socket beyond a bounded time, whichever way their
 * stream ends — dropped as a slow consumer while open, closed by {@code closeAll} and then aborted
 * while closing, ended by the container's async timeout, or left with a TCP reset.
 *
 * <p>The connector runs with four threads and a long connection (write) timeout. A connector thread
 * that had to flush unsent bytes to a client that does not read would stay blocked for that
 * timeout; with four threads, a wave of six such clients would starve every other request. Each
 * scenario therefore checks, independently of the channel's own counters, that Tomcat's connection
 * count returns to its baseline while the stalled clients still have not read a byte, that no
 * connector thread stays busy, and that a JSON request and a new stream are served at once.
 *
 * <p>Backpressure must not depend on the operating system's socket buffers, which differ widely
 * (macOS loopback absorbs far more than the order stream's small frames produce in a minute). The
 * stalled clients therefore subscribe to a test-only endpoint of this test's context whose source
 * changes its (large) revision on every read, so the real channel and transport write about a
 * megabyte per second to each of them. Production code has no such endpoint or switch.
 */
@TestPropertySource(
    properties = {
      "server.tomcat.threads.max=4",
      "server.tomcat.threads.min-spare=1",
      "server.tomcat.connection-timeout=120s"
    })
@Import(SalesOrderLiveTransportIT.FloodEndpoint.class)
class SalesOrderLiveTransportIT extends SalesOrderLiveItSupport {

  private static final String FLOOD_PATH = "/api/v1/test/live-flood/";
  private static final int FLOOD_FRAME_CHARS = 128 * 1024;
  private static final int STALLED_CLIENTS = 6; // more than the connector's four threads
  private static final Duration SERVED = Duration.ofSeconds(3);
  private static final HttpClient HTTP =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(5))
          .build();

  @Autowired private ServletWebServerApplicationContext webContext;
  @Autowired private MeterRegistry meterRegistry;

  private final List<RawSseClient> stalled = new ArrayList<>();

  @BeforeEach
  void transportStart() {
    liveProperties.setMaxConnectionLifetime(Duration.ofMinutes(5));
  }

  @AfterEach
  void transportEnd() {
    stalled.forEach(RawSseClient::close);
    stalled.clear();
  }

  // ── open streams: slow consumers ──────────────────────────────────────

  @Test
  @DisplayName(
      "R1: waves of clients that stop reading are dropped as slow consumers; the connector closes"
          + " their sockets without a reader, no connector thread stays busy, and readers, JSON"
          + " requests and new streams are served throughout")
  void slowConsumerWavesNeverHoldTheConnector() throws Exception {
    liveProperties.setSendTimeout(Duration.ofSeconds(1));
    LiveSse reader = subscribe(actorC);
    ready(reader);
    try (VersionPump pump = new VersionPump(orderId)) {
      pump.start();
      for (int wave = 1; wave <= 3; wave++) {
        long baseline = connectorConnections();
        double dropped = closed("SLOW_CONSUMER");
        openStalled(actorB);

        awaitState(
            "wave " + wave + ": every stalled client dropped as a slow consumer",
            Duration.ofSeconds(30),
            () -> closed("SLOW_CONSUMER") >= dropped + STALLED_CLIENTS);
        assertThat(reader.nextEvent(WAIT).event())
            .as("the reader is still served")
            .isEqualTo("invalidated");
        assertConnectorRecovered("wave " + wave, baseline);
        closeStalled();
      }
    }
  }

  // ── closing streams: closeAll, then the abort of what is still closing ─

  @Test
  @DisplayName(
      "R1: closeAll never waits on clients that stop reading; a reader gets its closed frame and"
          + " the end of the stream, the stalled ones are aborted while closing, and the connector"
          + " recovers without them reading")
  void closingStalledStreamsAreAborted() throws Exception {
    liveProperties.setSendTimeout(Duration.ofSeconds(30));
    LiveSse reader = subscribe(actorC);
    ready(reader);
    long baseline = connectorConnections();
    double aborted = counter("live.stream.transports.aborted");
    openStalled(actorB);
    awaitStalledClients();

    long closing = System.nanoTime();
    liveStreams.closeAll(LiveCloseReason.RECONNECT_REQUIRED);
    assertThat(Duration.ofNanos(System.nanoTime() - closing)).isLessThan(Duration.ofSeconds(1));
    awaitState("every connection closed", Duration.ofSeconds(5), () -> liveRegistry.size() == 0);
    // The normal end: the reader takes its closed frame and sees the stream end.
    expectClosed(reader, LiveCloseReason.RECONNECT_REQUIRED);
    assertThat(liveStreams.closingTransports())
        .as("the stalled ones are still closing")
        .isEqualTo(STALLED_CLIENTS);

    liveProperties.setSendTimeout(Duration.ofSeconds(1));
    assertConnectorRecovered("after closeAll", baseline);
    assertThat(counter("live.stream.transports.aborted")).isEqualTo(aborted + STALLED_CLIENTS);
  }

  // ── the container's async timeout ─────────────────────────────────────

  @Test
  @DisplayName(
      "R1: with the scheduler's abort out of reach, the container's async timeout ends stalled"
          + " closing streams without a flush, and only then is their capacity returned")
  void containerTimeoutEndsStalledStreams() throws Exception {
    // The async timeout is fixed when a stream opens: lifetime + poll + read + send + 5 s, ~14 s.
    liveProperties.setMaxConnectionLifetime(Duration.ofSeconds(6));
    liveProperties.setSendTimeout(Duration.ofSeconds(1));
    long baseline = connectorConnections();
    double timeouts = counter("live.stream.transports.timeouts");
    openStalled(actorB);
    // From now on the scheduler would wait ten minutes before aborting: the container ends them.
    liveProperties.setSendTimeout(Duration.ofMinutes(10));
    awaitState(
        "every flood stream stalled",
        Duration.ofSeconds(5),
        () -> liveStreams.stalledConnections() >= STALLED_CLIENTS);
    awaitState(
        "every stream closed at its lifetime, still closing",
        Duration.ofSeconds(10),
        () -> liveRegistry.size() == 0 && liveStreams.closingTransports() == STALLED_CLIENTS);
    assertThat(liveRegistry.reservedCount())
        .as("closing streams keep their slots")
        .isEqualTo(STALLED_CLIENTS);

    awaitState(
        "the container's timeout ended them",
        Duration.ofSeconds(30),
        () -> counter("live.stream.transports.timeouts") >= timeouts + STALLED_CLIENTS);
    assertConnectorRecovered("after the container timeout", baseline);
  }

  // ── clients that leave with a reset ───────────────────────────────────

  @Test
  @DisplayName(
      "R1: stalled clients that leave with a TCP reset end their transports through the"
          + " container's error, long before the scheduler would abort them")
  void resetClientsEndTheirTransports() throws Exception {
    liveProperties.setSendTimeout(Duration.ofMinutes(1));
    long baseline = connectorConnections();
    double errors = closed("TRANSPORT_ERROR");
    openStalled(actorB);
    awaitStalledClients();

    stalled.forEach(RawSseClient::reset);
    stalled.clear();

    awaitState(
        "every transport ended by the container's error",
        Duration.ofSeconds(15),
        () ->
            liveStreams.openTransports() == 0
                && liveRegistry.reservedCount() == 0
                && closed("TRANSPORT_ERROR") >= errors + STALLED_CLIENTS);
    assertConnectorRecovered("after the resets", baseline);
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  /** Opens the stalled clients: each reads the response head, then never reads again. */
  private void openStalled(Actor actor) throws Exception {
    String token = token(actor);
    for (int i = 0; i < STALLED_CLIENTS; i++) {
      RawSseClient client =
          RawSseClient.connect(port, FLOOD_PATH + UUID.randomUUID() + "/live-events", token);
      stalled.add(client);
      assertThat(client.status()).isEqualTo(200);
    }
  }

  /**
   * Every stalled client's socket is full: the transports stay stalled without a break. A single
   * moment is not enough, since a large receive buffer (macOS) takes data in bursts until it is
   * full.
   */
  private void awaitStalledClients() {
    Duration hold = Duration.ofSeconds(2);
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    long since = System.nanoTime();
    while (System.nanoTime() - since < hold.toNanos()) {
      if (liveStreams.stalledConnections() < STALLED_CLIENTS) {
        since = System.nanoTime();
      }
      if (System.nanoTime() > deadline) {
        throw new AssertionError(
            "the stalled clients did not stay stalled for " + hold + " within 30s; " + state());
      }
      try {
        Thread.sleep(20);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError(interrupted);
      }
    }
  }

  private void closeStalled() {
    stalled.forEach(RawSseClient::close);
    stalled.clear();
  }

  /**
   * The connector is back to its baseline while the stalled clients still have not read: their
   * sockets are closed, no connector thread is busy, no transport or slot outlives a registered
   * connection, and a JSON request and a new stream are served at once.
   */
  private void assertConnectorRecovered(String when, long baseline) throws Exception {
    awaitState(
        when + ": the stalled sockets are closed by the server",
        Duration.ofSeconds(10),
        () -> connectorConnections() <= baseline);
    awaitState(
        when + ": no connector thread busy",
        Duration.ofSeconds(5),
        () -> busyConnectorThreads() == 0);
    awaitState(
        when + ": transports and slots returned",
        Duration.ofSeconds(5),
        () ->
            liveStreams.closingTransports() == 0
                && liveStreams.openTransports() == liveRegistry.size()
                && liveRegistry.reservedCount() == liveRegistry.size());

    long started = System.nanoTime();
    HttpResponse<String> order =
        HTTP.send(
            HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/api/v1/sales/orders/" + orderId))
                .header("Authorization", "Bearer " + token(actorA))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(order.statusCode()).as(order.body()).isEqualTo(200);
    assertThat(Duration.ofNanos(System.nanoTime() - started))
        .as(when + ": JSON")
        .isLessThan(SERVED);

    started = System.nanoTime();
    ready(subscribe(actorA));
    assertThat(Duration.ofNanos(System.nanoTime() - started))
        .as(when + ": a new stream")
        .isLessThan(SERVED);
  }

  private void awaitState(String what, Duration bound, BooleanSupplier condition) {
    long deadline = System.nanoTime() + bound.toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError(what + " — not reached within " + bound + "; " + state());
      }
      try {
        Thread.sleep(50);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError(interrupted);
      }
    }
  }

  /** Everything a failure needs to be understood without a debugger. */
  private String state() {
    return "connector connections="
        + connectorConnections()
        + ", busy connector threads="
        + busyConnectorThreads()
        + ", registry="
        + liveRegistry.size()
        + ", reserved="
        + liveRegistry.reservedCount()
        + ", transports="
        + liveStreams.openTransports()
        + ", closing="
        + liveStreams.closingTransports()
        + ", stalled="
        + liveStreams.stalledConnections()
        + ", queued checks="
        + liveStreams.queuedChecks()
        + ", slow consumers="
        + closed("SLOW_CONSUMER")
        + ", aborted="
        + counter("live.stream.transports.aborted")
        + ", timeouts="
        + counter("live.stream.transports.timeouts");
  }

  private AbstractProtocol<?> protocol() {
    TomcatWebServer server = (TomcatWebServer) webContext.getWebServer();
    ProtocolHandler handler = server.getTomcat().getConnector().getProtocolHandler();
    return (AbstractProtocol<?>) handler;
  }

  /** Tomcat's own count of open connections, independent of the channel's counters. */
  private long connectorConnections() {
    return protocol().getConnectionCount();
  }

  private int busyConnectorThreads() {
    Executor executor = protocol().getExecutor();
    return executor instanceof ThreadPoolExecutor pool ? pool.getActiveCount() : -1;
  }

  private double closed(String reason) {
    return meterRegistry.counter("live.stream.connections.closed", "reason", reason).count();
  }

  private double counter(String name) {
    return meterRegistry.counter(name, "pool", "live").count();
  }

  /** Commits a new order version every 30 ms from outside the application until closed. */
  private static final class VersionPump implements AutoCloseable {
    private final UUID order;
    private final Thread thread;
    private volatile boolean running = true;

    VersionPump(UUID order) {
      this.order = order;
      this.thread = new Thread(this::run, "live-version-pump");
      this.thread.setDaemon(true);
    }

    void start() {
      thread.start();
    }

    private void run() {
      try (Connection owner = ownerConnection();
          PreparedStatement bump =
              owner.prepareStatement(
                  "UPDATE sales_ord.sales_order SET version = version + 1 WHERE id = ?")) {
        bump.setObject(1, order);
        while (running) {
          bump.executeUpdate();
          Thread.sleep(30);
        }
      } catch (SQLException | InterruptedException stopped) {
        // The test ends the pump; nothing to report.
      }
    }

    @Override
    public void close() throws InterruptedException {
      running = false;
      thread.join(5000);
    }
  }

  /** Registers the test-only flood endpoint in this test's context alone. */
  @TestConfiguration
  static class FloodEndpoint {
    @Bean
    FloodController liveFloodController(LiveStreamService live, Clock clock) {
      return new FloodController(live, clock);
    }
  }

  /**
   * A stream whose revision is large and changes on every read: the real channel then writes about
   * a megabyte per second to a client, so a client that stops reading stalls the transport within a
   * second on any operating system. Test sources only; never part of the application. A plain
   * {@code @Controller} (it writes the response itself): not a sales REST endpoint, so the sales
   * endpoint inventory of {@code SalesEndpointAuthorizationArchTest} does not list it.
   */
  @Controller
  static class FloodController {
    private static final String PADDING = "x".repeat(FLOOD_FRAME_CHARS);

    private final LiveStreamService live;
    private final Clock clock;
    private final AtomicLong reads = new AtomicLong();
    private final LiveRevisionSource source =
        new LiveRevisionSource() {
          @Override
          public String resourceType() {
            return "test-flood";
          }

          @Override
          public LiveReadResult read(LiveActor actor, UUID resourceId) {
            return new LiveReadResult.Visible(
                new LiveRevision(reads.incrementAndGet() + "-" + PADDING));
          }
        };

    FloodController(LiveStreamService live, Clock clock) {
      this.live = live;
      this.clock = clock;
    }

    @GetMapping(FLOOD_PATH + "{resourceId}/live-events")
    public void flood(
        @PathVariable UUID resourceId,
        Authentication authentication,
        HttpServletRequest request,
        HttpServletResponse response) {
      AuthenticatedUserContext principal = (AuthenticatedUserContext) authentication.getPrincipal();
      LiveActor actor =
          new LiveActor(
              principal.tenantId(),
              principal.userId(),
              clock.instant().plus(Duration.ofMinutes(10)));
      live.subscribe(actor, source, resourceId, request, response);
    }
  }
}
