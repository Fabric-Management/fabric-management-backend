package com.fabricmanagement.procurement.purchaseorder.app.adapter;

import com.fabricmanagement.procurement.purchaseorder.infra.repository.PurchaseOrderLineRepository;
import com.fabricmanagement.product.yarn.app.port.YarnUsageDiscovery;
import com.fabricmanagement.product.yarn.app.port.YarnUsageSignal;
import com.fabricmanagement.product.yarn.app.port.YarnUsageSignalSource;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProcurementYarnUsageAdapter implements YarnUsageSignalSource {

  private final PurchaseOrderLineRepository purchaseOrderLineRepository;

  @Override
  public YarnUsageDiscovery discover(UUID tenantId) {
    return new YarnUsageDiscovery(
        Map.of(
            YarnUsageSignal.OPEN_PURCHASE_ORDER,
            Set.copyOf(purchaseOrderLineRepository.findOpenReferencedProductIds(tenantId))),
        Map.of(
            YarnUsageSignal.OPEN_PURCHASE_ORDER,
            purchaseOrderLineRepository.countOpenUnlinkedYarnPurchaseOrders(tenantId)));
  }
}
