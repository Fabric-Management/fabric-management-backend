package com.fabricmanagement.platform.realtime.app;

import com.fabricmanagement.common.infrastructure.tenant.CurrentTenantAccessPort;
import com.fabricmanagement.platform.realtime.domain.LiveActor;
import com.fabricmanagement.platform.realtime.domain.LiveReadResult;
import com.fabricmanagement.platform.realtime.domain.LiveResource;
import com.fabricmanagement.platform.realtime.domain.LiveRevisionSource;
import com.fabricmanagement.platform.realtime.domain.exception.LiveStreamRejectedException;
import com.fabricmanagement.platform.realtime.domain.exception.LiveStreamRejectedException.Rejection;
import com.fabricmanagement.platform.realtime.dto.LiveCloseReason;
import com.fabricmanagement.platform.realtime.dto.LiveClosedDto;
import com.fabricmanagement.platform.realtime.dto.LiveInvalidatedDto;
import com.fabricmanagement.platform.realtime.dto.LiveReadyDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The live change channel (CEDIT-05 §5): opens authorised streams, re-reads each connection's
 * committed revision and current access on a schedule, and tells the client to read again when the
 * revision changed. The revision is read from PostgreSQL by every instance on its own, so a save
 * committed through any instance reaches streams held by any other; nothing depends on an
 * in-process event bus or on sticky sessions. Signals may coalesce (R0, R1, R2 can arrive as R2);
 * the client always reads the full current state and never counts frames.
 *
 * <p>Work is bounded: capacity is reserved before anything is read, each connection has at most one
 * job at a time, jobs run on a fixed pool with a bounded queue, and no thread ever waits on a
 * client: the transport writes non-blocking and reports since when it has been stalled (R1). Every
 * transport is watched until the container reports its end, open or already closing: one stalled
 * longer than the send timeout is closed as a slow consumer and aborted, so the container drops
 * what it still holds instead of flushing it to a client that does not read. A slot is given back
 * only when the container reports the transport's end; after that report the container closes the
 * socket without waiting on the client.
 *
 * <p>Every read first asks whether the actor's tenant still has access, through RLS on the tenant's
 * own row (R2); a suspended, cancelled or deactivated tenant closes and refuses streams.
 */
@Service
@Slf4j
public class LiveStreamService {

  private final LiveConnectionRegistry registry;
  private final LiveTenantScope tenantScope;
  private final CurrentTenantAccessPort tenantAccess;
  private final LiveStreamProperties properties;
  private final Clock clock;
  private final LiveStreamMetrics metrics;
  private final ObjectMapper objectMapper;
  private final Executor workers;
  private final ThreadPoolExecutor ownedWorkers;

  /** Every response turned into a stream whose end the container has not reported yet. */
  private final Set<LiveConnection> transports = ConcurrentHashMap.newKeySet();

  @Autowired
  public LiveStreamService(
      LiveConnectionRegistry registry,
      LiveTenantScope tenantScope,
      CurrentTenantAccessPort tenantAccess,
      LiveStreamProperties properties,
      Clock clock,
      LiveStreamMetrics metrics,
      ObjectMapper objectMapper) {
    this(
        registry,
        tenantScope,
        tenantAccess,
        properties,
        clock,
        metrics,
        objectMapper,
        newWorkerPool(properties));
  }

  LiveStreamService(
      LiveConnectionRegistry registry,
      LiveTenantScope tenantScope,
      CurrentTenantAccessPort tenantAccess,
      LiveStreamProperties properties,
      Clock clock,
      LiveStreamMetrics metrics,
      ObjectMapper objectMapper,
      Executor workers) {
    this.registry = registry;
    this.tenantScope = tenantScope;
    this.tenantAccess = tenantAccess;
    this.properties = properties;
    this.clock = clock;
    this.metrics = metrics;
    this.objectMapper = objectMapper;
    this.workers = workers;
    this.ownedWorkers = workers instanceof ThreadPoolExecutor pool ? pool : null;
    metrics.bind(
        registry::size,
        transports::size,
        this::closingTransports,
        this::stalledConnections,
        ownedWorkers);
  }

  /**
   * Opens a Server-Sent Events stream on this request for an already authenticated actor. Every
   * refusal is raised before the response is committed; on success the response is asynchronous,
   * this method returns at once and {@code ready} is the stream's first frame.
   */
  public void subscribe(
      LiveActor actor,
      LiveRevisionSource source,
      UUID resourceId,
      HttpServletRequest request,
      HttpServletResponse response) {
    open(
        actor,
        source,
        resourceId,
        () ->
            SseLiveChannel.start(
                request, response, objectMapper, clock, properties.emitterTimeout()));
  }

  /** Connections registered on this instance (open, or opening after their first read). */
  public int activeConnections() {
    return registry.size();
  }

