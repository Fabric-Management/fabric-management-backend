package com.fabricmanagement.production.quality.decision.infra.repository;

import com.fabricmanagement.production.quality.decision.domain.QualityDecision;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QualityDecisionRepository extends JpaRepository<QualityDecision, UUID> {

  @Query(
      """
      SELECT COALESCE(MAX(d.seq), 0)
      FROM QualityDecision d
      WHERE d.tenantId = :tenantId AND d.batchId = :batchId
      """)
  long findMaxSeq(@Param("tenantId") UUID tenantId, @Param("batchId") UUID batchId);

  Optional<QualityDecision> findByIdAndTenantId(UUID id, UUID tenantId);

  Optional<QualityDecision> findByTenantIdAndSourceEventId(UUID tenantId, UUID sourceEventId);

  Optional<QualityDecision> findFirstByTenantIdAndBatchIdOrderByDecidedAtDescSeqDesc(
      UUID tenantId, UUID batchId);

  Page<QualityDecision> findByTenantIdAndBatchIdOrderByDecidedAtDescSeqDesc(
      UUID tenantId, UUID batchId, Pageable pageable);
}
