package com.fabricmanagement.platform.realtime.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The right of one edit session to change one key of one resource for a limited time (CEDIT-07
 * §3.1). There is one row per (tenant, resource, key); each holding of it is an ownership period
 * with its own server-generated {@code token}. A new period (another session, or the same session
 * after the lease ended) always gets a new token, so a late renewal, release or save that still
 * carries an older token can never touch the current holder.
 *
 * <p>A period ends when it is released, when it expires, or when the resource moves to a new {@code
 * resourceGeneration} (the consumer bumps it when the resource stops being editable, for example
 * when a sales order leaves the draft). Ending never deletes the row; the retention job removes
 * ended rows later, and nothing about safety depends on that cleanup.
 *
 * <p>A lease is a write right, not a lock on reading, and grants no permission: the consumer checks
 * permission and the resource's state on every request.
 *
 * <p>Clock policy (CEDIT-07 R3): each instance decides with its own clock, read after its locks.
 * Within one period the recorded renewal time never moves backwards: a renewal or release whose
 * clock is behind the time already recorded (another instance, a clock corrected backwards) keeps
 * the later one. The expiry is the later of the new and the recorded one, capped by the bound the
 * caller gives (the edit session's expiry as read now); since a session renewed on a clock behind
 * can move its own expiry earlier, the cap may shorten the lease. When the cap is not after the
 * recorded renewal (only a clock difference of about a whole lifetime), no consistent extension
 * exists and the period ends there: the token is no proof of anything afterwards. A new period
 * takes its own clock as is: it shares no time with the previous one.
 */
@Entity
@Table(name = "live_edit_lease", schema = "common_infrastructure")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveEditLease extends BaseEntity {

  @Column(name = "resource_type", nullable = false, updatable = false, length = 40)
  private String resourceType;

  @Column(name = "resource_id", nullable = false, updatable = false)
  private UUID resourceId;

  @Column(name = "lease_scope", nullable = false, updatable = false, length = 80)
  private String leaseScope;

  @Column(name = "lease_part", nullable = false, updatable = false, length = 60)
  private String leasePart;

  /** The resource's editability generation this period was granted in. */
  @Column(name = "resource_generation", nullable = false)
  private long resourceGeneration;

  @Column(name = "session_id", nullable = false)
  private UUID sessionId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  /** Secret proof of this ownership period; never shown to anyone but its own session. */
  @Column(name = "token", nullable = false)
  private UUID token;

  @Column(name = "acquired_at", nullable = false)
  private Instant acquiredAt;

  @Column(name = "renewed_at", nullable = false)
  private Instant renewedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  /** When the holder gave it back (or a successful save or session close did); else null. */
  @Column(name = "released_at")
  private Instant releasedAt;

  /** A first period of {@code key} for the session, ending at {@code expiresAt}. */
  public static LiveEditLease grant(
      LiveResource resource,
      LiveLeaseKey key,
      long generation,
      UUID sessionId,
      UUID userId,
      Instant now,
      Instant expiresAt) {
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(key, "key");
    LiveEditLease lease = new LiveEditLease();
    lease.resourceType = resource.type();
    lease.resourceId = resource.id();
    lease.leaseScope = key.scope();
    lease.leasePart = key.part();
    lease.startPeriod(generation, sessionId, userId, now, expiresAt);
    return lease;
  }

  /**
   * Starts a new ownership period on this row, which must have ended; the new holder gets a new
   * token whoever held it before, the same session included.
   */
  public void takeOver(
      long generation, UUID sessionId, UUID userId, Instant now, Instant expiresAt) {
    if (isHeldAt(now, generation)) {
      throw new IllegalStateException("A lease that is still held cannot be taken over");
    }
    startPeriod(generation, sessionId, userId, now, expiresAt);
  }

  /**
   * Extends the current period; the token stays. Only a held lease is renewed. The renewal time is
   * the later of {@code now} and the one recorded; the expiry the later of {@code expiresAt} and
   * the one recorded, capped by {@code bound} (the edit session's expiry as read now). When no
   * consistent extension exists (the bound is not after the renewal time), the period ends now
   * instead and false is returned: the caller reports the token lost, and it is lost on the server
   * too, for saves, lists and other sessions alike.
   */
  public boolean renew(Instant now, Instant expiresAt, Instant bound, long generation) {
    if (!isHeldAt(now, generation)) {
      throw new IllegalStateException("An ended lease cannot be renewed");
    }
    requireAfter(now, expiresAt);
    Instant renewed = later(now, renewedAt);
    Instant expires = earlier(later(expiresAt, this.expiresAt), bound);
    if (!expires.isAfter(renewed)) {
      release(now);
      return false;
    }
    this.renewedAt = renewed;
    this.expiresAt = expires;
    return true;
  }

  /**
   * Ends the current period now; releasing an ended period changes nothing. A clock behind the
   * recorded renewal still ends the period at once; the recorded time is not earlier than it.
   */
  public void release(Instant now) {
    if (releasedAt == null) {
      this.releasedAt = later(now, renewedAt);
    }
  }

  private static Instant later(Instant left, Instant right) {
    return left.isAfter(right) ? left : right;
  }

  private static Instant earlier(Instant left, Instant right) {
    return left.isBefore(right) ? left : right;
  }

  /** Whether the current period is held at {@code now} in the resource's {@code generation}. */
  public boolean isHeldAt(Instant now, long generation) {
    return releasedAt == null && now.isBefore(expiresAt) && resourceGeneration == generation;
  }

  public boolean isHeldBy(UUID session, Instant now, long generation) {
    return isHeldAt(now, generation) && sessionId.equals(session);
  }

  public LiveLeaseKey key() {
    return new LiveLeaseKey(leaseScope, leasePart);
  }

  private void startPeriod(
      long generation, UUID sessionId, UUID userId, Instant now, Instant expiresAt) {
    Objects.requireNonNull(sessionId, "sessionId");
    Objects.requireNonNull(userId, "userId");
    requireAfter(now, expiresAt);
    this.resourceGeneration = generation;
    this.sessionId = sessionId;
    this.userId = userId;
    this.token = UUID.randomUUID();
    this.acquiredAt = now;
    this.renewedAt = now;
    this.expiresAt = expiresAt;
    this.releasedAt = null;
  }

  private static void requireAfter(Instant now, Instant expiresAt) {
    Objects.requireNonNull(now, "now");
    if (expiresAt == null || !expiresAt.isAfter(now)) {
      throw new IllegalArgumentException("A lease lives for a positive time");
    }
  }

  @Override
  protected String getModuleCode() {
    return "LEL";
  }
}
