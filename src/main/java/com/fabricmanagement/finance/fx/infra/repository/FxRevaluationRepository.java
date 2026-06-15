package com.fabricmanagement.finance.fx.infra.repository;

import com.fabricmanagement.finance.fx.domain.FxRevaluation;
import com.fabricmanagement.finance.fx.domain.FxRevaluationEntryType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FxRevaluationRepository extends JpaRepository<FxRevaluation, UUID> {

  List<FxRevaluation> findByTenantIdAndPeriodIdOrderByCreatedAtAsc(UUID tenantId, UUID periodId);

  @Query(
      "SELECT f FROM FxRevaluation f WHERE f.tenantId = :tenantId "
          + "AND f.periodId = :periodId "
          + "AND f.entryType = :entryType "
          + "AND NOT EXISTS ("
          + "  SELECT r FROM FxRevaluation r "
          + "  WHERE r.tenantId = :tenantId AND r.reversalOfId = f.id"
          + ")")
  List<FxRevaluation> findUnreversedEntriesForPeriodByType(
      @Param("tenantId") UUID tenantId,
      @Param("periodId") UUID periodId,
      @Param("entryType") FxRevaluationEntryType entryType);

  @Query(
      "SELECT f FROM FxRevaluation f WHERE f.tenantId = :tenantId "
          + "AND f.periodId = :periodId "
          + "AND NOT EXISTS ("
          + "  SELECT r FROM FxRevaluation r "
          + "  WHERE r.tenantId = :tenantId AND r.reversalOfId = f.id"
          + ")")
  List<FxRevaluation> findUnreversedEntriesForPeriod(
      @Param("tenantId") UUID tenantId, @Param("periodId") UUID periodId);

  @Query(
      "SELECT f FROM FxRevaluation f WHERE f.tenantId = :tenantId "
          + "AND f.asOfDate <= :asOfDate "
          + "ORDER BY f.asOfDate ASC, f.createdAt ASC")
  List<FxRevaluation> findPositionEntriesThroughAsOfDate(
      @Param("tenantId") UUID tenantId, @Param("asOfDate") LocalDate asOfDate);

  boolean existsByTenantIdAndReversalOfId(UUID tenantId, UUID reversalOfId);

  @Query(
      "SELECT COALESCE(SUM(f.unrealizedGainLoss), 0) FROM FxRevaluation f "
          + "WHERE f.tenantId = :tenantId AND f.periodId = :periodId")
  BigDecimal sumPeriodMovement(@Param("tenantId") UUID tenantId, @Param("periodId") UUID periodId);
}
