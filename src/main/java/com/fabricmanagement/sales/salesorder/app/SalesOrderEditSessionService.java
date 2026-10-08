package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.realtime.app.LiveEditSessionService;
import com.fabricmanagement.platform.realtime.domain.LiveEditSession;
import com.fabricmanagement.platform.realtime.domain.LiveResource;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.sales.salesorder.app.SalesOrderAccessPolicy.PermissionFreshness;
import com.fabricmanagement.sales.salesorder.app.SalesOrderAccessPolicy.ReadReach;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditSessionDto;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditorDto;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditorsDto;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who holds a sales order's edit form open (CEDIT-06 §2–3). Every call decides access again from
 * current data (FRESH permission and scope, active user, active order) before the platform's edit
 * sessions are touched. Opening and renewing need current write access, because a session says
 * "editing"; listing and closing need read access. An order the caller cannot read is not found,
 * whatever the reason, so the endpoints reveal nothing about other tenants or other scopes.
 *
 * <p>A session is presence only: no save, lock or capability depends on it.
 */
@Service
@RequiredArgsConstructor
public class SalesOrderEditSessionService {

  private final SalesOrderRepository orders;
  private final SalesOrderAccessPolicy accessPolicy;
  private final UserQueryService users;
  private final LiveEditSessionService sessions;

  /** Opens a new edit session of the caller on the order. */
  @Transactional
  public SalesOrderEditSessionDto open(UUID orderId, UUID actor) {
    requireWritable(orderId, actor);
    LiveEditSession session = sessions.open(resource(orderId), actor);
    return new SalesOrderEditSessionDto(
        session.getId(), session.getExpiresAt(), sessions.renewAfterSeconds());
  }

  /** Renews the caller's own open session; an ended or foreign one is not found. */
  @Transactional
  public SalesOrderEditSessionDto renew(UUID orderId, UUID editSessionId, UUID actor) {
    requireWritable(orderId, actor);
    Instant expiresAt = sessions.renew(resource(orderId), editSessionId, actor);
    return new SalesOrderEditSessionDto(editSessionId, expiresAt, sessions.renewAfterSeconds());
  }

  /** Closes the caller's own session; repeating it, or naming another's, changes nothing. */
  @Transactional
  public void close(UUID orderId, UUID editSessionId, UUID actor) {
    requireReadable(orderId, actor);
    sessions.close(resource(orderId), editSessionId, actor);
  }

  /** The order's open edit sessions now, oldest first. */
  @Transactional(readOnly = true)
  public SalesOrderEditorsDto editors(UUID orderId, UUID actor) {
    UUID tenantId = requireReadable(orderId, actor);
    Map<UUID, Optional<String>> names = new HashMap<>();
    List<SalesOrderEditorDto> editors =
        sessions.live(resource(orderId)).stream()
            .map(
                session -> {
                  boolean mine = actor.equals(session.getUserId());
                  String name =
                      names
                          .computeIfAbsent(session.getUserId(), user -> displayName(tenantId, user))
                          .orElse(null);
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

  /** The bound tenant, when the caller is active and may read the active order now. */
  private UUID requireReadable(UUID orderId, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    if (!users.isActive(tenantId, actor)) {
      throw notFound(orderId);
    }
    ReadReach reach = accessPolicy.readReach(tenantId, actor, PermissionFreshness.FRESH);
    boolean visible =
        reach.any()
            && orders
                .findLiveRevisionViewByTenantIdAndId(tenantId, orderId)
                .filter(order -> Boolean.TRUE.equals(order.getIsActive()))
                .filter(order -> reach.permits(order.getTenantId(), order.getCreatedBy()))
                .isPresent();
    if (!visible) {
      throw notFound(orderId);
    }
    return tenantId;
  }

  /** As {@link #requireReadable}, and the caller may write the order now. */
  private void requireWritable(UUID orderId, UUID actor) {
    UUID tenantId = requireReadable(orderId, actor);
    SalesOrder order =
        orders
            .findByTenantIdAndId(tenantId, orderId)
            .filter(value -> Boolean.TRUE.equals(value.getIsActive()))
            .orElseThrow(() -> notFound(orderId));
    if (!accessPolicy.canWrite(tenantId, actor, order, PermissionFreshness.FRESH)) {
      throw new AccessDeniedException("You do not have access to edit this sales order.");
    }
  }

  private Optional<String> displayName(UUID tenantId, UUID userId) {
    return users
        .findById(tenantId, userId)
        .map(UserDto::getDisplayName)
        .filter(name -> !name.isBlank());
  }

  private static NotFoundException notFound(UUID orderId) {
    return new NotFoundException("Sales order not found: " + orderId);
  }
}
