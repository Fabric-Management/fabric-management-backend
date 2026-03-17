package com.fabricmanagement.costing.infra.repository;

import com.fabricmanagement.costing.domain.price.PriceList;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** JPA repository for {@link PriceList}. */
public interface PriceListRepository extends JpaRepository<PriceList, UUID> {

  /**
   * Find the active price list for a given tenant + module + date. Returns the most recently
   * started list (latest validFrom ≤ date).
   */
  @Query(
      """
      SELECT pl FROM PriceList pl
      WHERE pl.tenantId = :tenantId
        AND pl.moduleType = :moduleType
        AND pl.isActive = true
        AND pl.validFrom <= :onDate
        AND (pl.validUntil IS NULL OR pl.validUntil >= :onDate)
      ORDER BY pl.validFrom DESC
      """)
  Optional<PriceList> findActiveForModule(
      @Param("tenantId") UUID tenantId,
      @Param("moduleType") String moduleType,
      @Param("onDate") LocalDate onDate);

  List<PriceList> findByTenantIdAndModuleTypeAndIsActiveTrueOrderByValidFromDesc(
      UUID tenantId, String moduleType);
}
