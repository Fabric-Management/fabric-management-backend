package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.OrderFlowEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderFlowEventRepository extends JpaRepository<OrderFlowEvent, UUID> {

  List<OrderFlowEvent> findByTenantIdAndSalesOrderIdOrderByOccurredAtDesc(
      UUID tenantId, UUID salesOrderId);
}
