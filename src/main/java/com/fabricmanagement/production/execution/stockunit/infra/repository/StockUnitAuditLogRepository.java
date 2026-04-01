package com.fabricmanagement.production.execution.stockunit.infra.repository;

import com.fabricmanagement.production.execution.stockunit.domain.StockUnitAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockUnitAuditLogRepository extends JpaRepository<StockUnitAuditLog, UUID> {

  List<StockUnitAuditLog> findByTenantIdAndStockUnitIdOrderByCreatedAtAsc(
      UUID tenantId, UUID stockUnitId);
}
