package com.fabricmanagement.product.yarn.infra.repository;

import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillQueueStatus;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillReconciliation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface YarnBackfillReconciliationRepository
    extends JpaRepository<YarnBackfillReconciliation, UUID> {

  List<YarnBackfillReconciliation> findByTenantIdAndStatus(
      UUID tenantId, YarnBackfillQueueStatus status);
}
