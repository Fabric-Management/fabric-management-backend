package com.fabricmanagement.platform.realtime.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.realtime.app.LiveTestDoubles.TestClock;
import com.fabricmanagement.platform.realtime.dto.LiveCloseReason;
import com.fabricmanagement.platform.realtime.dto.LiveClosedDto;
import com.fabricmanagement.platform.realtime.dto.LiveInvalidatedDto;
import com.fabricmanagement.platform.realtime.dto.LiveReadyDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The SSE transport against a scripted servlet container (CEDIT-05 R1): what is written, when the
 * response is completed, and when the connection is closed without a final flush. The container is
 * a mock async context and an output stream whose readiness the test sets; Tomcat itself is
 * exercised in {@code SalesOrderLiveTransportIT}.
 */
class SseLiveChannelTest {

  private static final Instant START = Instant.parse("2026-10-07T09:00:00Z");

  private final UUID connectionId = UUID.randomUUID();
  private final UUID resourceId = UUID.randomUUID();
  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  private final TestClock clock = new TestClock(START);

  private AsyncContext async;
  private ScriptedOutput out;
  private RecordingAbort closeNow;
  private SseLiveChannel channel;
  private AsyncListener container;
  private RecordingListener events;

  @BeforeEach
  void setUp() {
    async = mock(AsyncContext.class);
    out = new ScriptedOutput();
    closeNow = new RecordingAbort(true);
    channel = new SseLiveChannel(async, out, json, clock, closeNow);
    channel.register();
    ArgumentCaptor<AsyncListener> listener = ArgumentCaptor.forClass(AsyncListener.class);
    verify(async).addListener(listener.capture());
    container = listener.getValue();
    events = new RecordingListener();
    channel.listen(events);
  }

  @Test
  @DisplayName("R1: the stream's body ends with its connection, so ending writes nothing more")
  void startCommitsTheStreamHeaders() throws IOException {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    AsyncContext started = mock(AsyncContext.class);
    when(request.startAsync(request, response)).thenReturn(started);
    when(response.getOutputStream()).thenReturn(new ScriptedOutput());

    SseLiveChannel.start(request, response, json, clock, Duration.ofSeconds(90));

    verify(response).setStatus(200);
    verify(response).setContentType("text/event-stream");
    verify(response).setHeader("Cache-Control", "no-store");
    verify(response).setHeader("X-Accel-Buffering", "no");
    verify(response).setHeader("Connection", "close");
    verify(started).setTimeout(90_000L);
    verify(response).flushBuffer();
    verify(started).addListener(any(AsyncListener.class));
  }

  @Test
  @DisplayName("R1: a failing start completes the async request and reports the failure")
  void failingStartCompletes() throws IOException {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    AsyncContext started = mock(AsyncContext.class);
    when(request.startAsync(request, response)).thenReturn(started);
    doThrow(new IOException("client gone")).when(response).flushBuffer();

    assertThatThrownBy(
            () -> SseLiveChannel.start(request, response, json, clock, Duration.ofSeconds(90)))
        .isInstanceOf(IOException.class);
    verify(started).complete();
  }

  @Test
  @DisplayName(
      "R1: frames are written in the SSE shape; a normal end completes once, nothing aborted")
  void framesAndNormalEnd() throws IOException {
    channel.send(ready("1"));
    channel.send(LiveFrame.Heartbeat.INSTANCE);
    channel.send(closed(LiveCloseReason.RECONNECT_REQUIRED));
    channel.complete();
    channel.complete();

    String written = out.text();
    assertThat(written).startsWith("event:ready\ndata:{");
    assertThat(written).contains("\n\n: keepalive\n\nevent:closed\ndata:{");
    assertThat(written).doesNotContain("\nid:");
    verify(async, times(1)).complete();
    assertThat(closeNow.calls).isZero();
    assertThat(channel.stalledSince()).isNull();
  }

