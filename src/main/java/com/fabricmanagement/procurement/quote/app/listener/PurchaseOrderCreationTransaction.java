package com.fabricmanagement.procurement.quote.app.listener;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.procurement.purchaseorder.app.PurchaseOrderService;
import com.fabricmanagement.procurement.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.fabricmanagement.procurement.purchaseorder.dto.PurchaseOrderResponse;
import com.fabricmanagement.procurement.purchaseorder.infra.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Isolates automatic purchase-order creation so a race rollback cannot poison the listener. */
@Component
@RequiredArgsConstructor
public class PurchaseOrderCreationTransaction {

  private final PurchaseOrderService purchaseOrderService;
  private final PurchaseOrderRepository purchaseOrderRepository;
  private final TenantSessionBinder tenantSessionBinder;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public PurchaseOrderResponse createAndFlush(CreatePurchaseOrderRequest request) {
    tenantSessionBinder.bindToCurrentSession(TenantContext.requireTenantId());
    PurchaseOrderResponse response = purchaseOrderService.createPurchaseOrder(request);
    purchaseOrderRepository.flush();
    return response;
  }
}
