package com.fabricmanagement.logistics.shipment.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.logistics.common.exception.LogisticsDomainException;
import com.fabricmanagement.logistics.shipment.domain.Shipment;
import com.fabricmanagement.logistics.shipment.domain.ShipmentUnit;
import com.fabricmanagement.logistics.shipment.domain.port.SalesShipmentQuantityPort;
import com.fabricmanagement.logistics.shipment.infra.repository.ShipmentLineRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExactShipmentQuantityGuard {
  private final SalesShipmentQuantityPort sales;
  private final ShipmentLineRepository lines;

  public void assertPickupAllowed(Shipment shipment) {
    var rows =
        shipment.getLines() == null
            ? List.<com.fabricmanagement.logistics.shipment.domain.ShipmentLine>of()
            : shipment.getLines();
    var ids =
        rows.stream()
            .map(com.fabricmanagement.logistics.shipment.domain.ShipmentLine::getSalesOrderLineId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    var limits = sales.lockExactLimits(ids);
    for (var entry : limits.entrySet()) {
      var limit = entry.getValue();
      if (limit.physicalWorkPending())
        throw LogisticsDomainException.rule(
            "EXACT_CUT_NOT_VERIFIED",
            "Verify the physical delivery pieces before dispatching this exact order line");
      var expectedUnit = "M".equals(limit.canonicalUnit()) ? ShipmentUnit.METER : ShipmentUnit.KG;
      BigDecimal prior = BigDecimal.ZERO;
      for (var loaded :
          lines.dispatchedQuantities(
              TenantContext.requireTenantId(), entry.getKey(), shipment.getId())) {
        if (loaded.getUnit() != expectedUnit) throw incompatible();
        prior = prior.add(nonNegative(loaded.getQuantity()));
      }
      for (var row : rows) {
        if (!entry.getKey().equals(row.getSalesOrderLineId())) continue;
        if (row.getUnit() != expectedUnit) throw incompatible();
        prior = prior.add(nonNegative(row.totalLoadedQuantity()));
      }
      if (prior.compareTo(limit.canonicalQuantity()) > 0)
        throw LogisticsDomainException.rule(
            "EXACT_QUANTITY_EXCEEDED",
            "The combined dispatched quantity exceeds the exact customer quantity");
    }
  }

  private BigDecimal nonNegative(BigDecimal quantity) {
    if (quantity == null || quantity.signum() < 0)
      throw LogisticsDomainException.rule(
          "EXACT_QUANTITY_MEASUREMENT_INVALID", "Dispatch requires a valid measured quantity");
    return quantity;
  }

  private LogisticsDomainException incompatible() {
    return LogisticsDomainException.rule(
        "EXACT_QUANTITY_UNIT_UNKNOWN",
        "Dispatch needs a measured quantity in the agreed dimension");
  }
}
