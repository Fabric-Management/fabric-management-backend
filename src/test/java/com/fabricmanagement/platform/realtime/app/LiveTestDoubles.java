package com.fabricmanagement.platform.realtime.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.realtime.domain.LiveActor;
import com.fabricmanagement.platform.realtime.domain.LiveReadResult;
import com.fabricmanagement.platform.realtime.domain.LiveRevision;
import com.fabricmanagement.platform.realtime.domain.LiveRevisionSource;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Deterministic stand-ins for the live channel's unit tests: clock, executor, source, channel. */
final class LiveTestDoubles {

  private LiveTestDoubles() {}

  /** A visible resource with presence (CEDIT-06). */
  static LiveReadResult visible(String revision, String presence) {
    return new LiveReadResult.Visible(new LiveRevision(revision), new LiveRevision(presence));
  }

  /** A visible resource with presence and field leases (CEDIT-07). */
  static LiveReadResult visible(String revision, String presence, String lease) {
    return new LiveReadResult.Visible(
        new LiveRevision(revision), new LiveRevision(presence), new LiveRevision(lease));
  }

  static LiveReadResult visible(String revision) {
    return new LiveReadResult.Visible(new LiveRevision(revision));
  }

  /** Waits for a condition within a bound; a timeout fails the test. */
  static void await(BooleanSupplier condition) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("Condition not reached in time");
      }
      try {
        Thread.sleep(5);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError(interrupted);
      }
    }
  }

  static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new AssertionError("Latch not reached in time");
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AssertionError(interrupted);
    }
  }

  /** A clock the test moves by hand. */
  static final class TestClock extends Clock {
    private final AtomicReference<Instant> now;

    TestClock(Instant start) {
      this.now = new AtomicReference<>(start);
    }

    void advance(Duration duration) {
      now.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now.get();
    }
  }

  /** Jobs wait until the test runs them; a full executor refuses like the bounded pool does. */
  static final class ManualExecutor implements Executor {
    private final Deque<Runnable> tasks = new ArrayDeque<>();
    private volatile boolean full;

    @Override
    public synchronized void execute(Runnable task) {
      if (full) {
        throw new RejectedExecutionException("queue full");
      }
      tasks.add(task);
    }

    void full(boolean value) {
      full = value;
    }

    synchronized int queued() {
      return tasks.size();
    }

    private synchronized Runnable next() {
      return tasks.poll();
    }

    void runAll() {
      Runnable task;
      while ((task = next()) != null) {
        task.run();
      }
    }
  }

  /**
   * A transaction manager that records what a read ran under: the bound tenant when the transaction
   * began, and how it ended.
   */
  static final class RecordingTransactionManager implements PlatformTransactionManager {
    final List<UUID> tenantsAtBegin = new CopyOnWriteArrayList<>();
    final List<Authentication> authenticationsAtBegin = new CopyOnWriteArrayList<>();
    final List<TransactionDefinition> definitions = new CopyOnWriteArrayList<>();
    final AtomicInteger commits = new AtomicInteger();
    final AtomicInteger rollbacks = new AtomicInteger();

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      tenantsAtBegin.add(TenantContext.getCurrentTenantIdOrNull());
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication != null) {
        authenticationsAtBegin.add(authentication);
      }
      definitions.add(definition);
      return new SimpleTransactionStatus(true);
    }

    @Override
    public void commit(TransactionStatus status) {
      commits.incrementAndGet();
    }

    @Override
    public void rollback(TransactionStatus status) {
      rollbacks.incrementAndGet();
    }
  }

  /**
   * A source answering from a script; once the script is used up it repeats its last answer. An
   * answer may be a result or a runtime exception to throw. It records the tenant of every read.
   */
  static final class FakeSource implements LiveRevisionSource {
    private final Deque<Object> script = new ArrayDeque<>();
    private volatile Object last;
    final List<UUID> tenantsSeen = new CopyOnWriteArrayList<>();
    final AtomicInteger reads = new AtomicInteger();
    volatile Runnable duringRead = () -> {};

    FakeSource(Object... answers) {
      answer(answers);
    }

    synchronized FakeSource answer(Object... answers) {
      for (Object answer : answers) {
        script.add(answer);
      }
      return this;
    }

    @Override
    public String resourceType() {
      return "test-resource";
    }

    @Override
    public LiveReadResult read(LiveActor actor, UUID resourceId) {
      reads.incrementAndGet();
      tenantsSeen.add(TenantContext.getCurrentTenantIdOrNull());
      duringRead.run();
      Object answer = nextAnswer();
      if (answer instanceof RuntimeException failure) {
        throw failure;
      }
      return (LiveReadResult) answer;
    }

    private synchronized Object nextAnswer() {
      Object next = script.poll();
      if (next != null) {
        last = next;
      }
      return last;
    }
  }

  /**
   * A non-blocking transport that records frames. It can fail, report itself stalled from a given
   * instant, and end in the container at once when completed or aborted (as the container usually
   * does: an abort ends with an error) or wait until the test ends it, like a container still
   * holding a socket. Once aborted it no longer reports a stall, like the real channel.
   */
  static final class FakeChannel implements LiveChannel {
    final List<LiveFrame> frames = new CopyOnWriteArrayList<>();
    final AtomicInteger completions = new AtomicInteger();
    final AtomicInteger aborts = new AtomicInteger();
    volatile Listener listener;
    volatile IOException failure;
    volatile Instant stalledSince;
    volatile boolean containerEndsOnComplete = true;
    volatile boolean containerEndsOnAbort = true;

    @Override
    public void send(LiveFrame frame) throws IOException {
      IOException fail = failure;
      if (fail != null && !(frame instanceof LiveFrame.Ready)) {
        throw fail;
      }
      frames.add(frame);
    }

    @Override
    public Instant stalledSince() {
      return aborts.get() > 0 ? null : stalledSince;
    }

    @Override
    public void complete() {
      completions.incrementAndGet();
      Listener current = listener;
      if (containerEndsOnComplete && current != null) {
        current.completed();
      }
    }

    @Override
    public void abort() {
      aborts.incrementAndGet();
      Listener current = listener;
      if (containerEndsOnAbort && current != null) {
        current.failed(new IOException("aborted"));
      }
    }

    @Override
    public void listen(Listener listener) {
      this.listener = listener;
    }

    /** Frames as short labels: ready:1, invalidated:2, closed:ACCESS_REVOKED, heartbeat. */
    List<String> labels() {
      return frames.stream().map(FakeChannel::label).toList();
    }

    static String label(LiveFrame frame) {
      return switch (frame) {
        case LiveFrame.Ready ready -> "ready:" + ready.data().revision();
        case LiveFrame.Invalidated invalidated -> "invalidated:" + invalidated.data().revision();
        case LiveFrame.Closed closed -> "closed:" + closed.data().reason();
        case LiveFrame.Heartbeat heartbeat -> "heartbeat";
      };
    }
  }
}
