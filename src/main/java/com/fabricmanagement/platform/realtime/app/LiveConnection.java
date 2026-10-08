package com.fabricmanagement.platform.realtime.app;

import com.fabricmanagement.platform.realtime.domain.LiveActor;
import com.fabricmanagement.platform.realtime.domain.LiveResource;
import com.fabricmanagement.platform.realtime.domain.LiveRevision;
import com.fabricmanagement.platform.realtime.domain.LiveRevisionSource;
import com.fabricmanagement.platform.realtime.dto.LiveCloseReason;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One live connection's state. The opening request holds it until the ready frame is written; after
 * that at most one worker job holds it at a time ({@link #tryAcquire()}), so checks never overlap
 * and an older read is never written after a newer one. Closing is a single transition; whoever
 * makes it stops all further frames. The capacity is given back when the transport has ended.
 */
final class LiveConnection {

  enum State {
    OPENING,
    OPEN,
    CLOSED
  }

  private final UUID id;
  private final LiveActor actor;
  private final LiveResource resource;
  private final LiveRevisionSource source;
  private final LiveChannel channel;
  private final LiveConnectionRegistry.Slot slot;
  private final Instant lifetimeEnd;

  private final AtomicReference<State> state = new AtomicReference<>(State.OPENING);
  private final AtomicBoolean busy = new AtomicBoolean(true);
  private final AtomicBoolean completed = new AtomicBoolean();
  private final AtomicBoolean transportEnded = new AtomicBoolean();
  private final AtomicBoolean aborted = new AtomicBoolean();

  private volatile String lastSentRevision;
  private volatile Instant lastSentAt;
  private volatile Instant nextCheckAt;
  private volatile String closeCause;
  private volatile LiveCloseReason pendingClose;

  LiveConnection(
      UUID id,
      LiveActor actor,
      LiveResource resource,
      LiveRevisionSource source,
      LiveChannel channel,
      LiveConnectionRegistry.Slot slot,
      Instant lifetimeEnd) {
    this.id = id;
    this.actor = actor;
    this.resource = resource;
    this.source = source;
    this.channel = channel;
    this.slot = slot;
    this.lifetimeEnd = lifetimeEnd;
  }

  UUID id() {
    return id;
  }

  LiveActor actor() {
    return actor;
  }

  LiveResource resource() {
    return resource;
  }

  LiveRevisionSource source() {
    return source;
  }

  LiveChannel channel() {
    return channel;
  }

  LiveConnectionRegistry.Slot slot() {
    return slot;
  }

  Instant lifetimeEnd() {
    return lifetimeEnd;
  }

  String lastSentRevision() {
    return lastSentRevision;
  }

  Instant lastSentAt() {
    return lastSentAt;
  }

  String closeCause() {
    return closeCause;
  }

  /** The ready frame was written: the connection becomes open and the opener lets it go. */
  boolean opened(LiveRevision baseline, Instant at, Instant firstCheckAt) {
    lastSentRevision = baseline.value();
    lastSentAt = at;
    nextCheckAt = firstCheckAt;
    boolean open = state.compareAndSet(State.OPENING, State.OPEN);
    busy.set(false);
    return open;
  }

  boolean isOpen() {
    return state.get() == State.OPEN;
  }

  boolean isClosed() {
    return state.get() == State.CLOSED;
  }

  /** Makes the one transition to closed; true only for the caller that made it. */
  boolean markClosed(String cause) {
    State previous = state.getAndSet(State.CLOSED);
    if (previous == State.CLOSED) {
      return false;
    }
    closeCause = cause;
    return true;
  }

  /** Takes the connection for one job; false while another job (or the opener) holds it. */
  boolean tryAcquire() {
    return busy.compareAndSet(false, true);
  }

  /** The job ended; the next check is due at the given time. */
  void finishJob(Instant next) {
    nextCheckAt = next;
    busy.set(false);
  }

  /** The job never ran (the worker queue was full); the check stays due. */
  void abandonJob() {
    busy.set(false);
  }

  boolean isBusy() {
    return busy.get();
  }

  /** Asks the connection to close with this reason on its next job; the first request wins. */
  void requestClose(LiveCloseReason reason) {
    if (pendingClose == null) {
      pendingClose = reason;
    }
  }

  LiveCloseReason pendingClose() {
    return pendingClose;
  }

  /** Whether a check should run now: its turn came, a deadline passed, or a close was asked. */
  boolean isDue(Instant now) {
    Instant next = nextCheckAt;
    return pendingClose != null
        || (next != null && !now.isBefore(next))
        || !now.isBefore(lifetimeEnd)
        || !now.isBefore(actor.expiresAt());
  }

  void sent(LiveRevision revision, Instant at) {
    lastSentRevision = revision.value();
    lastSentAt = at;
  }

  void heartbeatSent(Instant at) {
    lastSentAt = at;
  }

  /** The stream is ended exactly once on the transport. */
  boolean markCompleted() {
    return completed.compareAndSet(false, true);
  }

  /** The container reported the transport's end; true only for the first report. */
  boolean markTransportEnded() {
    return transportEnded.compareAndSet(false, true);
  }

  /** The transport is aborted exactly once (R1). */
  boolean markAborted() {
    return aborted.compareAndSet(false, true);
  }
}
