package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.OrderFieldChange;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only field history of safe-edit saves; nothing here updates or deletes a row. */
public interface OrderFieldChangeRepository extends JpaRepository<OrderFieldChange, UUID> {

  List<OrderFieldChange> findByTenantIdAndSalesOrderIdOrderByChangedAtAscIdAsc(
      UUID tenantId, UUID salesOrderId);

  List<OrderFieldChange> findByTenantIdAndOperationIdOrderByChangedAtAscIdAsc(
      UUID tenantId, UUID operationId);
}
