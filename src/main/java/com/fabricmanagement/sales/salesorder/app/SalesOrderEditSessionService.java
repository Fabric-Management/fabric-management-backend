package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.platform.realtime.app.LiveEditSessionService;
import com.fabricmanagement.platform.realtime.domain.LiveEditSession;
import com.fabricmanagement.platform.realtime.domain.LiveResource;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditSessionDto;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditorDto;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditorsDto;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who holds a sales order's edit form open (CEDIT-06 §2–3). Every call decides access again from
 * current data ({@link SalesOrderEditAccess}: tenant access, active user, FRESH permission and
 * scope, active order) before the platform's edit sessions are touched. Opening and renewing need
 * current write access, because a session says "editing"; listing and closing need read access. An
 * order the caller cannot read is not found, whatever the reason, so the endpoints reveal nothing
 * about other tenants or other scopes.
 *
 * <p>A session is presence only: no save or capability depends on it. Field leases (CEDIT-07) name
 * the session that holds them; closing the session ends its leases in the same transaction.
 */
@Service
@RequiredArgsConstructor
public class SalesOrderEditSessionService {

  private final SalesOrderEditAccess access;
  private final LiveEditSessionService sessions;

  /** Opens a new edit session of the caller on the order. */
  @Transactional
  public SalesOrderEditSessionDto open(UUID orderId, UUID actor) {
    access.requireWritable(orderId, actor);
    LiveEditSession session = sessions.open(resource(orderId), actor);
    return new SalesOrderEditSessionDto(
        session.getId(), session.getExpiresAt(), sessions.renewAfterSeconds());
  }

  /** Renews the caller's own open session; an ended or foreign one is not found. */
  @Transactional
  public SalesOrderEditSessionDto renew(UUID orderId, UUID editSessionId, UUID actor) {
    access.requireWritable(orderId, actor);
    Instant expiresAt = sessions.renew(resource(orderId), editSessionId, actor);
    return new SalesOrderEditSessionDto(editSessionId, expiresAt, sessions.renewAfterSeconds());
  }

  /**
   * Closes the caller's own session and ends its field leases; repeating it, or naming another's,
   * changes nothing.
   */
  @Transactional
  public void close(UUID orderId, UUID editSessionId, UUID actor) {
    access.requireReadable(orderId, actor);
    sessions.close(resource(orderId), editSessionId, actor);
  }

  /** The order's open edit sessions now, oldest first. */
  @Transactional(readOnly = true)
  public SalesOrderEditorsDto editors(UUID orderId, UUID actor) {
    access.requireReadable(orderId, actor);
    Map<UUID, Optional<String>> names = new HashMap<>();
    List<SalesOrderEditorDto> editors =
        sessions.live(resource(orderId)).stream()
            .map(
                session -> {
                  boolean mine = actor.equals(session.getUserId());
                  String name =
                      names.computeIfAbsent(session.getUserId(), access::displayName).orElse(null);
                  return new SalesOrderEditorDto(
                      session.getUserId(),
                      name,
                      session.getOpenedAt(),
                      mine,
                      mine ? session.getId() : null);
                })
            .toList();
    return new SalesOrderEditorsDto(editors);
  }

  private static LiveResource resource(UUID orderId) {
    return SalesOrderLiveRevisionSource.resource(orderId);
  }
}
