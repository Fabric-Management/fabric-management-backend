package com.fabricmanagement.procurement.purchaseorder.infra.repository;

import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrder;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

  Optional<PurchaseOrder> findByPoNumberAndIsActiveTrue(String poNumber);

  List<PurchaseOrder> findByWorkOrderIdAndIsActiveTrue(UUID workOrderId);

  List<PurchaseOrder> findByTradingPartnerIdAndIsActiveTrue(UUID tradingPartnerId);

  List<PurchaseOrder> findByStatusAndIsActiveTrue(PurchaseOrderStatus status);

  boolean existsByPoNumberAndIsActiveTrue(String poNumber);

  boolean existsBySupplierQuoteIdAndIsActiveTrue(UUID supplierQuoteId);
}