  @Test
  @DisplayName(
      "R1: while the client does not take the stream, a normal end waits; it completes once the"
          + " container has written everything")
  void normalEndWaitsForTheClient() throws IOException {
    out.readyAfterFlush = false;
    channel.send(ready("1"));
    assertThat(channel.stalledSince()).isEqualTo(START);

    clock.advance(Duration.ofSeconds(1));
    channel.send(closed(LiveCloseReason.ACCESS_REVOKED));
    channel.complete();
    verify(async, never()).complete();
    assertThat(channel.stalledSince()).as("stalled since the first time").isEqualTo(START);

    out.ready = true;
    out.readyAfterFlush = true;
    out.listener.onWritePossible();

    assertThat(out.text()).contains("event:closed");
    verify(async, times(1)).complete();
    assertThat(closeNow.calls).isZero();
  }

  @Test
  @DisplayName(
      "R1: an abort with unsent bytes closes the connection without a flush; the container's error"
          + " then completes the request and ends the transport")
  void abortWithUnsentBytesClosesWithoutFlush() throws IOException {
    out.readyAfterFlush = false;
    channel.send(ready("1"));

    channel.abort();
    channel.abort();

    assertThat(closeNow.calls).isEqualTo(1);
    verify(async, never()).complete();
    assertThat(channel.stalledSince()).isNull();
    assertThatThrownBy(() -> channel.send(LiveFrame.Heartbeat.INSTANCE))
        .isInstanceOf(IOException.class);

    IOException closed = new IOException("closed now");
    container.onError(new AsyncEvent(async, closed));
    verify(async, times(1)).complete();
    assertThat(events.failed).containsExactly(closed);
    container.onComplete(new AsyncEvent(async));
    assertThat(events.completed.get()).as("one final end").isZero();
  }

  @Test
  @DisplayName("R1: an abort with nothing unsent is a normal completion, no connection close")
  void abortWithoutUnsentBytesCompletes() throws IOException {
    channel.send(ready("1"));

    channel.abort();

    assertThat(closeNow.calls).isZero();
    verify(async, times(1)).complete();
  }

  @Test
  @DisplayName(
      "R1: where the container cannot close a connection that way, an abort completes normally")
  void abortFallsBackToCompletion() throws IOException {
    closeNow = new RecordingAbort(false);
    AsyncContext otherAsync = mock(AsyncContext.class);
    ScriptedOutput otherOut = new ScriptedOutput();
    SseLiveChannel other = new SseLiveChannel(otherAsync, otherOut, json, clock, closeNow);
    other.register();
    otherOut.readyAfterFlush = false;
    other.send(ready("1"));

    other.abort();

    assertThat(closeNow.calls).isEqualTo(1);
    verify(otherAsync, times(1)).complete();
  }

  @Test
  @DisplayName(
      "R1: the container's timeout is reported, unsent bytes are dropped with the connection and"
          + " the request is completed within the callback")
  void containerTimeoutEndsTheStream() throws IOException {
    out.readyAfterFlush = false;
    channel.send(ready("1"));

    container.onTimeout(new AsyncEvent(async));

    assertThat(events.timeouts.get()).isEqualTo(1);
    assertThat(closeNow.calls).isEqualTo(1);
    verify(async, times(1)).complete();
    container.onComplete(new AsyncEvent(async));
    assertThat(events.completed.get()).isEqualTo(1);
    assertThat(events.failed).isEmpty();
  }

  @Test
  @DisplayName("R1: a broken write ends the stream with a completion that writes nothing more")
  void brokenWriteCompletes() {
    out.failure = new IOException("Broken pipe");

    assertThatThrownBy(() -> channel.send(ready("1"))).isInstanceOf(IOException.class);

    verify(async, times(1)).complete();
    assertThat(closeNow.calls).isZero();
  }

