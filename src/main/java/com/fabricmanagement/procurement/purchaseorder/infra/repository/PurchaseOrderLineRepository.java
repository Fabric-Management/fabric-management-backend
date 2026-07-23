package com.fabricmanagement.procurement.purchaseorder.infra.repository;

import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderLine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {

  List<PurchaseOrderLine> findByPurchaseOrderIdAndIsActiveTrueOrderByCreatedAtAsc(
      UUID purchaseOrderId);

  List<PurchaseOrderLine> findByTenantIdAndPurchaseOrderIdAndIsActiveTrueOrderByCreatedAtAsc(
      UUID tenantId, UUID purchaseOrderId);

  Optional<PurchaseOrderLine> findByIdAndTenantIdAndPurchaseOrderIdAndIsActiveTrue(
      UUID id, UUID tenantId, UUID purchaseOrderId);
}
