package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.OrderCoverCaseLine;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OrderCoverCaseLineRepository extends JpaRepository<OrderCoverCaseLine, UUID> {
  List<OrderCoverCaseLine> findAllByTenantIdAndCaseIdOrderBySalesOrderLineId(
      UUID tenantId, UUID caseId);

  List<OrderCoverCaseLine> findAllByTenantIdAndCaseIdIn(UUID tenantId, Collection<UUID> caseIds);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select l from OrderCoverCaseLine l where l.tenantId=:tenantId and l.caseId=:caseId order by l.salesOrderLineId")
  List<OrderCoverCaseLine> lockAll(@Param("tenantId") UUID tenantId, @Param("caseId") UUID caseId);
}
