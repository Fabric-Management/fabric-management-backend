package com.fabricmanagement.procurement.purchaseorder.infra.repository;

import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrder;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PurchaseOrderRepository
    extends JpaRepository<PurchaseOrder, UUID>, JpaSpecificationExecutor<PurchaseOrder> {

  Optional<PurchaseOrder> findByPoNumberAndIsActiveTrue(String poNumber);

  List<PurchaseOrder> findByWorkOrderIdAndIsActiveTrue(UUID workOrderId);

  List<PurchaseOrder> findByTradingPartnerIdAndIsActiveTrue(UUID tradingPartnerId);

  List<PurchaseOrder> findByStatusAndIsActiveTrue(PurchaseOrderStatus status);

  boolean existsByPoNumberAndIsActiveTrue(String poNumber);

  boolean existsByTenantIdAndSupplierQuoteIdAndIsActiveTrue(UUID tenantId, UUID supplierQuoteId);

  Optional<PurchaseOrder> findByTenantIdAndSupplierQuoteIdAndIsActiveTrue(
      UUID tenantId, UUID supplierQuoteId);

  Optional<PurchaseOrder> findByIdAndTenantIdAndIsActiveTrue(UUID id, UUID tenantId);
}
