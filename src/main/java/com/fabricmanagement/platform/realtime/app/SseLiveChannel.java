package com.fabricmanagement.platform.realtime.app;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletResponseWrapper;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-Sent Events over the Servlet container's non-blocking output (CEDIT-05 R1).
 *
 * <p>The response is switched to asynchronous, non-blocking mode: a write only happens when the
 * container says the client can take more ({@code isReady()}), so neither a worker nor the
 * scheduler can be held by a client that stopped reading. What the client cannot take yet waits
 * here, coalesced: a newer {@code invalidated} replaces an older one and a keepalive is dropped
 * when anything else is waiting. When the container cannot write, the channel records since when.
 *
 * <p>Ending never makes the container wait on the client either. The response carries {@code
 * Connection: close}, so its body ends with the connection and no chunked trailer is left to write.
 * A normal end ({@link #complete()}) completes the response only once every frame is written and
 * the container holds nothing unsent; until then the transport stays stalled and the service aborts
 * it after the send timeout. An abort with unsent bytes in the container closes the connection
 * without flushing them ({@link ConnectionAbort}); without unsent bytes it is a normal completion.
 *
 * <p>Data frames carry the standard {@link ApiResponse} envelope as compact JSON written by the
 * application's object mapper; the keepalive is an SSE comment; no {@code id:} line is written.
 * Exactly one thread touches the output at a time; the container's callbacks and the workers hand
 * over through a flag, never through a lock held while calling the container.
 */
final class SseLiveChannel implements LiveChannel {

  static final String READY = "ready";
  static final String INVALIDATED = "invalidated";
  static final String CLOSED = "closed";

  private static final byte[] KEEPALIVE = ": keepalive\n\n".getBytes(StandardCharsets.UTF_8);
  private static final int MAX_WAITING = 4;

  /** Closes the connection at once, dropping what the container still holds unsent. */
  @FunctionalInterface
  interface ConnectionAbort {
    /** False when the container offers no such close; the caller then completes normally. */
    boolean closeNow(IOException cause);
  }

  private final AsyncContext async;
  private final ServletOutputStream out;
  private final ObjectMapper json;
  private final Clock clock;
  private final ConnectionAbort connectionAbort;

  private final Deque<LiveFrame> waiting = new ArrayDeque<>();
  private final AtomicBoolean writing = new AtomicBoolean();

  /** Signalled whenever the output flag is let go; the container's timeout waits on it. */
  private final Object released = new Object();

  private final AtomicInteger wakeups = new AtomicInteger();
  private final AtomicBoolean completeRequested = new AtomicBoolean();
  private final AtomicBoolean abortRequested = new AtomicBoolean();

  /** The end was carried out (normal completion or abort); nothing is written any more. */
  private final AtomicBoolean finished = new AtomicBoolean();

  /** The container reported the final end. */
  private final AtomicBoolean ended = new AtomicBoolean();

  /** {@code AsyncContext.complete()} was asked for; it is asked for once. */
  private final AtomicBoolean completionAsked = new AtomicBoolean();

  private volatile boolean containerTimedOut;
  private volatile Instant stalledSince;
  private volatile Throwable failure;
  private volatile Listener listener;
  private volatile boolean timeoutReported;
  private volatile EndKind endKind;
  private volatile Throwable endFailure;

  private enum EndKind {
    COMPLETED,
    FAILED
  }

  SseLiveChannel(
      AsyncContext async,
      ServletOutputStream out,
      ObjectMapper json,
      Clock clock,
      ConnectionAbort connectionAbort) {
    this.async = async;
    this.out = out;
    this.json = json;
    this.clock = clock;
    this.connectionAbort = connectionAbort;
  }

  /**
   * Commits the 200 response with the stream headers, switches it to asynchronous, non-blocking
   * output and returns the channel. Called only after every refusal has been decided.
   */
  static SseLiveChannel start(
      HttpServletRequest request,
      HttpServletResponse response,
      ObjectMapper json,
      Clock clock,
      Duration asyncTimeout)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("text/event-stream");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("X-Accel-Buffering", "no");
    // The body ends with the connection: nothing (no chunked trailer) is left to write at the end.
    response.setHeader("Connection", "close");
    AsyncContext async = request.startAsync(request, response);
    async.setTimeout(asyncTimeout.toMillis());
    SseLiveChannel channel;
    try {
      // Headers leave now (a few hundred bytes, still in blocking mode); frames follow
      // non-blocking.
      response.flushBuffer();
      HttpServletResponse container = innermost(response);
      channel =
          new SseLiveChannel(
              async,
              container.getOutputStream(),
              json,
              clock,
              cause -> TomcatConnectionAbort.closeNow(container, cause));
    } catch (IOException | RuntimeException failure) {
      async.complete();
      throw failure;
    }
    channel.register();
    return channel;
  }

  /** Subscribes to the container's notifications; from now on the container may call back. */
  void register() {
    async.addListener(new ContainerEvents());
    out.setWriteListener(new WritePossible());
  }

  @Override
  public void send(LiveFrame frame) throws IOException {
    if (completeRequested.get() || abortRequested.get() || ended.get()) {
      throw new IOException("The stream has ended");
    }
    synchronized (waiting) {
      enqueue(frame);
    }
    drain();
    Throwable failed = failure;
    if (failed != null) {
      throw new IOException("The stream could not be written", failed);
    }
  }

  @Override
  public Instant stalledSince() {
    return finished.get() || ended.get() ? null : stalledSince;
  }

  @Override
  public void complete() {
    if (completeRequested.compareAndSet(false, true)) {
      drain();
    }
  }

  @Override
  public void abort() {
    if (abortRequested.compareAndSet(false, true)) {
      drain();
    }
  }

  @Override
  public void listen(Listener listener) {
    this.listener = listener;
    if (containerTimedOut) {
      reportTimeout(listener);
    }
    if (ended.get()) {
      report(listener);
    }
  }

  // ── writing ─────────────────────────────────────────────────────────────

  /** Coalesces so that a stalled client never makes this buffer grow. */
  private void enqueue(LiveFrame frame) {
    if (frame instanceof LiveFrame.Heartbeat && !waiting.isEmpty()) {
      return; // Something else is already waiting; the keepalive adds nothing.
    }
    if (frame instanceof LiveFrame.Invalidated) {
      waiting.removeIf(LiveFrame.Invalidated.class::isInstance);
    }
    if (waiting.size() >= MAX_WAITING) {
      waiting.removeIf(LiveFrame.Heartbeat.class::isInstance);
    }
    if (waiting.size() < MAX_WAITING || frame instanceof LiveFrame.Closed) {
      waiting.addLast(frame);
    }
  }

  /**
   * Whoever holds the flag works on the output; a thread that finds it taken leaves its request (a
   * frame, a completion, an abort) behind, and the holder looks again before it lets go. A
   * write-possible callback that arrived meanwhile is noticed through the wake-up count.
   */
  private void drain() {
    while (true) {
      int seen = wakeups.get();
      if (!writing.compareAndSet(false, true)) {
        return;
      }
      try {
        step();
      } finally {
        release();
      }
      if (wakeups.get() == seen && !workLeft()) {
        return;
      }
    }
  }

  /** One pass with the flag held. */
  private void step() {
    if (ended.get() || finished.get()) {
      clearWaiting();
      return;
    }
    if (abortRequested.get()) {
      if (!containerTimedOut) {
        endNow();
      }
      // After the container's timeout only its own callback ends the stream (see onTimeout).
      return;
    }
    if (failure != null) {
      // The socket is broken: completing writes nothing more.
      finishNormally();
      return;
    }
    try {
      boolean flushed = writeWhileReady();
      if (flushed && completeRequested.get()) {
        finishNormally();
      }
    } catch (IOException | RuntimeException writeFailure) {
      failure = writeFailure;
      finishNormally();
    }
  }

  private boolean workLeft() {
    if (ended.get() || finished.get()) {
      return false;
    }
    if (abortRequested.get()) {
      return !containerTimedOut;
    }
    if (failure != null) {
      return true;
    }
    if (stalledSince != null) {
      return false; // The container calls back when the client takes bytes again.
    }
    synchronized (waiting) {
      return !waiting.isEmpty() || completeRequested.get();
    }
  }

  /**
   * Writes waiting frames while the container takes them, then flushes. True when every frame is
   * written and the container holds nothing unsent.
   */
  private boolean writeWhileReady() throws IOException {
    while (true) {
      if (!out.isReady()) {
        markStalled();
        return false;
      }
      LiveFrame next;
      synchronized (waiting) {
        next = waiting.pollFirst();
      }
      if (next != null) {
        out.write(encode(next));
        continue;
      }
      out.flush();
      if (out.isReady()) {
        stalledSince = null;
        return true;
      }
      markStalled();
      return false;
    }
  }

  private void markStalled() {
    if (stalledSince == null) {
      stalledSince = clock.instant();
    }
  }

  /** With the flag held: nothing unsent stays in the container, so completing writes nothing. */
  private void finishNormally() {
    if (!finished.compareAndSet(false, true)) {
      return;
    }
    clearWaiting();
    completeQuietly();
  }

  /**
   * With the flag held: ends at once. Unsent bytes in the container are dropped with the
   * connection; the container then reports the error and {@link ContainerEvents#onError} completes.
   * Without unsent bytes, or where the container cannot close that way, it is a normal completion.
   * Within the container's own timeout callback the completion is ours to make.
   */
  private void endNow() {
    if (!finished.compareAndSet(false, true)) {
      return;
    }
    clearWaiting();
    boolean closed = false;
    if (stalledSince != null) {
      try {
        closed =
            connectionAbort.closeNow(
                new IOException("Live stream aborted: the client does not take the stream"));
      } catch (RuntimeException unsupported) {
        closed = false;
      }
    }
    if (!closed || containerTimedOut) {
      completeQuietly();
    }
  }

  private void release() {
    writing.set(false);
    synchronized (released) {
      released.notifyAll();
    }
  }

  private void completeQuietly() {
    if (!completionAsked.compareAndSet(false, true)) {
      return;
    }
    try {
      async.complete();
    } catch (IllegalStateException alreadyEnded) {
      // The container already ended the request (client gone, error or timeout).
    }
  }

  private void clearWaiting() {
    synchronized (waiting) {
      waiting.clear();
    }
  }

  private byte[] encode(LiveFrame frame) throws IOException {
    return switch (frame) {
      case LiveFrame.Ready ready -> event(READY, ApiResponse.success(ready.data()));
      case LiveFrame.Invalidated invalidated ->
          event(INVALIDATED, ApiResponse.success(invalidated.data()));
      case LiveFrame.Closed closed -> event(CLOSED, ApiResponse.success(closed.data()));
      case LiveFrame.Heartbeat heartbeat -> KEEPALIVE;
    };
  }

  private byte[] event(String name, ApiResponse<?> body) throws IOException {
    // Compact JSON has no line breaks (line breaks inside strings are escaped), so one data line.
    String data = json.writeValueAsString(body);
    return ("event:" + name + "\ndata:" + data + "\n\n").getBytes(StandardCharsets.UTF_8);
  }

  // ── container notifications ─────────────────────────────────────────────

  private void ended(EndKind kind, Throwable cause) {
    if (!ended.compareAndSet(false, true)) {
      return;
    }
    endKind = kind;
    endFailure = cause;
    clearWaiting();
    Listener current = listener;
    if (current != null) {
      report(current);
    }
  }

  private void report(Listener target) {
    switch (endKind) {
      case COMPLETED -> target.completed();
      case FAILED -> target.failed(endFailure);
    }
  }

  private void reportTimeout(Listener target) {
    synchronized (this) {
      if (timeoutReported) {
        return;
      }
      timeoutReported = true;
    }
    target.timedOut();
  }

  private static HttpServletResponse innermost(HttpServletResponse response) {
    HttpServletResponse current = response;
    while (current instanceof ServletResponseWrapper wrapper
        && wrapper.getResponse() instanceof HttpServletResponse inner) {
      current = inner;
    }
    return current;
  }

  private final class WritePossible implements WriteListener {

    @Override
    public void onWritePossible() {
      wakeups.incrementAndGet();
      stalledSince = null;
      drain();
    }

    @Override
    public void onError(Throwable error) {
      failure = error;
      ended(EndKind.FAILED, error);
      completeQuietly();
    }
  }

  private final class ContainerEvents implements AsyncListener {

    @Override
    public void onComplete(AsyncEvent event) {
      ended(EndKind.COMPLETED, null);
    }

    @Override
    public void onTimeout(AsyncEvent event) {
      // Set before the abort request: a worker that sees the request also sees that the end is
      // this callback's to make, and leaves it alone.
      containerTimedOut = true;
      abortRequested.set(true);
      Listener current = listener;
      if (current != null) {
        reportTimeout(current);
      }
      // The end is made here, by the one thread that owns the output, and within this callback as
      // the container expects. A worker holding the output only makes non-blocking calls and
      // leaves at once; this waits for that hand-over, never for the client, and never completes
      // the response while another thread may still be writing to it.
      boolean interrupted = false;
      while (!ended.get()) {
        if (writing.compareAndSet(false, true)) {
          try {
            if (ended.get()) {
              break;
            }
            if (!finished.get()) {
              endNow();
            } else {
              // Ended earlier under the flag: by an abort (unsent bytes dropped, I/O closed) or
              // a normal end (nothing unsent). Completing writes nothing; once only.
              completeQuietly();
            }
          } finally {
            release();
          }
          break;
        }
        interrupted |= awaitRelease();
      }
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }

    /** Waits briefly for the output flag to be let go; true when interrupted meanwhile. */
    private boolean awaitRelease() {
      synchronized (released) {
        if (writing.get()) {
          try {
            released.wait(10);
          } catch (InterruptedException interrupted) {
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public void onError(AsyncEvent event) {
      failure = event.getThrowable();
      ended(EndKind.FAILED, event.getThrowable());
      // Completing here keeps the container from an error dispatch on a committed stream.
      completeQuietly();
    }

    @Override
    public void onStartAsync(AsyncEvent event) {
      // Not restarted.
    }
  }
}
