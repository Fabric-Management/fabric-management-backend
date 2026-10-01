package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.DeliveryProposal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeliveryProposalRepository extends JpaRepository<DeliveryProposal, UUID> {

  /** The planner's latest proposal: the one that counts. */
  Optional<DeliveryProposal> findFirstByTenantIdAndSalesOrderIdOrderBySequenceDesc(
      UUID tenantId, UUID salesOrderId);

  List<DeliveryProposal> findByTenantIdAndSalesOrderIdInOrderBySequenceDesc(
      UUID tenantId, Collection<UUID> salesOrderIds);
}
