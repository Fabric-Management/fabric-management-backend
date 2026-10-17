package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.OrderDelivery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderDeliveryRepository extends JpaRepository<OrderDelivery, UUID> {

  List<OrderDelivery> findByTenantIdAndSalesOrderIdOrderBySequenceNoAsc(
      UUID tenantId, UUID salesOrderId);

  Optional<OrderDelivery> findByTenantIdAndSalesOrderIdAndId(
      UUID tenantId, UUID salesOrderId, UUID id);

  /**
   * The address cards this tenant's deliveries to a consignee were copied from, the most used first
   * and, among equally used cards, the one used last. A delivery counts when its consignee is the
   * partner itself or, as "the customer", the customer of its order; deliveries of removed orders
   * do not count.
   */
  @Query(
      "select d.shipTo.sourceAddressId from OrderDelivery d"
          + " where d.tenantId = :tenantId and d.shipTo.sourceAddressId is not null"
          + " and ((d.consigneeMode = com.fabricmanagement.sales.salesorder.domain.PartyMode.PARTNER"
          + " and d.consigneePartnerId = :partnerId)"
          + " or (d.consigneeMode = com.fabricmanagement.sales.salesorder.domain.PartyMode.CUSTOMER"
          + " and d.salesOrderId in (select c.id from SalesOrder c"
          + " where c.tenantId = :tenantId and c.tradingPartnerId = :partnerId)))"
          + " and d.salesOrderId in (select o.id from SalesOrder o"
          + " where o.tenantId = :tenantId and o.isActive = true)"
          + " group by d.shipTo.sourceAddressId"
          + " order by count(d) desc, max(d.createdAt) desc")
  List<UUID> shippedToCards(@Param("tenantId") UUID tenantId, @Param("partnerId") UUID partnerId);
}
