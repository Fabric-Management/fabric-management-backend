package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.OrderCoverEvidence;
import java.util.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderCoverEvidenceRepository extends JpaRepository<OrderCoverEvidence, UUID> {
  Optional<OrderCoverEvidence> findFirstByTenantIdAndCaseIdOrderByRevisionDesc(
      UUID tenantId, UUID caseId);

  Optional<OrderCoverEvidence> findByTenantIdAndSalesOrderIdAndId(
      UUID tenantId, UUID salesOrderId, UUID id);

  List<OrderCoverEvidence> findAllByTenantIdAndCaseIdInOrderByCaseIdAscRevisionDesc(
      UUID tenantId, Collection<UUID> caseIds);
}
