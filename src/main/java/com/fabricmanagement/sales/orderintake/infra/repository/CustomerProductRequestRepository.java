package com.fabricmanagement.sales.orderintake.infra.repository;

import com.fabricmanagement.sales.orderintake.domain.CustomerProductRequest;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerProductRequestRepository
    extends JpaRepository<CustomerProductRequest, UUID> {

  Optional<CustomerProductRequest> findByTenantIdAndIdAndIsActiveTrue(UUID tenantId, UUID id);

  List<CustomerProductRequest>
      findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByRecordedAtAscIdAsc(
          UUID tenantId, UUID salesOrderId);

  List<CustomerProductRequest>
      findByTenantIdAndCustomerIdAndSalesOrderIdIsNullAndStatusNotInAndIsActiveTrueOrderByRecordedAtAsc(
          UUID tenantId, UUID customerId, Collection<CustomerRequestStatus> statuses);

  List<CustomerProductRequest> findByTenantIdAndStatusInAndIsActiveTrueOrderByRecordedAtAscIdAsc(
      UUID tenantId, Collection<CustomerRequestStatus> statuses);

  List<CustomerProductRequest> findByTenantIdAndOriginOrderIdAndStatusNotInAndIsActiveTrue(
      UUID tenantId, UUID originOrderId, Collection<CustomerRequestStatus> statuses);

  List<CustomerProductRequest> findByTenantIdAndResolvedLineIdInAndIsActiveTrue(
      UUID tenantId, Collection<UUID> lineIds);
}
