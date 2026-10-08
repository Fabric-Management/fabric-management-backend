package com.fabricmanagement.platform.realtime.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.realtime.domain.LiveEditSession;
import com.fabricmanagement.platform.realtime.domain.LiveResource;
import com.fabricmanagement.platform.realtime.domain.LiveRevision;
import com.fabricmanagement.platform.realtime.domain.exception.LiveEditSessionNotFoundException;
import com.fabricmanagement.platform.realtime.infra.repository.LiveEditSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edit sessions of any live resource (CEDIT-06 §2). Domain-agnostic: the consuming module decides
 * who may open, renew, close or list sessions of its resource and calls this service only after
 * that check, in the bound tenant. State lives in PostgreSQL only, so every instance sees the same
 * sessions and nothing depends on which instance a request reaches.
 */
@Service
@RequiredArgsConstructor
public class LiveEditSessionService {

  /** The presence revision of a resource nobody edits. */
  static final LiveRevision NOBODY = new LiveRevision("0");

  private final LiveEditSessionRepository sessions;
  private final LiveEditSessionProperties properties;
  private final LiveEditLeaseService leases;
  private final Clock clock;

  /** Opens a new session of {@code userId} on the resource; every call is a new session. */
  @Transactional
  public LiveEditSession open(LiveResource resource, UUID userId) {
    TenantContext.requireTenantId();
    LiveEditSession session =
        LiveEditSession.open(resource, userId, clock.instant(), properties.getTtl());
    return sessions.save(session);
  }

  /**
   * Extends the user's own open session; the new expiry is returned. A closed, expired, foreign or
   * unknown session is refused the same way, so nothing is revealed about other sessions.
   */
  @Transactional
  public Instant renew(LiveResource resource, UUID sessionId, UUID userId) {
    UUID tenantId = TenantContext.requireTenantId();
    Instant now = clock.instant();
    Instant expiresAt = now.plus(properties.getTtl());
    int renewed =
        sessions.renew(tenantId, sessionId, resource.type(), resource.id(), userId, now, expiresAt);
    if (renewed != 1) {
      throw new LiveEditSessionNotFoundException();
    }
    return expiresAt;
  }

  /**
   * Closes the user's own session; closing twice, or someone else's session, changes nothing. The
   * session's field leases end in the same transaction (CEDIT-07): the session row is updated
   * first, then its lease rows, the order every lease writer uses.
   */
  @Transactional
  public void close(LiveResource resource, UUID sessionId, UUID userId) {
    UUID tenantId = TenantContext.requireTenantId();
    int closed =
        sessions.close(
            tenantId, sessionId, resource.type(), resource.id(), userId, clock.instant());
    if (closed == 1) {
      leases.releaseSession(resource, sessionId, userId);
    }
  }

  /** Sessions live now on the resource, oldest first. */
  @Transactional(readOnly = true)
  public List<LiveEditSession> live(LiveResource resource) {
    UUID tenantId = TenantContext.requireTenantId();
    return sessions.findLive(tenantId, resource.type(), resource.id(), clock.instant());
  }

  /**
   * An opaque marker of who edits the resource now: it changes when a session opens, closes or
   * expires, and only then. It is compared for equality only and reveals neither ids nor a count.
   * Called by live sources inside their own read transaction.
   */
  @Transactional(readOnly = true)
  public LiveRevision presenceRevision(LiveResource resource) {
    UUID tenantId = TenantContext.requireTenantId();
    return digest(sessions.findLiveIds(tenantId, resource.type(), resource.id(), clock.instant()));
  }

  /** When a client should renew its session, in whole seconds. */
  public long renewAfterSeconds() {
    return properties.renewAfterSeconds();
  }

  static LiveRevision digest(List<UUID> ids) {
    Objects.requireNonNull(ids, "ids");
    if (ids.isEmpty()) {
      return NOBODY;
    }
    MessageDigest sha256;
    try {
      sha256 = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is always available", impossible);
    }
    ids.stream()
        .map(UUID::toString)
        .sorted()
        .forEach(id -> sha256.update((id + "\n").getBytes(StandardCharsets.US_ASCII)));
    return new LiveRevision("p" + HexFormat.of().formatHex(sha256.digest(), 0, 16));
  }
}
