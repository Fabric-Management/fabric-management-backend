package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.CustomerApproval;
import com.fabricmanagement.sales.salesorder.domain.CustomerApprovalStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerApprovalRepository extends JpaRepository<CustomerApproval, UUID> {

  /** The latest request of the order, whatever its state. */
  Optional<CustomerApproval> findFirstByTenantIdAndSalesOrderIdOrderByRequestedAtDesc(
      UUID tenantId, UUID salesOrderId);

  List<CustomerApproval> findByTenantIdAndSalesOrderIdOrderByRequestedAtDesc(
      UUID tenantId, UUID salesOrderId);

  Optional<CustomerApproval> findByTenantIdAndId(UUID tenantId, UUID id);

  List<CustomerApproval> findByTenantIdAndSalesOrderIdAndStatusIn(
      UUID tenantId, UUID salesOrderId, Collection<CustomerApprovalStatus> statuses);

  Optional<CustomerApproval> findByTenantIdAndTokenHash(UUID tenantId, String tokenHash);

  Optional<CustomerApproval> findByTenantIdAndInternalApprovalRequestId(
      UUID tenantId, UUID internalApprovalRequestId);

  /** Change requests sales has not followed up yet, oldest first. */
  List<CustomerApproval> findByTenantIdAndStatusAndChangesResolvedAtIsNullOrderByDecidedAtAsc(
      UUID tenantId, CustomerApprovalStatus status);
}
