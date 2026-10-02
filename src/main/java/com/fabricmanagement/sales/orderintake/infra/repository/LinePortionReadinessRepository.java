package com.fabricmanagement.sales.orderintake.infra.repository;

import com.fabricmanagement.sales.orderintake.domain.CoverPortionKind;
import com.fabricmanagement.sales.orderintake.domain.LinePortionReadiness;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LinePortionReadinessRepository extends JpaRepository<LinePortionReadiness, UUID> {

  Optional<LinePortionReadiness> findFirstByTenantIdAndSalesOrderLineIdAndPortionAndStatusIn(
      UUID tenantId,
      UUID salesOrderLineId,
      CoverPortionKind portion,
      Collection<LinePortionReadiness.Status> statuses);

  List<LinePortionReadiness> findByTenantIdAndSalesOrderLineIdInAndStatusIn(
      UUID tenantId, Collection<UUID> lineIds, Collection<LinePortionReadiness.Status> statuses);

  List<LinePortionReadiness> findByTenantIdAndStatusAndPortionInOrderByRequestedAtAscIdAsc(
      UUID tenantId, LinePortionReadiness.Status status, Collection<CoverPortionKind> portions);
}