  /** Responses this instance turned into streams whose end the container has not reported. */
  public int openTransports() {
    return transports.size();
  }

  /** Of those, the ones already closed by the channel and still ending in the container. */
  public int closingTransports() {
    return (int) transports.stream().filter(LiveConnection::isClosed).count();
  }

  /** Transports, open or closing, whose client is not taking written bytes right now. */
  public int stalledConnections() {
    return (int)
        transports.stream()
            .filter(connection -> connection.channel().stalledSince() != null)
            .count();
  }

  /** Checks waiting for a worker (0 when the pool is not this service's own). */
  public int queuedChecks() {
    return ownedWorkers == null ? 0 : ownedWorkers.getQueue().size();
  }

  /** Opens a transport for a connection that passed every check; it commits the response. */
  @FunctionalInterface
  interface ChannelOpener {
    LiveChannel open() throws IOException;
  }

  UUID open(LiveActor actor, LiveRevisionSource source, UUID resourceId, ChannelOpener opener) {
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(resourceId, "resourceId");
    Objects.requireNonNull(opener, "opener");
    if (!properties.isEnabled()) {
      throw refused(Rejection.FEATURE_DISABLED);
    }
    if (!clock.instant().isBefore(actor.expiresAt())) {
      throw refused(Rejection.UNAUTHENTICATED);
    }
    LiveConnectionRegistry.Slot slot =
        registry
            .reserve(actor.tenantId(), actor.userId())
            .orElseThrow(() -> refused(Rejection.CAPACITY));

    // Phase 1: everything that may still refuse the request with a normal HTTP error.
    LiveReadResult.Visible baseline;
    Instant now;
    LiveChannel channel;
    try {
      LiveReadResult initial = read(actor, source, resourceId);
      baseline =
          switch (initial) {
            case LiveReadResult.Visible visible -> visible;
            case LiveReadResult.Hidden hidden ->
                throw rejected(
                    hidden == LiveReadResult.Hidden.FORBIDDEN
                        ? Rejection.FORBIDDEN
                        : Rejection.NOT_FOUND);
          };
      // A slow first read must not outlive the token or the switch (§4.7): judged after it.
      now = clock.instant();
      if (!properties.isEnabled()) {
        throw rejected(Rejection.FEATURE_DISABLED);
      }
      if (!now.isBefore(actor.expiresAt())) {
        throw rejected(Rejection.UNAUTHENTICATED);
      }
      channel = opener.open();
    } catch (LiveStreamRejectedException rejection) {
      slot.release();
      metrics.rejected(rejection.rejection().name());
      throw rejection;
    } catch (IOException | RuntimeException failure) {
      slot.release();
      metrics.rejected(Rejection.UNAVAILABLE.name());
      log.warn(
          "Live stream could not open for {}: {}",
          source.resourceType(),
          failure.getClass().getSimpleName());
      throw rejected(Rejection.UNAVAILABLE);
    }

    // Phase 2: the response is committed. Nothing is thrown any more; a failure ends the stream.
    LiveConnection connection =
        new LiveConnection(
            UUID.randomUUID(),
            actor,
            new LiveResource(source.resourceType(), resourceId),
            source,
            channel,
            slot,
            now.plus(properties.getMaxConnectionLifetime()));
    transports.add(connection);
    registry.register(connection);
    channel.listen(new TransportListener(connection));
    if (connection.isClosed()) {
      completeIfClosed(connection);
      return connection.id();
    }
    if (!send(
        connection,
        new LiveFrame.Ready(
            new LiveReadyDto(
                connection.id(),
                resourceId,
                baseline.revision().value(),
                LiveConnection.presenceValue(baseline),
                LiveConnection.leaseValue(baseline))))) {
      completeIfClosed(connection);
      return connection.id();
    }
    if (!connection.opened(baseline, now, now.plus(properties.getPollInterval()))) {
      // Closed between the ready frame and now: whoever closed it left the stream to us.
      completeIfClosed(connection);
    }
    metrics.opened(source.resourceType());
    return connection.id();
  }

  /**
   * One scheduler pass: aborts every transport, open or closing, whose client has not taken bytes
   * for longer than the send timeout, and hands every due open connection to a worker unless it
   * already has a job. It never reads the database and never waits on a socket.
   */
  public void tick() {
    Instant now = clock.instant();
    Duration sendTimeout = properties.getSendTimeout();
    for (LiveConnection connection : transports) {
      Instant stalled = connection.channel().stalledSince();
      if (stalled != null && !now.isBefore(stalled.plus(sendTimeout))) {
        // An open connection is closed as a slow consumer; a closing one keeps its reason.
        terminate(connection, LiveCloseReason.SLOW_CONSUMER.name());
        connection.markCompleted();
        abort(connection);
      }
    }
    boolean enabled = properties.isEnabled();
    for (LiveConnection connection : registry.snapshot()) {
      if (connection.isOpen() && (!enabled || connection.isDue(now))) {
        schedule(connection);
      }
    }
  }

