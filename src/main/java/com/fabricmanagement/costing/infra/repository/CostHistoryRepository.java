package com.fabricmanagement.costing.infra.repository;

import com.fabricmanagement.costing.domain.currency.CostHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** JPA repository for {@link CostHistory}. */
public interface CostHistoryRepository extends JpaRepository<CostHistory, UUID> {

  @Query(
      """
      SELECT ch FROM CostHistory ch
      WHERE ch.tenantId = :tenantId
        AND ch.costItemCode = :costItemCode
        AND (:materialId IS NULL OR ch.materialId = :materialId)
      ORDER BY ch.validFrom DESC
      """)
  List<CostHistory> findHistory(
      @Param("tenantId") UUID tenantId,
      @Param("costItemCode") String costItemCode,
      @Param("materialId") UUID materialId);
}
