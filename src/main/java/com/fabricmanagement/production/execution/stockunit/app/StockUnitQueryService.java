package com.fabricmanagement.production.execution.stockunit.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.execution.stockunit.dto.StockUnitDto;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only queries for StockUnit subsystem. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockUnitQueryService {

  private final StockUnitRepository stockUnitRepository;

  /**
   * Look up a stock unit by its unique barcode within the tenant.
   *
   * @throws NotFoundException if not found
   */
  public StockUnitDto findByBarcode(String barcode) {
    UUID tenantId = TenantContext.requireTenantId();
    return stockUnitRepository
        .findByTenantIdAndBarcode(tenantId, barcode)
        .map(StockUnitDto::from)
        .orElseThrow(() -> new NotFoundException("StockUnit not found with barcode: " + barcode));
  }

  /** Gets all active and partially consumed stock units for a batch. */
  public List<StockUnitDto> findActiveByBatchId(UUID batchId) {
    UUID tenantId = TenantContext.requireTenantId();
    return stockUnitRepository
        .findByTenantIdAndBatchIdAndStatusIn(
            tenantId,
            batchId,
            List.of(StockUnitStatus.AVAILABLE, StockUnitStatus.PARTIAL, StockUnitStatus.RESERVED))
        .stream()
        .map(StockUnitDto::from)
        .toList();
  }

  /** Look up paginated stock units for a specific location. */
  public Page<StockUnitDto> findByLocationId(UUID locationId, Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();
    return stockUnitRepository
        .findByTenantIdAndLocationId(tenantId, locationId, pageable)
        .map(StockUnitDto::from);
  }
}
