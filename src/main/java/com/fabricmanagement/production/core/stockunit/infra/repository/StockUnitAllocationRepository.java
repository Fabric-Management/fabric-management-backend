package com.fabricmanagement.production.core.stockunit.infra.repository;

import com.fabricmanagement.production.core.stockunit.domain.StockUnitAllocation;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitAllocationStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockUnitAllocationRepository extends JpaRepository<StockUnitAllocation, UUID> {

  List<StockUnitAllocation> findByTenantIdAndBatchIdInAndStatus(
      UUID tenantId, Collection<UUID> batchIds, StockUnitAllocationStatus status);

  List<StockUnitAllocation> findByTenantIdAndStockUnitIdInAndStatus(
      UUID tenantId, Collection<UUID> stockUnitIds, StockUnitAllocationStatus status);

  List<StockUnitAllocation> findByTenantIdAndSalesOrderLineIdAndStatusOrderByAllocatedAtAscIdAsc(
      UUID tenantId, UUID salesOrderLineId, StockUnitAllocationStatus status);

  List<StockUnitAllocation> findByTenantIdAndSalesOrderIdAndStatusOrderByAllocatedAtAscIdAsc(
      UUID tenantId, UUID salesOrderId, StockUnitAllocationStatus status);
}
