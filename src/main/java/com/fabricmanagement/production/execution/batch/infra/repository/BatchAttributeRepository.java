package com.fabricmanagement.production.execution.batch.infra.repository;

import com.fabricmanagement.production.execution.batch.domain.BatchAttribute;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchAttributeRepository extends JpaRepository<BatchAttribute, UUID> {

  List<BatchAttribute> findByBatch_IdAndIsActiveTrue(UUID batchId);

  List<BatchAttribute> findByBatch_Id(UUID batchId);

  Optional<BatchAttribute> findByBatch_IdAndAttribute_Id(UUID batchId, UUID attributeId);

  Optional<BatchAttribute> findByIdAndBatch_IdAndTenantId(UUID id, UUID batchId, UUID tenantId);

  @Query(
      """
      SELECT ba
      FROM BatchAttribute ba
      JOIN FETCH ba.batch b
      JOIN FETCH ba.attribute a
      WHERE b.id IN :batchIds
        AND ba.isActive = true
        AND a.isActive = true
        AND UPPER(a.attributeCode) IN :attributeCodes
      """)
  List<BatchAttribute> findActiveColourAttributesByBatchIds(
      @Param("batchIds") List<UUID> batchIds,
      @Param("attributeCodes") java.util.Set<String> attributeCodes);
}
