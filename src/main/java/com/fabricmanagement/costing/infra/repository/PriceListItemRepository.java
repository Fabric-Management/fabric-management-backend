package com.fabricmanagement.costing.infra.repository;

import com.fabricmanagement.costing.domain.price.PriceListItem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** JPA repository for {@link PriceListItem}. */
public interface PriceListItemRepository extends JpaRepository<PriceListItem, UUID> {

  /**
   * Resolve the best-matching PriceListItem for a cost item, applying a 4-level priority fallback:
   *
   * <ol>
   *   <li>Supplier-specific + material-specific (highest priority)
   *   <li>Supplier-specific (any material)
   *   <li>General (no supplier) + material-specific
   *   <li>General (no supplier, no material) — catch-all
   * </ol>
   *
   * <p>Uses native SQL (not JPQL) because {@code CASE WHEN … IS NOT NULL} ordering is unreliable in
   * Hibernate 6 JPQL — the translated HQL may produce incorrect ORDER BY expressions depending on
   * the dialect.
   *
   * <p>Note: {@code volumeBreaks} will be lazy-loaded when accessed (transaction must be open).
   */
  @Query(
      value =
          """
          SELECT pli.*
          FROM   costing.price_list_item pli
          WHERE  pli.price_list_id  = :priceListId
            AND  pli.cost_item_code = :costItemCode
            AND  pli.is_active      = true
            AND  (CAST(:materialId AS uuid) IS NULL
                    OR pli.material_id IS NULL
                    OR pli.material_id = CAST(:materialId AS uuid))
            AND  (CAST(:tradingPartnerId AS uuid) IS NULL
                    OR pli.trading_partner_id IS NULL
                    OR pli.trading_partner_id = CAST(:tradingPartnerId AS uuid))
          ORDER BY
            -- Most-specific wins: supplier-specific rows first, then material-specific rows
            (CASE WHEN pli.trading_partner_id IS NOT NULL THEN 0 ELSE 1 END),
            (CASE WHEN pli.material_id        IS NOT NULL THEN 0 ELSE 1 END)
          LIMIT 1
          """,
      nativeQuery = true)
  Optional<PriceListItem> findBest(
      @Param("priceListId") UUID priceListId,
      @Param("costItemCode") String costItemCode,
      @Param("materialId") UUID materialId,
      @Param("tradingPartnerId") UUID tradingPartnerId);
}
