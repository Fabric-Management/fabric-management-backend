package com.fabricmanagement.production.core.batch.infra.repository;

import com.fabricmanagement.production.core.batch.domain.BatchFinishedWidthMeasurement;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchFinishedWidthMeasurementRepository
    extends JpaRepository<BatchFinishedWidthMeasurement, UUID> {

  List<BatchFinishedWidthMeasurement> findByTenantIdAndBatchIdInAndIsActiveTrue(
      UUID tenantId, Collection<UUID> batchIds);

  List<BatchFinishedWidthMeasurement> findByTenantIdAndBatchIdAndIsActiveTrueOrderByMeasuredAtDesc(
      UUID tenantId, UUID batchId);
}
