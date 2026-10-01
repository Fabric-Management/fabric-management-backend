package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.OrderWorkAssignmentEvent;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkKind;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderWorkAssignmentEventRepository
    extends JpaRepository<OrderWorkAssignmentEvent, UUID> {

  List<OrderWorkAssignmentEvent> findByTenantIdAndSalesOrderIdAndKindOrderByOccurredAtDesc(
      UUID tenantId, UUID salesOrderId, OrderWorkKind kind);
}
