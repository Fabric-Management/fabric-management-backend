package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.OrderDelivery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderDeliveryRepository extends JpaRepository<OrderDelivery, UUID> {

  List<OrderDelivery> findByTenantIdAndSalesOrderIdOrderBySequenceNoAsc(
      UUID tenantId, UUID salesOrderId);

  Optional<OrderDelivery> findByTenantIdAndSalesOrderIdAndId(
      UUID tenantId, UUID salesOrderId, UUID id);
}
