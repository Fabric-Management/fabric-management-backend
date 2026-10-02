package com.fabricmanagement.sales.orderintake.infra.repository;

import com.fabricmanagement.sales.orderintake.domain.QuantityProposal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuantityProposalRepository extends JpaRepository<QuantityProposal, UUID> {

  Optional<QuantityProposal> findFirstByTenantIdAndSalesOrderLineIdOrderByEvaluatedAtDescIdDesc(
      UUID tenantId, UUID salesOrderLineId);

  Optional<QuantityProposal> findByTenantIdAndIdAndSalesOrderLineId(
      UUID tenantId, UUID id, UUID salesOrderLineId);
}
