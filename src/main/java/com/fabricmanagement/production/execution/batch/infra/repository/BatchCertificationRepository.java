package com.fabricmanagement.production.execution.batch.infra.repository;

import com.fabricmanagement.production.execution.batch.domain.BatchCertification;
import com.fabricmanagement.production.execution.batch.domain.BatchCertificationScope;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchCertificationRepository extends JpaRepository<BatchCertification, UUID> {

  List<BatchCertification> findByBatch_Id(UUID batchId);

  List<BatchCertification> findByBatch_IdAndIsActiveTrue(UUID batchId);

  /**
   * Loads batch certifications with associations in one query to avoid N+1 when mapping to DTOs.
   */
  @Query(
      "SELECT bc FROM BatchCertification bc "
          + "JOIN FETCH bc.batch "
          + "JOIN FETCH bc.certification "
          + "LEFT JOIN FETCH bc.partnerCertification "
          + "LEFT JOIN FETCH bc.orgCertification "
          + "WHERE bc.batch.id = :batchId AND bc.isActive = true")
  List<BatchCertification> findByBatch_IdAndIsActiveTrueWithAssociations(
      @Param("batchId") UUID batchId);

  /**
   * Active batch certifications for the same (batch, cert, scope, partner, org). Optional excludeId
   * to skip one record (e.g. when updating). Used for date-range overlap check.
   */
  @Query(
      "SELECT bc FROM BatchCertification bc "
          + "WHERE bc.batch.id = :batchId AND bc.certification.id = :certId AND bc.scope = :scope "
          + "AND bc.isActive = true "
          + "AND ((:partnerId IS NULL AND bc.partnerCertification IS NULL) OR (bc.partnerCertification IS NOT NULL AND bc.partnerCertification.id = :partnerId)) "
          + "AND ((:orgId IS NULL AND bc.orgCertification IS NULL) OR (bc.orgCertification IS NOT NULL AND bc.orgCertification.id = :orgId)) "
          + "AND (:excludeId IS NULL OR bc.id <> :excludeId)")
  List<BatchCertification> findActiveByBatchAndCertAndScopeAndPartnerAndOrgExcludingId(
      @Param("batchId") UUID batchId,
      @Param("certId") UUID certId,
      @Param("scope") BatchCertificationScope scope,
      @Param("partnerId") UUID partnerId,
      @Param("orgId") UUID orgId,
      @Param("excludeId") UUID excludeId);

  /**
   * Active batch certifications for a tenant whose validUntil is on or before the threshold
   * (expired or expiring within threshold). Used by expiry warning job.
   */
  @Query(
      "SELECT bc FROM BatchCertification bc WHERE bc.tenantId = :tenantId AND bc.isActive = true"
          + " AND bc.validUntil IS NOT NULL AND bc.validUntil <= :threshold")
  List<BatchCertification> findByTenantIdAndIsActiveTrueAndValidUntilBeforeOrOn(
      @Param("tenantId") UUID tenantId, @Param("threshold") LocalDate threshold);
}
