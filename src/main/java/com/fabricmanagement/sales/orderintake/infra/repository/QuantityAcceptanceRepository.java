package com.fabricmanagement.sales.orderintake.infra.repository;

import com.fabricmanagement.sales.orderintake.domain.QuantityAcceptance;
import com.fabricmanagement.sales.orderintake.domain.QuantityAcceptanceStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuantityAcceptanceRepository extends JpaRepository<QuantityAcceptance, UUID> {

  Optional<QuantityAcceptance> findFirstByTenantIdAndSalesOrderLineIdAndStatus(
      UUID tenantId, UUID salesOrderLineId, QuantityAcceptanceStatus status);

  List<QuantityAcceptance> findByTenantIdAndSalesOrderLineIdInAndStatus(
      UUID tenantId, Collection<UUID> salesOrderLineIds, QuantityAcceptanceStatus status);

  List<QuantityAcceptance> findByTenantIdAndSalesOrderLineIdOrderByRecordedAtDescIdDesc(
      UUID tenantId, UUID salesOrderLineId);

  Optional<QuantityAcceptance> findByTenantIdAndIdempotencyKey(
      UUID tenantId, String idempotencyKey);
}
