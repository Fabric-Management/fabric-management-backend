package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.realtime.app.LiveEditSessionService;
import com.fabricmanagement.platform.realtime.domain.LiveActor;
import com.fabricmanagement.platform.realtime.domain.LiveReadResult;
import com.fabricmanagement.platform.realtime.domain.LiveResource;
import com.fabricmanagement.platform.realtime.domain.LiveRevision;
import com.fabricmanagement.platform.realtime.domain.LiveRevisionSource;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.sales.salesorder.app.SalesOrderAccessPolicy.PermissionFreshness;
import com.fabricmanagement.sales.salesorder.app.SalesOrderAccessPolicy.ReadReach;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A sales order as a live resource (CEDIT-05 §6). Its revision is the committed version of the
 * order row, which the safe edit, product correction, quantity acceptance, the legacy full update
 * and the flow moves all advance; a replay, a no-change, a conflict or a rollback leaves it as it
 * was. Every read decides access again: the user must still be active, still hold a fresh sales
 * read permission, and the active order must still be within that scope. Only the order root's
 * tenant, creator, active flag and version are read; no detail, line or profile is loaded.
 *
 * <p>A visible order also carries its presence marker (CEDIT-06): who holds its edit form open now,
 * read from the edit sessions only after access was decided. It is a separate marker, never folded
 * into the version.
 */
@Component
@RequiredArgsConstructor
public class SalesOrderLiveRevisionSource implements LiveRevisionSource {

  static final String RESOURCE_TYPE = "sales-order";

  private final SalesOrderRepository orders;
  private final SalesOrderAccessPolicy accessPolicy;
  private final UserQueryService users;
  private final LiveEditSessionService editSessions;

  @Override
  public String resourceType() {
    return RESOURCE_TYPE;
  }

  @Override
  public LiveReadResult read(LiveActor actor, UUID orderId) {
    UUID tenantId = actor.tenantId();
    if (!Objects.equals(tenantId, TenantContext.getCurrentTenantIdOrNull())) {
      throw new IllegalStateException("A live order read runs in the actor's own tenant scope");
    }
    if (!users.isActive(tenantId, actor.userId())) {
      return LiveReadResult.Hidden.FORBIDDEN;
    }
    ReadReach reach = accessPolicy.readReach(tenantId, actor.userId(), PermissionFreshness.FRESH);
    if (!reach.any()) {
      return LiveReadResult.Hidden.FORBIDDEN;
    }
    return orders
        .findLiveRevisionViewByTenantIdAndId(tenantId, orderId)
        .filter(order -> Boolean.TRUE.equals(order.getIsActive()))
        .filter(order -> reach.permits(order.getTenantId(), order.getCreatedBy()))
        .<LiveReadResult>map(
            order ->
                new LiveReadResult.Visible(
                    revision(order.getVersion()), editSessions.presenceRevision(resource(orderId))))
        .orElse(LiveReadResult.Hidden.NOT_FOUND);
  }

  /** The live resource of one order, as edit sessions and the channel name it. */
  static LiveResource resource(UUID orderId) {
    return new LiveResource(RESOURCE_TYPE, orderId);
  }

  private static LiveRevision revision(Long version) {
    if (version == null) {
      throw new IllegalStateException("A sales order always has a version");
    }
    return LiveRevision.of(version);
  }
}
