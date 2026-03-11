package com.fabricmanagement.production.execution.batch.infra.repository;

import com.fabricmanagement.production.execution.batch.domain.BatchCertification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchCertificationRepository extends JpaRepository<BatchCertification, UUID> {

  List<BatchCertification> findByBatch_IdAndIsActiveTrue(UUID batchId);

  List<BatchCertification> findByBatch_Id(UUID batchId);
}
