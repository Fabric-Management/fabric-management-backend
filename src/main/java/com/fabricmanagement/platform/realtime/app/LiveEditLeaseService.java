package com.fabricmanagement.platform.realtime.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.realtime.domain.LiveEditLease;
import com.fabricmanagement.platform.realtime.domain.LiveEditSession;
import com.fabricmanagement.platform.realtime.domain.LiveLeaseKey;
import com.fabricmanagement.platform.realtime.domain.LiveLeaseMode;
import com.fabricmanagement.platform.realtime.domain.LiveResource;
import com.fabricmanagement.platform.realtime.domain.LiveRevision;
import com.fabricmanagement.platform.realtime.domain.exception.LiveEditLeaseLimitException;
import com.fabricmanagement.platform.realtime.domain.exception.LiveEditLeasesNotEnforcedException;
import com.fabricmanagement.platform.realtime.domain.exception.LiveEditSessionNotFoundException;
import com.fabricmanagement.platform.realtime.infra.repository.LiveEditLeaseControlRepository;
import com.fabricmanagement.platform.realtime.infra.repository.LiveEditLeaseRepository;
import com.fabricmanagement.platform.realtime.infra.repository.LiveEditSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Field leases of any live resource (CEDIT-07 §3), domain-agnostic. The consuming module decides
 * who may lease which keys of its resource, maps its catalogue to {@link LiveLeaseKey}s, names the
 * resource's current editability generation and calls this service only after its own access check,
 * in the bound tenant. Ownership lives in PostgreSQL only, so every instance sees the same holder
 * and nothing depends on which instance a request reaches.
 *
 * <p>Lock order, the same for every caller: the consumer's own resource lock (if it takes one),
 * then the per-resource lease lock (decisions that may grant a lease or rely on nobody holding
 * one), then the edit session row (shared), then lease rows in key order. Time is read after the
 * last lock is held, so a lease that expired while a request waited is never taken as held.
 */
@Service
@RequiredArgsConstructor
public class LiveEditLeaseService {

  /** The lease revision of a resource on which nobody holds a lease. */
  static final LiveRevision NOBODY = new LiveRevision("0");

  private final LiveEditLeaseRepository leases;
  private final LiveEditSessionRepository sessions;
  private final LiveEditLeaseControlRepository control;
  private final LiveEditLeaseProperties properties;
  private final Clock clock;

  /** What an acquire answered: every key granted, or the other sessions' leases in the way. */
  public sealed interface Acquisition permits Granted, Refused {}

  /** Every requested key, held by the session now, in key order. */
  public record Granted(List<LiveEditLease> leases) implements Acquisition {}

  /** Nothing was granted; these leases of other sessions overlap a requested key. */
  public record Refused(List<LiveEditLease> holders) implements Acquisition {}

  /** Renewed leases with their new expiry, and the tokens that are no longer this session's. */
  public record Renewal(List<LiveEditLease> renewed, List<UUID> lost) {}

  /**
   * A required key the save does not hold: {@code holder} is the lease that is in the way, or null
   * when nobody holds it (or only an ended period of the caller's own exists).
   */
  public record Missing(LiveLeaseKey key, LiveEditLease holder) {}

  /**
   * What a save proved: the leases it holds for its required keys (to be released when it
   * succeeds), the keys it lacks, and the tokens it sent that prove none of its required keys.
   */
  public record Verification(
      List<LiveEditLease> held, List<Missing> missing, Set<UUID> unexpectedTokens) {

    public Verification {
      held = List.copyOf(held);
      missing = List.copyOf(missing);
      unexpectedTokens = Set.copyOf(unexpectedTokens);
    }

    public boolean proven() {
      return missing.isEmpty() && unexpectedTokens.isEmpty();
    }
  }

  // ── mode ──────────────────────────────────────────────────────────────────

  /** Whether leases are enforced for this kind of resource in the bound tenant now. */
  @Transactional(readOnly = true)
  public LiveLeaseMode mode(String resourceType) {
    UUID tenantId = TenantContext.requireTenantId();
    return control.isEnforced(tenantId, resourceType) ? LiveLeaseMode.ENFORCED : LiveLeaseMode.OFF;
  }

  /**
   * Switches enforcement on for the bound tenant (CEDIT-07 §3.4). Monotonic: nothing in the
   * application switches it off. Activation in a real environment is an operation step; see the
   * field-lease contract. Returns true when this call switched it on.
   */
  @Transactional
  public boolean enforce(String resourceType, String by) {
    UUID tenantId = TenantContext.requireTenantId();
    return control.enforce(tenantId, resourceType, now(), Objects.requireNonNull(by));
  }

  // ── a session's own leases ───────────────────────────────────────────────

