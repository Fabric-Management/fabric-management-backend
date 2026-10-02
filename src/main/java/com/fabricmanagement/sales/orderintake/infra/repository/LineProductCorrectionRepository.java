package com.fabricmanagement.sales.orderintake.infra.repository;

import com.fabricmanagement.sales.orderintake.domain.LineProductCorrection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LineProductCorrectionRepository
    extends JpaRepository<LineProductCorrection, UUID> {

  List<LineProductCorrection> findByTenantIdAndSalesOrderIdOrderByCorrectedAtDescIdDesc(
      UUID tenantId, UUID salesOrderId);
}