  /**
   * Closes every connection with the reason: the closed frame is written where the client takes it,
   * and every stream is ended normally; a client that does not take the rest is aborted by the
   * scheduler after the send timeout. Never waits on a client.
   */
  public void closeAll(LiveCloseReason reason) {
    for (LiveConnection connection : registry.snapshot()) {
      connection.requestClose(reason);
      if (!connection.tryAcquire()) {
        // Opening, or a job is running: that job (or the next tick) writes the closed frame.
        continue;
      }
      try {
        workers.execute(() -> checkJob(connection));
      } catch (RejectedExecutionException full) {
        connection.abandonJob();
        terminate(connection, reason.name());
        completeIfClosed(connection);
      }
    }
  }

  /** Ends every stream and stops the workers; safe to call more than once. */
  public void shutdown() {
    closeAll(LiveCloseReason.RECONNECT_REQUIRED);
    if (ownedWorkers != null) {
      ownedWorkers.shutdown();
      try {
        if (!ownedWorkers.awaitTermination(
            properties.getSendTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
          ownedWorkers.shutdownNow();
        }
      } catch (InterruptedException interrupted) {
        ownedWorkers.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
    for (LiveConnection connection : transports) {
      terminate(connection, LiveCloseReason.RECONNECT_REQUIRED.name());
      connection.markCompleted();
      // The container is stopping: nothing more is waited for, and the capacity goes with it.
      abort(connection);
      connection.slot().release();
    }
  }

  // ── jobs ────────────────────────────────────────────────────────────────

  private void schedule(LiveConnection connection) {
    if (!connection.tryAcquire()) {
      return;
    }
    try {
      workers.execute(() -> checkJob(connection));
    } catch (RejectedExecutionException full) {
      connection.abandonJob();
      metrics.workerRejected();
    }
  }

  void checkJob(LiveConnection connection) {
    try {
      check(connection);
    } catch (RuntimeException unexpected) {
      log.warn(
          "Live check failed for {}: {}",
          connection.resource().type(),
          unexpected.getClass().getSimpleName());
      close(connection, LiveCloseReason.TEMPORARILY_UNAVAILABLE);
    } finally {
      LiveCloseReason pending = connection.pendingClose();
      if (pending != null && connection.isOpen()) {
        // A close was asked while this job ran; it is written now, before the job lets go.
        close(connection, pending);
      }
      connection.finishJob(clock.instant().plus(properties.getPollInterval()));
      completeIfClosed(connection);
    }
  }

  /**
   * One check (CEDIT-05 §5): gates, a fresh read in a new transaction, gates again right before the
   * write, then at most one frame. A heartbeat is written only after a successful fresh check.
   */
  private void check(LiveConnection connection) {
    if (!connection.isOpen()) {
      return;
    }
    LiveCloseReason gate = gate(connection, clock.instant());
    if (gate != null) {
      close(connection, gate);
      return;
    }
    LiveReadResult current;
    long started = System.nanoTime();
    try {
      current = read(connection.actor(), connection.source(), connection.resource().id());
      metrics.checked(
          connection.resource().type(), Duration.ofNanos(System.nanoTime() - started), false);
    } catch (RuntimeException failure) {
      metrics.checked(
          connection.resource().type(), Duration.ofNanos(System.nanoTime() - started), true);
      log.debug(
          "Live revision read failed for {}: {}",
          connection.resource().type(),
          failure.getClass().getSimpleName());
      close(connection, LiveCloseReason.TEMPORARILY_UNAVAILABLE);
      return;
    }
    Instant now = clock.instant();
    if (!connection.isOpen()) {
      return;
    }
    gate = gate(connection, now);
    if (gate != null) {
      close(connection, gate);
      return;
    }
    switch (current) {
      case LiveReadResult.Hidden hidden -> close(connection, LiveCloseReason.ACCESS_REVOKED);
      case LiveReadResult.Visible visible -> {
        if (connection.differsFrom(visible)) {
          LiveFrame frame =
              new LiveFrame.Invalidated(
                  new LiveInvalidatedDto(
                      connection.id(),
                      connection.resource().id(),
                      visible.revision().value(),
                      LiveConnection.presenceValue(visible),
                      LiveConnection.leaseValue(visible)));
          if (send(connection, frame)) {
            connection.sent(visible, now);
          }
        } else if (!now.isBefore(connection.lastSentAt().plus(properties.getHeartbeatInterval()))) {
          if (send(connection, LiveFrame.Heartbeat.INSTANCE)) {
            connection.heartbeatSent(now);
          }
        }
      }
    }
  }

  /**
   * The reason the connection must end now, or null: an asked close, the switch, the token and the
   * lifetime, in that order.
   */
  private LiveCloseReason gate(LiveConnection connection, Instant now) {
    if (connection.pendingClose() != null) {
      return connection.pendingClose();
    }
    if (!properties.isEnabled()) {
      return LiveCloseReason.FEATURE_DISABLED;
    }
    if (!now.isBefore(connection.actor().expiresAt())) {
      return LiveCloseReason.AUTH_EXPIRED;
    }
    if (!now.isBefore(connection.lifetimeEnd())) {
      return LiveCloseReason.RECONNECT_REQUIRED;
    }
    return null;
  }

  /** The tenant's access first (R2), then the source; one transaction as the actor. */
  private LiveReadResult read(LiveActor actor, LiveRevisionSource source, UUID resourceId) {
    LiveReadResult result =
        tenantScope.read(
            actor,
            () ->
                tenantAccess.currentTenantHasAccess()
                    ? source.read(actor, resourceId)
                    : LiveReadResult.Hidden.FORBIDDEN);
    if (result == null) {
      throw new IllegalStateException("A live revision source answered nothing");
    }
    return result;
  }

  // ── writing and ending ──────────────────────────────────────────────────

  /** Hands one frame of an open connection to its transport; a failed transport ends it. */
  private boolean send(LiveConnection connection, LiveFrame frame) {
    try {
      connection.channel().send(frame);
      return true;
    } catch (IOException | RuntimeException failure) {
      terminate(connection, "TRANSPORT_ERROR");
      return false;
    }
  }

  /** Ends the connection with a closed frame; the stream itself is completed by the job's end. */
  private void close(LiveConnection connection, LiveCloseReason reason) {
    if (!terminate(connection, reason.name())) {
      return;
    }
    try {
      connection.channel().send(new LiveFrame.Closed(new LiveClosedDto(connection.id(), reason)));
    } catch (IOException | RuntimeException unreachable) {
      log.debug("Closed frame not delivered: {}", unreachable.getClass().getSimpleName());
    }
  }

  /** The single transition to closed: no more frames and no more jobs for this connection. */
  private boolean terminate(LiveConnection connection, String cause) {
    if (!connection.markClosed(cause)) {
      return false;
    }
    registry.remove(connection);
    metrics.closed(cause);
    return true;
  }

  private void completeIfClosed(LiveConnection connection) {
    if (connection.isClosed() && connection.markCompleted()) {
      connection.channel().complete();
    }
  }

  /** Ends the transport at once (R1): unsent bytes the container still holds are dropped. */
  private void abort(LiveConnection connection) {
    if (connection.markAborted()) {
      metrics.transportAborted();
      connection.channel().abort();
    }
  }

  /** The container reported the transport's end: the connection is over, its capacity returns. */
  private void transportEnded(LiveConnection connection, String cause) {
    terminate(connection, cause);
    connection.markCompleted();
    if (connection.markTransportEnded()) {
      transports.remove(connection);
      connection.slot().release();
    }
  }

  private static LiveStreamRejectedException rejected(Rejection rejection) {
    return new LiveStreamRejectedException(rejection);
  }

  /** A refusal decided before anything was reserved; counted here. */
  private LiveStreamRejectedException refused(Rejection rejection) {
    metrics.rejected(rejection.name());
    return rejected(rejection);
  }

  /** The transport's own end: idempotent, and bound to this connection only (§5, L16). */
  private final class TransportListener implements LiveChannel.Listener {

    private final LiveConnection connection;

    private TransportListener(LiveConnection connection) {
      this.connection = connection;
    }

    @Override
    public void completed() {
      transportEnded(connection, "CLIENT_GONE");
    }

    @Override
    public void timedOut() {
      // Not the end yet: the transport ends itself and reports completed or failed; the slot
      // stays taken until then.
      metrics.transportTimedOut();
      terminate(connection, "TRANSPORT_TIMEOUT");
      connection.markCompleted();
    }

    @Override
    public void failed(Throwable failure) {
      transportEnded(connection, "TRANSPORT_ERROR");
    }
  }

  private static ThreadPoolExecutor newWorkerPool(LiveStreamProperties properties) {
    AtomicInteger sequence = new AtomicInteger();
    ThreadFactory threads =
        runnable -> {
          Thread thread = new Thread(runnable, "live-stream-" + sequence.incrementAndGet());
          thread.setDaemon(true);
          return thread;
        };
    return new ThreadPoolExecutor(
        properties.getWorkerCount(),
        properties.getWorkerCount(),
        60,
        TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(properties.getWorkerQueueCapacity()),
        threads,
        new ThreadPoolExecutor.AbortPolicy());
  }
}