  /**
   * Grants all of {@code keys} to the session, or none (CEDIT-07 §3.1). A key the session already
   * holds keeps its period and token and is only extended, so a client that lost the answer and
   * asks again recovers the same leases; only when no consistent extension exists (R3) does that
   * period end and a new one start with a new token. A free key, an ended one or one of an older
   * generation starts a new period with a new token. Expiry never passes the session's own expiry.
   */
  @Transactional
  public Acquisition acquire(
      LiveResource resource,
      long generation,
      UUID sessionId,
      UUID userId,
      Collection<LiveLeaseKey> keys) {
    UUID tenantId = TenantContext.requireTenantId();
    Set<LiveLeaseKey> wanted = new TreeSet<>(keys);
    if (wanted.isEmpty() || wanted.size() != keys.size()) {
      throw new IllegalArgumentException("Acquire names distinct keys");
    }
    if (wanted.size() > properties.getMaxKeysPerRequest()) {
      throw new IllegalArgumentException("Too many keys in one acquire");
    }
    if (!control.isEnforced(tenantId, resource.type())) {
      throw new LiveEditLeasesNotEnforcedException();
    }
    control.lockResource(tenantId, resource);
    LiveEditSession session = ownSession(tenantId, resource, sessionId, userId);
    Map<LiveLeaseKey, LiveEditLease> rows = byKey(lockScopes(tenantId, resource, wanted));
    Instant now = now();
    if (!session.isLiveAt(now)) {
      throw new LiveEditSessionNotFoundException();
    }

    List<LiveEditLease> blocking =
        rows.values().stream()
            .filter(row -> row.isHeldAt(now, generation))
            .filter(row -> !row.getSessionId().equals(sessionId))
            .filter(row -> wanted.stream().anyMatch(key -> key.overlaps(row.key())))
            .toList();
    if (!blocking.isEmpty()) {
      return new Refused(blocking);
    }

    long added =
        wanted.stream()
            .filter(
                key ->
                    !Optional.ofNullable(rows.get(key))
                        .map(row -> row.isHeldBy(sessionId, now, generation))
                        .orElse(false))
            .count();
    // Only holdings valid now in this generation count (R2); a key this session already holds is
    // not in `added`, and a void or ended row being taken over is in neither count. Both counts are
    // read under the per-resource lock, and a session belongs to this resource, so two acquires
    // cannot together pass a bound.
    if (added > 0) {
      if (leases.countHeldBySession(
                  tenantId, resource.type(), resource.id(), sessionId, generation, now)
              + added
          > properties.getMaxLeasesPerSession()) {
        throw new LiveEditLeaseLimitException(
            "This edit form holds too many fields; save or leave some first");
      }
      if (leases.countHeldOnResource(tenantId, resource.type(), resource.id(), generation, now)
              + added
          > properties.getMaxLeasesPerResource()) {
        throw new LiveEditLeaseLimitException("Too many fields of this record are being edited");
      }
    }

    Instant expiresAt = expiry(now, session);
    List<LiveEditLease> granted = new ArrayList<>();
    for (LiveLeaseKey key : wanted) {
      LiveEditLease row = rows.get(key);
      if (row == null) {
        row =
            leases.save(
                LiveEditLease.grant(resource, key, generation, sessionId, userId, now, expiresAt));
      } else if (row.isHeldBy(sessionId, now, generation)) {
        if (!row.renew(now, expiresAt, session.getExpiresAt(), generation)) {
          // No consistent extension (a clock difference of about a lifetime): the old period
          // has just ended in this transaction; a new one starts on this clock, with a new token.
          row.takeOver(generation, sessionId, userId, now, expiresAt);
        }
      } else {
        row.takeOver(generation, sessionId, userId, now, expiresAt);
      }
      granted.add(row);
    }
    leases.flush();
    return new Granted(granted);
  }

  /**
   * Extends the session's own held leases named by their tokens; each one independently. A token of
   * an ended period, of another session or of an older generation is lost and never revived. A
   * lease that cannot be extended consistently (R3) is ended in this transaction and reported lost,
   * so the server agrees: it proves no save and blocks nobody.
   */
  @Transactional
  public Renewal renew(
      LiveResource resource,
      long generation,
      UUID sessionId,
      UUID userId,
      Collection<UUID> tokens) {
    UUID tenantId = TenantContext.requireTenantId();
    Set<UUID> asked = new LinkedHashSet<>(tokens);
    LiveEditSession session = ownSession(tenantId, resource, sessionId, userId);
    List<LiveEditLease> rows =
        asked.isEmpty()
            ? List.of()
            : leases.lockByTokens(tenantId, resource.type(), resource.id(), asked);
    Instant now = now();
    if (!session.isLiveAt(now)) {
      throw new LiveEditSessionNotFoundException();
    }
    Instant expiresAt = expiry(now, session);
    List<LiveEditLease> renewed = new ArrayList<>();
    for (LiveEditLease row : rows) {
      if (row.isHeldBy(sessionId, now, generation)
          && row.getUserId().equals(userId)
          && row.renew(now, expiresAt, session.getExpiresAt(), generation)) {
        renewed.add(row);
      }
    }
    Set<UUID> kept = renewed.stream().map(LiveEditLease::getToken).collect(Collectors.toSet());
    return new Renewal(renewed, asked.stream().filter(token -> !kept.contains(token)).toList());
  }

