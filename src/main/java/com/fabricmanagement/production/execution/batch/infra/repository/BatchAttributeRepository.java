package com.fabricmanagement.production.execution.batch.infra.repository;

import com.fabricmanagement.production.execution.batch.domain.BatchAttribute;
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
}
