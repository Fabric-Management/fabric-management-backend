package com.fabricmanagement.procurement.purchaseorder.infra.repository;

import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderLine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {

  @Query(
      value =
          """
          SELECT DISTINCT line.product_id
          FROM procurement.purchase_order_line line
          JOIN procurement.purchase_order purchase_order
            ON purchase_order.id = line.purchase_order_id
           AND purchase_order.tenant_id = line.tenant_id
          WHERE line.tenant_id = :tenantId
            AND line.is_active = TRUE
            AND purchase_order.is_active = TRUE
            AND purchase_order.status NOT IN ('CLOSED', 'CANCELLED')
            AND line.product_id IS NOT NULL
          """,
      nativeQuery = true)
  List<UUID> findOpenReferencedProductIds(@Param("tenantId") UUID tenantId);

  @Query(
      value =
          """
          SELECT count(DISTINCT line.purchase_order_id)
          FROM procurement.purchase_order_line line
          JOIN procurement.purchase_order purchase_order
            ON purchase_order.id = line.purchase_order_id
           AND purchase_order.tenant_id = line.tenant_id
          WHERE line.tenant_id = :tenantId
            AND line.is_active = TRUE
            AND purchase_order.is_active = TRUE
            AND purchase_order.status NOT IN ('CLOSED', 'CANCELLED')
            AND line.product_id IS NULL
            AND line.module_specs ->> 'specType' = 'YARN'
          """,
      nativeQuery = true)
  long countOpenUnlinkedYarnPurchaseOrders(@Param("tenantId") UUID tenantId);

  List<PurchaseOrderLine> findByTenantIdAndIsActiveTrue(UUID tenantId);

  List<PurchaseOrderLine> findByPurchaseOrderIdAndIsActiveTrueOrderByCreatedAtAsc(
      UUID purchaseOrderId);

  List<PurchaseOrderLine> findByTenantIdAndPurchaseOrderIdAndIsActiveTrueOrderByCreatedAtAsc(
      UUID tenantId, UUID purchaseOrderId);

  Optional<PurchaseOrderLine> findByIdAndTenantIdAndPurchaseOrderIdAndIsActiveTrue(
      UUID id, UUID tenantId, UUID purchaseOrderId);
}