  /**
   * Gives back the session's own leases named by their tokens. Releasing twice, an ended period or
   * a token that is not the session's changes nothing. Works after the session ended too.
   */
  @Transactional
  public int release(LiveResource resource, UUID sessionId, UUID userId, Collection<UUID> tokens) {
    UUID tenantId = TenantContext.requireTenantId();
    if (tokens.isEmpty()) {
      return 0;
    }
    // The session row first, as a close takes it: a close and a release cannot cross.
    if (sessions
        .lockForLease(tenantId, sessionId, resource.type(), resource.id())
        .filter(session -> session.getUserId().equals(userId))
        .isEmpty()) {
      return 0;
    }
    List<LiveEditLease> rows =
        leases.lockByTokens(tenantId, resource.type(), resource.id(), Set.copyOf(tokens));
    Instant now = now();
    int released = 0;
    for (LiveEditLease row : rows) {
      if (row.getSessionId().equals(sessionId)
          && row.getUserId().equals(userId)
          && row.getReleasedAt() == null) {
        row.release(now);
        released++;
      }
    }
    return released;
  }

  /**
   * Releases every lease of a session that is being closed, in the close's transaction (after the
   * session row was updated by it). Only the session owner's leases are touched.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public int releaseSession(LiveResource resource, UUID sessionId, UUID userId) {
    UUID tenantId = TenantContext.requireTenantId();
    List<LiveEditLease> rows =
        leases.lockUnreleasedOfSession(tenantId, resource.type(), resource.id(), sessionId);
    Instant now = now();
    int released = 0;
    for (LiveEditLease row : rows) {
      if (row.getUserId().equals(userId)) {
        row.release(now);
        released++;
      }
    }
    return released;
  }

  // ── what everybody may see ────────────────────────────────────────────────

  /** Leases held now on the resource in its current generation, in key order. */
  @Transactional(readOnly = true)
  public List<LiveEditLease> held(LiveResource resource, long generation) {
    UUID tenantId = TenantContext.requireTenantId();
    return leases.findHeld(tenantId, resource.type(), resource.id(), generation, now());
  }

  /**
   * An opaque marker of which leases are held on the resource now: it changes when a lease is
   * granted, released, taken over, expires or falls to an older generation, and not when one is
   * renewed. Compared for equality only; it reveals no token, id or count. Called by live sources
   * inside their own read transaction.
   */
  @Transactional(readOnly = true)
  public LiveRevision leaseRevision(LiveResource resource, long generation) {
    return digest(held(resource, generation));
  }

  // ── other writers ────────────────────────────────────────────────────────

  /**
   * The leases held now that overlap any of {@code keys}, decided under the per-resource lease lock
   * held until the caller's transaction ends: no lease can be granted on these keys before the
   * caller commits. A writer that changes a leased field refuses when this is not empty.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public List<LiveEditLease> heldOverlapping(
      LiveResource resource, long generation, Collection<LiveLeaseKey> keys) {
    UUID tenantId = TenantContext.requireTenantId();
    control.lockResource(tenantId, resource);
    Instant now = now();
    return leases.findHeld(tenantId, resource.type(), resource.id(), generation, now).stream()
        .filter(row -> keys.stream().anyMatch(key -> key.overlaps(row.key())))
        .toList();
  }

  /** As {@link #heldOverlapping} for a writer that replaces the whole resource. */
  @Transactional(propagation = Propagation.MANDATORY)
  public List<LiveEditLease> heldAny(LiveResource resource, long generation) {
    UUID tenantId = TenantContext.requireTenantId();
    control.lockResource(tenantId, resource);
    return leases.findHeld(tenantId, resource.type(), resource.id(), generation, now());
  }

  // ── a save ───────────────────────────────────────────────────────────────

