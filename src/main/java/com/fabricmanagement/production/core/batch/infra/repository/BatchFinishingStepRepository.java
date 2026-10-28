package com.fabricmanagement.production.core.batch.infra.repository;

import com.fabricmanagement.production.core.batch.domain.BatchFinishingStep;
import com.fabricmanagement.production.core.batch.domain.BatchFinishingStepStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchFinishingStepRepository extends JpaRepository<BatchFinishingStep, UUID> {

  Optional<BatchFinishingStep> findByTenantIdAndIdAndIsActiveTrue(UUID tenantId, UUID id);

  List<BatchFinishingStep> findByTenantIdAndBatchIdAndIsActiveTrueOrderBySequenceAsc(
      UUID tenantId, UUID batchId);

  boolean existsByTenantIdAndBatchIdAndStatusAndIsActiveTrue(
      UUID tenantId, UUID batchId, BatchFinishingStepStatus status);

  @Query(
      "select coalesce(max(s.sequence), 0) from BatchFinishingStep s "
          + "where s.tenantId = :tenantId and s.batchId = :batchId")
  int lastSequence(@Param("tenantId") UUID tenantId, @Param("batchId") UUID batchId);

  /**
   * Steps in a status, newest send first, optionally narrowed to one firm and to a job number. The
   * pattern is a lower-case SQL pattern the caller builds ({@code %} for any); steps without a job
   * number match only the bare {@code %}. The "what is at the dyehouse" list reads this with SENT.
   */
  default List<BatchFinishingStep> search(
      UUID tenantId,
      BatchFinishingStepStatus status,
      UUID partnerId,
      String jobPattern,
      Pageable pageable) {
    return searchSteps(tenantId, status, partnerId == null, partnerId, jobPattern, pageable);
  }

  @Query(
      """
      select s from BatchFinishingStep s
      where s.tenantId = :tenantId and s.isActive = true and s.status = :status
        and (:anyPartner = true or s.partnerId = :partnerId)
        and lower(coalesce(s.firmJobNumber, '')) like :jobPattern
      order by s.sentOn desc, s.sequence desc
      """)
  List<BatchFinishingStep> searchSteps(
      @Param("tenantId") UUID tenantId,
      @Param("status") BatchFinishingStepStatus status,
      @Param("anyPartner") boolean anyPartner,
      @Param("partnerId") UUID partnerId,
      @Param("jobPattern") String jobPattern,
      Pageable pageable);
}