  @Test
  @DisplayName(
      "R1: while stalled, waiting frames coalesce: the newest invalidated replaces older ones and"
          + " keepalives are dropped")
  void waitingFramesCoalesce() throws IOException {
    out.ready = false;
    channel.send(invalidated("2"));
    channel.send(LiveFrame.Heartbeat.INSTANCE);
    channel.send(invalidated("3"));
    channel.send(invalidated("4"));
    assertThat(out.text()).isEmpty();

    out.ready = true;
    out.listener.onWritePossible();

    String written = out.text();
    assertThat(written.split("event:invalidated", -1)).hasSize(2);
    assertThat(written).contains("\"revision\":\"4\"").doesNotContain("keepalive");
  }

  @Test
  @DisplayName("P03 (CEDIT-06): the presence marker is written when present and omitted otherwise")
  void presenceMarkerIsWrittenOnlyWhenPresent() throws IOException {
    channel.send(
        new LiveFrame.Invalidated(
            new LiveInvalidatedDto(UUID.randomUUID(), UUID.randomUUID(), "5", "pabc")));
    String first = out.text();
    assertThat(first).contains("\"presenceRevision\":\"pabc\"");

    channel.send(invalidated("6"));
    String second = out.text().substring(first.length());
    assertThat(second).contains("\"revision\":\"6\"").doesNotContain("presenceRevision");
  }

  @Test
  @DisplayName("R1: a container error ends the transport once and completes the request")
  void containerErrorEndsOnce() throws IOException {
    IOException reset = new IOException("Connection reset");
    container.onError(new AsyncEvent(async, reset));
    container.onError(new AsyncEvent(async, reset));
    container.onComplete(new AsyncEvent(async));

    assertThat(events.failed).containsExactly(reset);
    assertThat(events.completed.get()).isZero();
    verify(async, times(1)).complete();
  }

  // ── R1 follow-up 2: the container's timeout against a writer in progress ──

  @Test
  @DisplayName(
      "R1: a container timeout while a worker is inside a write never completes under it, however"
          + " long the worker takes; once it lets go, unsent bytes are dropped with the connection"
          + " first, then the request is completed, and nothing is written after")
  void containerTimeoutWaitsForTheWriterWithUnsentBytes() throws Exception {
    Race race = new Race();
    race.out.readyAfterFlush = false;

    race.runInterrupted(() -> race.channel.send(ready("1")));

    assertThat(race.order).containsExactly("closeNow", "complete");
    assertThat(race.events.timeouts.get()).isEqualTo(1);
    assertThatThrownBy(() -> race.channel.send(LiveFrame.Heartbeat.INSTANCE))
        .isInstanceOf(IOException.class);
    race.container.onComplete(new AsyncEvent(race.async));
    assertThat(race.events.completed.get()).isEqualTo(1);
    assertThat(race.events.failed).isEmpty();
  }

  @Test
  @DisplayName(
      "R1: a container timeout while a worker is inside a write that empties the output completes"
          + " normally once the worker lets go, without closing the connection")
  void containerTimeoutWaitsForTheWriterWithNothingUnsent() throws Exception {
    Race race = new Race();
    race.out.readyAfterFlush = true;

    race.runInterrupted(() -> race.channel.send(ready("1")));

    assertThat(race.order).containsExactly("complete");
  }

  /**
   * A channel whose container records the order of {@code closeNow} and {@code complete}, and whose
   * output can hold a worker inside {@code flush()}.
   */
  private final class Race {
    final List<String> order = new CopyOnWriteArrayList<>();
    final AsyncContext async = mock(AsyncContext.class);
    final ScriptedOutput out = new ScriptedOutput();
    final SseLiveChannel channel;
    final AsyncListener container;
    final RecordingListener events = new RecordingListener();

    Race() {
      doAnswer(
              invocation -> {
                order.add("complete");
                return null;
              })
          .when(async)
          .complete();
      channel =
          new SseLiveChannel(
              async,
              out,
              json,
              clock,
              cause -> {
                order.add("closeNow");
                return true;
              });
      channel.register();
      ArgumentCaptor<AsyncListener> captured = ArgumentCaptor.forClass(AsyncListener.class);
      verify(async).addListener(captured.capture());
      container = captured.getValue();
      channel.listen(events);
    }

