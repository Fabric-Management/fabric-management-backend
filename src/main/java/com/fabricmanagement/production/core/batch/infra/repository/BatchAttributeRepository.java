package com.fabricmanagement.production.core.batch.infra.repository;

import com.fabricmanagement.production.core.batch.domain.BatchAttribute;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchAttributeRepository extends JpaRepository<BatchAttribute, UUID> {

  List<BatchAttribute> findByBatch_IdAndIsActiveTrue(UUID batchId);

  List<BatchAttribute> findByBatch_Id(UUID batchId);

  Optional<BatchAttribute> findByBatch_IdAndAttribute_Id(UUID batchId, UUID attributeId);

  Optional<BatchAttribute> findByIdAndBatch_IdAndTenantId(UUID id, UUID batchId, UUID tenantId);
}
