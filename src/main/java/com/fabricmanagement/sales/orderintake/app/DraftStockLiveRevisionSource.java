package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.platform.realtime.app.LiveEditLeaseService;
import com.fabricmanagement.platform.realtime.domain.*;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.production.core.stockunit.api.StockUnitLeaseKeys;
import com.fabricmanagement.production.core.stockunit.api.StockUnitOwnershipPort;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Signals only: no customer, owner, token or physical stock data travels in the public stream. */
@Component
@RequiredArgsConstructor
public class DraftStockLiveRevisionSource implements LiveRevisionSource {
  private final LiveEditLeaseService leases;
  private final UserQueryService users;
  private final SalesAccessScopeResolver permissions;
  private final ProductSalesDefinitionQueryService products;
  private final StockUnitOwnershipPort ownership;
  private final com.fabricmanagement.common.infrastructure.security.PermissionEvaluator
      productionPermissions;

  @Override
  public String resourceType() {
    return StockUnitLeaseKeys.RESOURCE_TYPE;
  }

  @Override
  public LiveReadResult read(LiveActor actor, UUID productId) {
    if (!actor
        .tenantId()
        .equals(
            com.fabricmanagement.common.infrastructure.persistence.TenantContext
                .getCurrentTenantIdOrNull()))
      throw new IllegalStateException("A live stock read runs in the actor's own tenant scope");
    if (!users.isActive(actor.tenantId(), actor.userId())
        || (permissions
                    .resolve(
                        actor.tenantId(),
                        actor.userId(),
                        "read",
                        SalesAccessScopeResolver.PermissionFreshness.FRESH)
                    .scope()
                == null
            && !productionRead(actor))) return LiveReadResult.Hidden.FORBIDDEN;
    if (products
        .find(actor.tenantId(), productId)
        .filter(
            product ->
                product.active()
                    && product.productType()
                        == com.fabricmanagement.product.core.domain.ProductType.FABRIC)
        .isEmpty()) return LiveReadResult.Hidden.NOT_FOUND;
    return new LiveReadResult.Visible(
        new LiveRevision(ownership.availabilityRevision(productId)),
        null,
        leases.leaseRevision(StockUnitLeaseKeys.resource(productId), 1));
  }

  private boolean productionRead(LiveActor actor) {
    var identity = users.findPermissionIdentity(actor.tenantId(), actor.userId());
    return identity
        .map(
            value ->
                productionPermissions
                    .evaluateFresh(
                        actor.tenantId(), value.roleCode(), value.departmentCodes(), actor.userId())
                    .can("production", "read"))
        .orElse(false);
  }
}
