package com.fabricmanagement.sales.salesorder.app.adapter;

import com.fabricmanagement.common.infrastructure.persistence.SalesOrderLineFulfilmentLock;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.logistics.shipment.domain.port.SalesShipmentQuantityPort;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.sales.common.domain.SalesQuantityPolicy;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesShipmentQuantityAdapter implements SalesShipmentQuantityPort {
  private final SalesOrderLineFulfilmentLock locks;
  private final SalesOrderLineRepository lines;
  private final ProposalStockQueryService measures;
  private final com.fabricmanagement.production.core.stockunit.api.StockUnitCutPlanPort cutPlans;
  private final com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService
      products;

  @Override
  public Map<UUID, Limit> lockExactLimits(Collection<UUID> ids) {
    UUID tenant = TenantContext.requireTenantId();
    locks.lockAll(tenant, ids);
    Map<UUID, Limit> result = new LinkedHashMap<>();
    for (UUID id : ids) {
      var found = lines.findByTenantIdAndId(tenant, id);
      if (found.isEmpty() || found.get().getQuantityPolicy() != SalesQuantityPolicy.EXACT) continue;
      var line = found.get();
      var length =
          measures.toCanonical(line.getRequestedQty(), line.getUnit(), PrimaryMeasure.LENGTH);
      var weight =
          length.isPresent()
              ? java.util.Optional.<java.math.BigDecimal>empty()
              : measures.toCanonical(line.getRequestedQty(), line.getUnit(), PrimaryMeasure.WEIGHT);
      var quantity =
          length
              .or(() -> weight)
              .orElseThrow(
                  () ->
                      OrderIntakeException.rule(
                          "EXACT_QUANTITY_UNIT_UNKNOWN",
                          "The exact shipment quantity needs a compatible measured unit"));
      boolean fabric =
          products
              .find(tenant, line.getProductId())
              .map(
                  product ->
                      product.productType()
                          == com.fabricmanagement.product.core.domain.ProductType.FABRIC)
              .orElse(false);
      result.put(
          id,
          new Limit(
              quantity,
              length.isPresent() ? "M" : "KG",
              cutPlans.hasUnfinishedPlan(id)
                  || fabric && length.isPresent() && !cutPlans.exactDeliveryReady(id, quantity)));
    }
    return Map.copyOf(result);
  }
}
