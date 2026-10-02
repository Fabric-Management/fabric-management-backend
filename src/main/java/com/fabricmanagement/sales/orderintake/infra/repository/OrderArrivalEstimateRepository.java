package com.fabricmanagement.sales.orderintake.infra.repository;

import com.fabricmanagement.sales.orderintake.domain.OrderArrivalEstimate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderArrivalEstimateRepository extends JpaRepository<OrderArrivalEstimate, UUID> {

  Optional<OrderArrivalEstimate> findFirstByTenantIdAndSalesOrderIdAndSupersededAtIsNull(
      UUID tenantId, UUID salesOrderId);
}
