package com.fabricmanagement.production.core.output.infra.repository;

import com.fabricmanagement.production.core.output.domain.ProductionOutputItem;
import com.fabricmanagement.production.core.output.domain.ProductionOutputStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductionOutputItemRepository extends JpaRepository<ProductionOutputItem, UUID> {

  boolean existsByTenantIdAndBarcodeAndIsActiveTrue(UUID tenantId, String barcode);

  @Query(
      "SELECT COUNT(i) FROM ProductionOutputItem i WHERE i.tenantId = :tenantId AND i.record.workOrderId = :workOrderId AND i.record.status = :status AND i.isActive = true")
  int countConfirmedItemsByWorkOrderId(
      @Param("tenantId") UUID tenantId,
      @Param("workOrderId") UUID workOrderId,
      @Param("status") ProductionOutputStatus status);
}