  /**
   * Checks, in the save's transaction, that the save holds every key it writes (CEDIT-07 §3.3). The
   * session row (shared) and the lease rows of the keys' scopes stay locked until the save ends, so
   * no lease of these keys changes hands between this check and the save's commit.
   *
   * <p>{@code ENFORCED}: each required key needs a lease held now by {@code sessionId}, owned by
   * {@code userId}, whose token was sent; and no other session may hold an overlapping lease.
   * {@code OFF}: no proof is needed, but a key that somebody still holds is refused all the same,
   * so lowering the mode never frees a held field. In both modes a token that proves none of the
   * required keys is reported, never silently ignored.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public Verification verify(
      LiveResource resource,
      long generation,
      LiveLeaseMode mode,
      UUID sessionId,
      Set<UUID> tokens,
      UUID userId,
      Set<LiveLeaseKey> required) {
    UUID tenantId = TenantContext.requireTenantId();
    if (required.isEmpty()) {
      return new Verification(List.of(), List.of(), tokens);
    }
    Optional<LiveEditSession> session =
        sessionId == null
            ? Optional.empty()
            : sessions
                .lockForLease(tenantId, sessionId, resource.type(), resource.id())
                .filter(found -> found.getUserId().equals(userId));
    List<LiveEditLease> rows = lockScopes(tenantId, resource, required);
    Instant now = now();
    boolean sessionLive = session.map(found -> found.isLiveAt(now)).orElse(false);
    Map<LiveLeaseKey, LiveEditLease> byKey = byKey(rows);

    List<LiveEditLease> held = new ArrayList<>();
    List<Missing> missing = new ArrayList<>();
    for (LiveLeaseKey key : new TreeSet<>(required)) {
      LiveEditLease own =
          Optional.ofNullable(byKey.get(key))
              .filter(row -> sessionLive)
              .filter(row -> row.isHeldBy(sessionId, now, generation))
              .filter(row -> row.getUserId().equals(userId))
              .filter(row -> tokens.contains(row.getToken()))
              .orElse(null);
      Optional<LiveEditLease> inTheWay =
          rows.stream()
              .filter(row -> row != own)
              .filter(row -> row.isHeldAt(now, generation))
              .filter(row -> row.key().overlaps(key))
              .filter(row -> own == null || !row.getSessionId().equals(sessionId))
              .findFirst();
      if (own != null && inTheWay.isEmpty()) {
        held.add(own);
      } else if (mode == LiveLeaseMode.ENFORCED || inTheWay.isPresent()) {
        missing.add(new Missing(key, inTheWay.orElse(null)));
      }
    }
    Set<UUID> used = held.stream().map(LiveEditLease::getToken).collect(Collectors.toSet());
    Set<UUID> unexpected = new LinkedHashSet<>(tokens);
    unexpected.removeAll(used);
    return new Verification(held, missing, unexpected);
  }

  /** Ends the leases a successful save used, in the save's transaction. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void releaseHeld(Collection<LiveEditLease> held) {
    Instant now = now();
    held.forEach(lease -> lease.release(now));
  }

  /** When a client should renew, and the other published timings, in whole seconds. */
  public LiveEditLeaseProperties properties() {
    return properties;
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private LiveEditSession ownSession(
      UUID tenantId, LiveResource resource, UUID sessionId, UUID userId) {
    return sessions
        .lockForLease(tenantId, sessionId, resource.type(), resource.id())
        .filter(session -> session.getUserId().equals(userId))
        .orElseThrow(LiveEditSessionNotFoundException::new);
  }

  private List<LiveEditLease> lockScopes(
      UUID tenantId, LiveResource resource, Collection<LiveLeaseKey> keys) {
    Set<String> scopes = keys.stream().map(LiveLeaseKey::scope).collect(Collectors.toSet());
    return leases.lockInScopes(tenantId, resource.type(), resource.id(), scopes);
  }

  /**
   * The current instant at the precision PostgreSQL keeps (microseconds). A lease is answered from
   * memory when it is acquired and from its row afterwards; truncating here makes both answers
   * carry the same acquiredAt, renewedAt and expiresAt, whatever the clock's resolution
   * (nanoseconds on Linux).
   */
  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }

  private Instant expiry(Instant now, LiveEditSession session) {
    Instant byTtl = now.plus(properties.getTtl());
    return byTtl.isBefore(session.getExpiresAt()) ? byTtl : session.getExpiresAt();
  }

  private static Map<LiveLeaseKey, LiveEditLease> byKey(List<LiveEditLease> rows) {
    return rows.stream().collect(Collectors.toMap(LiveEditLease::key, Function.identity()));
  }

  static LiveRevision digest(List<LiveEditLease> held) {
    Objects.requireNonNull(held, "held");
    if (held.isEmpty()) {
      return NOBODY;
    }
    MessageDigest sha256;
    try {
      sha256 = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is always available", impossible);
    }
    // The ownership period, never its token: a renewal changes none of these.
    held.stream()
        .map(
            lease ->
                lease.getId()
                    + "|"
                    + lease.getLeaseScope()
                    + "|"
                    + lease.getLeasePart()
                    + "|"
                    + lease.getSessionId()
                    + "|"
                    + lease.getAcquiredAt())
        .sorted()
        .forEach(line -> sha256.update((line + "\n").getBytes(StandardCharsets.UTF_8)));
    return new LiveRevision("l" + HexFormat.of().formatHex(sha256.digest(), 0, 16));
  }
}
