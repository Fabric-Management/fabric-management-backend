package com.fabricmanagement.finance.fx.infra.repository;

import com.fabricmanagement.finance.fx.domain.FxRealization;
import com.fabricmanagement.finance.fx.domain.FxRealizationSourceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FxRealizationRepository extends JpaRepository<FxRealization, UUID> {

  List<FxRealization> findByTenantIdAndSourceTypeAndSourceIdAndReversalOfIdIsNull(
      UUID tenantId, FxRealizationSourceType sourceType, UUID sourceId);

  Optional<FxRealization>
      findFirstByTenantIdAndSourceTypeAndSourceIdAndReversalOfIdIsNullOrderByCreatedAtAsc(
          UUID tenantId, FxRealizationSourceType sourceType, UUID sourceId);

  boolean existsByTenantIdAndReversalOfId(UUID tenantId, UUID reversalOfId);

  @Query(
      "SELECT COALESCE(SUM(f.realizedGainLoss), 0) FROM FxRealization f "
          + "WHERE f.tenantId = :tenantId AND f.sourceType = :sourceType AND f.sourceId = :sourceId")
  BigDecimal sumRealizedGainLoss(
      @Param("tenantId") UUID tenantId,
      @Param("sourceType") FxRealizationSourceType sourceType,
      @Param("sourceId") UUID sourceId);

  List<FxRealization> findByTenantIdAndRealizedAtBetween(
      UUID tenantId, Instant fromDate, Instant toDate);
}
