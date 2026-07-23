package com.fabricmanagement.procurement.purchaseorder.app.adapter;

import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderStatus;
import com.fabricmanagement.procurement.purchaseorder.infra.repository.PurchaseOrderRepository;
import com.fabricmanagement.production.execution.goodsreceipt.domain.port.PoReceivabilityPort;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Procurement-owned adapter that answers the goods-receipt receivability question. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseOrderReceivabilityAdapter implements PoReceivabilityPort {

  private static final Set<PurchaseOrderStatus> RECEIVABLE_STATUSES =
      Set.of(PurchaseOrderStatus.CONFIRMED, PurchaseOrderStatus.PARTIALLY_RECEIVED);

  private final PurchaseOrderRepository purchaseOrderRepository;

  @Override
  public boolean isReceivable(UUID tenantId, UUID purchaseOrderId) {
    return purchaseOrderRepository
        .findByIdAndTenantIdAndIsActiveTrue(purchaseOrderId, tenantId)
        .map(purchaseOrder -> RECEIVABLE_STATUSES.contains(purchaseOrder.getStatus()))
        .orElse(false);
  }
}
