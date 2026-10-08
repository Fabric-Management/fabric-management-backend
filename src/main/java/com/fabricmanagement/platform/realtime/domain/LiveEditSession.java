package com.fabricmanagement.platform.realtime.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One open editor of one resource (CEDIT-06 §2.1): a browser tab of one user that holds the
 * resource's edit form open. The server creates it and its id; it lives while the tab renews it and
 * ends when the tab closes it or stops renewing. Two tabs of one user are two sessions.
 *
 * <p>A session is presence only. It grants no lock, no write right and no claim on any field; a
 * save never asks for it. A later field lease names the session that owns it.
 *
 * <p>Renewal and closing are single conditional updates in the repository, so a late renewal can
 * never revive a closed or expired session.
 */
@Entity
@Table(name = "live_edit_session", schema = "common_infrastructure")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveEditSession extends BaseEntity {

  @Column(name = "resource_type", nullable = false, updatable = false, length = 40)
  private String resourceType;

  @Column(name = "resource_id", nullable = false, updatable = false)
  private UUID resourceId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "opened_at", nullable = false, updatable = false)
  private Instant openedAt;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  /** When the tab closed it; null while open or after it simply expired. */
  @Column(name = "closed_at")
  private Instant closedAt;

  /** A session opened now by this user on this resource, valid for {@code ttl}. */
  public static LiveEditSession open(
      LiveResource resource, UUID userId, Instant now, Duration ttl) {
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(now, "now");
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("An edit session lives for a positive time");
    }
    LiveEditSession session = new LiveEditSession();
    session.resourceType = resource.type();
    session.resourceId = resource.id();
    session.userId = userId;
    session.openedAt = now;
    session.lastSeenAt = now;
    session.expiresAt = now.plus(ttl);
    return session;
  }

  /** Open and not yet expired at {@code now}. */
  public boolean isLiveAt(Instant now) {
    return closedAt == null && now.isBefore(expiresAt);
  }

  @Override
  protected String getModuleCode() {
    return "LES";
  }
}
