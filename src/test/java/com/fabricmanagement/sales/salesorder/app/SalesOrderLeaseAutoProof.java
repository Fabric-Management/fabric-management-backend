package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseProperties;
import com.fabricmanagement.platform.realtime.app.LiveEditSessionService;
import com.fabricmanagement.platform.realtime.domain.LiveLeaseKey;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditInstructions.LineOperation;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditInstructions.Parsed;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseDtos;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseKey;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The field leases one test save needs, taken for that save only (CEDIT-07-F3). Leases are always
 * enforced, so the safe-edit tests of merging, replay, retention, access and history save through
 * this instead of without proof: it opens an edit session of the saving user, takes the keys the
 * request writes ({@link SalesOrderLeaseKeys#required(Parsed, Set)}, lines no longer active left
 * out as the server leaves out the lines gone since the base), sends the proof with the save and
 * closes the session afterwards, which ends its leases.
 *
 * <p>While another automatic save of the same test holds a key, it waits for that save to end, as a
 * client waits for a field to be free; the saves then meet in the merge as before. A key held
 * otherwise, a session or lease the server refuses, and a request it cannot read are sent without
 * proof, so the server's own answer shows. Call it inside the user's tenant step; the step is set
 * again before the session closes, in case the save cleared it.
 *
 * <p>{@link #hold} keeps a form open past its save instead: the leases stay until {@link #close}.
 */
public final class SalesOrderLeaseAutoProof {

  private static final Duration WAIT = Duration.ofSeconds(30);
  private static final String UNAVAILABLE = "EDIT_LEASE_UNAVAILABLE";

  private final SalesOrderEditSessionService sessions;
  private final LiveEditSessionService liveSessions;
  private final SalesOrderEditLeaseService leases;
  private final LiveEditLeaseProperties limits;
  private final JdbcTemplate jdbc;

  /** Automatic saves of this test holding their leases now. */
  private final AtomicInteger holding = new AtomicInteger();

  SalesOrderLeaseAutoProof(
      SalesOrderEditSessionService sessions,
      LiveEditSessionService liveSessions,
      SalesOrderEditLeaseService leases,
      LiveEditLeaseProperties limits,
      JdbcTemplate jdbc) {
    this.sessions = sessions;
    this.liveSessions = liveSessions;
    this.leases = leases;
    this.limits = limits;
    this.jdbc = jdbc;
  }

  /**
   * Saves {@code body} with the leases it writes: {@code save} gets the body to send, with the
   * proof when one was taken. A body that already carries a proof is sent as it is.
   */
  public <T> T save(
      UUID orderId,
      UUID actor,
      Map<String, Object> body,
      Supplier<SalesOrderEditRequest> request,
      Function<Map<String, Object>, T> save) {
    if (body.containsKey("editSessionId") || body.containsKey("leaseTokens")) {
      return save.apply(body);
    }
    List<SalesOrderEditLeaseKey> keys = requiredKeys(orderId, request);
    if (keys.isEmpty()) {
      return save.apply(body);
    }
    UUID tenantId = TenantContext.requireTenantId();
    Held taken = take(orderId, actor, keys);
    if (taken == null) {
      return save.apply(body);
    }
    try {
      return save.apply(taken.proven(body));
    } finally {
      TenantContext.setCurrentTenantId(tenantId);
      TenantContext.setCurrentUserId(actor);
      close(orderId, actor, taken);
    }
  }

  /**
   * An open form: its edit session and the tokens of the keys its save writes, held until the test
   * closes it, as a person keeps a field after saving it.
   */
  public record Held(UUID session, List<UUID> tokens) {

    /** The body with this form's proof. */
    public Map<String, Object> proven(Map<String, Object> body) {
      Map<String, Object> proven = new LinkedHashMap<>(body);
      proven.put("editSessionId", session);
      proven.put("leaseTokens", tokens);
      return proven;
    }
  }

  /** Takes the leases {@code request} writes and keeps them; fails when they are refused. */
  public Held hold(UUID orderId, UUID actor, SalesOrderEditRequest request) {
    List<SalesOrderEditLeaseKey> keys = requiredKeys(orderId, () -> request);
    Held held = keys.isEmpty() ? null : take(orderId, actor, keys);
    if (held == null) {
      throw new IllegalStateException("The form could not take the leases of " + keys);
    }
    return held;
  }

  /** Closes the form's session, which ends its leases. */
  public void close(UUID orderId, UUID actor, Held held) {
    try {
      closeQuietly(orderId, actor, held.session());
    } finally {
      holding.decrementAndGet();
    }
  }

  // ── keys ─────────────────────────────────────────────────────────────────

  private List<SalesOrderEditLeaseKey> requiredKeys(
      UUID orderId, Supplier<SalesOrderEditRequest> request) {
    Parsed parsed;
    try {
      parsed = SalesOrderEditInstructions.parse(orderId, request.get(), Integer.MAX_VALUE);
    } catch (RuntimeException unreadable) {
      // The server refuses it before any lease is asked for.
      return List.of();
    }
    Set<UUID> active =
        new HashSet<>(
            jdbc.queryForList(
                "SELECT id FROM sales_ord.sales_order_line WHERE sales_order_id = ? AND is_active",
                UUID.class,
                orderId));
    Set<UUID> inactive = new HashSet<>();
    for (LineOperation line : parsed.lines()) {
      if (line.lineId() != null && !active.contains(line.lineId())) {
        inactive.add(line.lineId());
      }
    }
    List<SalesOrderEditLeaseKey> keys = new ArrayList<>();
    for (LiveLeaseKey key : SalesOrderLeaseKeys.required(parsed, inactive)) {
      keys.add(SalesOrderLeaseKeys.toWire(key));
    }
    return keys;
  }

  // ── session and leases ───────────────────────────────────────────────────

  /** Every key in a new session of the user, or null when the server refuses them. */
  private Held take(UUID orderId, UUID actor, List<SalesOrderEditLeaseKey> keys) {
    long deadline = System.nanoTime() + WAIT.toNanos();
    while (true) {
      UUID session;
      try {
        session = sessions.open(orderId, actor).editSessionId();
      } catch (RuntimeException refused) {
        return null;
      }
      List<UUID> tokens = new ArrayList<>();
      String refusal = acquireAll(orderId, actor, session, keys, tokens);
      if (refusal == null) {
        holding.incrementAndGet();
        return new Held(session, List.copyOf(tokens));
      }
      closeQuietly(orderId, actor, session);
      // Only another automatic save of this test is waited for: it ends when its save ends.
      if (!UNAVAILABLE.equals(refusal) || holding.get() == 0 || System.nanoTime() > deadline) {
        return null;
      }
      pause();
    }
  }

  /** Acquires the keys in requests of the allowed size; the refusal's code, or null. */
  private String acquireAll(
      UUID orderId,
      UUID actor,
      UUID session,
      List<SalesOrderEditLeaseKey> keys,
      List<UUID> tokens) {
    int size = limits.getMaxKeysPerRequest();
    for (int from = 0; from < keys.size(); from += size) {
      List<SalesOrderEditLeaseKey> chunk = keys.subList(from, Math.min(keys.size(), from + size));
      try {
        leases
            .acquire(orderId, new SalesOrderEditLeaseDtos.AcquireRequest(session, chunk), actor)
            .leases()
            .forEach(lease -> tokens.add(lease.leaseToken()));
      } catch (DomainException refused) {
        return refused.getErrorCode();
      } catch (RuntimeException refused) {
        return refused.getClass().getSimpleName();
      }
    }
    return null;
  }

  /**
   * Closes the session on the platform, which ends its leases; without the order's access check, so
   * a save that took the user's access away still leaves no lease behind.
   */
  private void closeQuietly(UUID orderId, UUID actor, UUID session) {
    try {
      liveSessions.close(SalesOrderLiveRevisionSource.resource(orderId), session, actor);
    } catch (RuntimeException ignored) {
      // The session ends with its time to live either way.
    }
  }

  private static void pause() {
    try {
      Thread.sleep(20);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }
}
