package com.fabricmanagement.production.execution.batch.infra.repository;

import com.fabricmanagement.production.execution.batch.domain.BatchCertification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchCertificationRepository extends JpaRepository<BatchCertification, UUID> {

  List<BatchCertification> findByBatch_Id(UUID batchId);

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
}