    /**
     * Runs the write on its own thread and holds it inside flush(); fires the container's timeout
     * on another thread; checks that well past the former 100 ms hand-over nothing has ended the
     * response; then lets the writer go and waits for both.
     */
    void runInterrupted(ThrowingStep write) throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      out.flushEntered = new CountDownLatch(1);
      out.flushRelease = release; // taken by the one flush it holds
      ExecutorService threads = Executors.newFixedThreadPool(2);
      try {
        Future<?> writer =
            threads.submit(
                () -> {
                  write.run();
                  return null;
                });
        assertThat(out.flushEntered.await(5, TimeUnit.SECONDS)).isTrue();
        Future<?> timeout =
            threads.submit(
                () -> {
                  container.onTimeout(new AsyncEvent(async));
                  return null;
                });

        Thread.sleep(300);
        assertThat(timeout.isDone()).as("the timeout waits for the writer").isFalse();
        assertThat(order).as("nothing ended the response under the writer").isEmpty();
        int writtenUnderTheWriter = out.size();

        release.countDown();
        timeout.get(5, TimeUnit.SECONDS);
        writer.get(5, TimeUnit.SECONDS);
        assertThat(out.size()).as("nothing written after the end").isEqualTo(writtenUnderTheWriter);
      } finally {
        threads.shutdownNow();
      }
    }
  }

  @FunctionalInterface
  private interface ThrowingStep {
    void run() throws Exception;
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private LiveFrame ready(String revision) {
    return new LiveFrame.Ready(new LiveReadyDto(connectionId, resourceId, revision));
  }

  private LiveFrame invalidated(String revision) {
    return new LiveFrame.Invalidated(new LiveInvalidatedDto(connectionId, resourceId, revision));
  }

  private LiveFrame closed(LiveCloseReason reason) {
    return new LiveFrame.Closed(new LiveClosedDto(connectionId, reason));
  }

  /** A container output whose readiness the test sets, before and after a flush. */
  private static final class ScriptedOutput extends ServletOutputStream {
    private final ByteArrayOutputStream written = new ByteArrayOutputStream();
    volatile boolean ready = true;
    volatile boolean readyAfterFlush = true;
    volatile IOException failure;
    volatile WriteListener listener;
    volatile CountDownLatch flushEntered;
    volatile CountDownLatch flushRelease;

    @Override
    public boolean isReady() {
      return ready;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
      this.listener = writeListener;
    }

    @Override
    public void write(int b) throws IOException {
      write(new byte[] {(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
      if (failure != null) {
        throw failure;
      }
      synchronized (written) {
        written.write(bytes, offset, length);
      }
    }

    @Override
    public void flush() throws IOException {
      if (failure != null) {
        throw failure;
      }
      CountDownLatch release = flushRelease;
      if (release != null) {
        flushRelease = null;
        flushEntered.countDown();
        try {
          if (!release.await(10, TimeUnit.SECONDS)) {
            throw new IOException("the test never let the writer go");
          }
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new IOException(interrupted);
        }
      }
      ready = readyAfterFlush;
    }

    int size() {
      synchronized (written) {
        return written.size();
      }
    }

    String text() {
      synchronized (written) {
        return written.toString(StandardCharsets.UTF_8);
      }
    }
  }

  private static final class RecordingAbort implements SseLiveChannel.ConnectionAbort {
    private final boolean supported;
    int calls;

    RecordingAbort(boolean supported) {
      this.supported = supported;
    }

    @Override
    public boolean closeNow(IOException cause) {
      calls++;
      return supported;
    }
  }

  private static final class RecordingListener implements LiveChannel.Listener {
    final AtomicInteger completed = new AtomicInteger();
    final AtomicInteger timeouts = new AtomicInteger();
    final List<Throwable> failed = new ArrayList<>();

    @Override
    public void completed() {
      completed.incrementAndGet();
    }

    @Override
    public void failed(Throwable failure) {
      failed.add(failure);
    }

    @Override
    public void timedOut() {
      timeouts.incrementAndGet();
    }
  }
}
