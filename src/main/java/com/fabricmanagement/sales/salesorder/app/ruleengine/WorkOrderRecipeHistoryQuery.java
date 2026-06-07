package com.fabricmanagement.sales.salesorder.app.ruleengine;

import java.sql.Types;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Query helper for RuleEngine recipe matching steps 1–3.
 *
 * <p>Uses raw SQL for efficiency — the recipe-matching queries span multiple schemas (production,
 * WorkOrder history) and are optimised for index usage.
 */
@Component
@RequiredArgsConstructor
public class WorkOrderRecipeHistoryQuery {

  private final NamedParameterJdbcTemplate jdbc;

  /**
   * Step 1: Find the most recently created ACTIVE recipe associated with this product (via recipe
   * name containing the product, or through a direct FK — if productId is stored on the recipe).
   * For now, returns the most recently created ACTIVE recipe that references this productId in its
   * RecipeComponent table, optionally matching certification and origin.
   *
   * <p>Certification and origin values are expected to be pre-normalized to uppercase by the
   * caller.
   */
  public Optional<UUID> findDefaultRecipeForProduct(
      UUID tenantId, UUID productId, String certification, String origin) {
    String sql =
        """
        SELECT r.id
        FROM production.prod_recipe r
        JOIN production.prod_recipe_component rc ON rc.recipe_id = r.id
        WHERE r.tenant_id = :tenantId
          AND r.status = 'ACTIVE'
          AND r.is_active = true
          AND rc.is_active = true
          AND rc.fiber_id = :productId
          AND (:certification IS NULL OR rc.certification = :certification)
          AND (:origin IS NULL OR rc.origin = :origin)
        ORDER BY r.created_at DESC
        LIMIT 1
        """;

    return querySingleUuid(
        sql,
        new MapSqlParameterSource("tenantId", tenantId)
            .addValue("productId", productId)
            .addValue("certification", certification, Types.VARCHAR)
            .addValue("origin", origin, Types.VARCHAR));
  }

  /**
   * Step 2: Most recently used recipe for the same (customer, product) combination. Looks at
   * confirmed WorkOrders for this trading partner linked to the same product, optionally matching
   * certification and origin.
   *
   * <p>Certification and origin values are expected to be pre-normalized to uppercase by the
   * caller.
   */
  public Optional<UUID> findMostRecentRecipeForCustomerAndProduct(
      UUID tenantId, UUID tradingPartnerId, UUID productId, String certification, String origin) {
    String sql =
        """
        SELECT wo.recipe_id
        FROM production.prod_work_order wo
        JOIN production.prod_recipe_component rc ON rc.recipe_id = wo.recipe_id
        WHERE wo.tenant_id = :tenantId
          AND wo.trading_partner_id = :tradingPartnerId
          AND rc.is_active = true
          AND rc.fiber_id = :productId
          AND (:certification IS NULL OR rc.certification = :certification)
          AND (:origin IS NULL OR rc.origin = :origin)
          AND wo.recipe_id IS NOT NULL
          AND wo.is_active = true
        ORDER BY wo.created_at DESC
        LIMIT 1
        """;

    return querySingleUuid(
        sql,
        new MapSqlParameterSource("tenantId", tenantId)
            .addValue("tradingPartnerId", tradingPartnerId)
            .addValue("productId", productId)
            .addValue("certification", certification, Types.VARCHAR)
            .addValue("origin", origin, Types.VARCHAR));
  }

  /**
   * Step 3: Most frequently used ACTIVE recipe for this product (ranked by number of WorkOrders
   * using it), optionally matching certification and origin.
   *
   * <p>Certification and origin values are expected to be pre-normalized to uppercase by the
   * caller.
   */
  public Optional<UUID> findMostUsedRecipeForProduct(
      UUID tenantId, UUID productId, String certification, String origin) {
    String sql =
        """
        SELECT wo.recipe_id, COUNT(*) AS usage_count
        FROM production.prod_work_order wo
        JOIN production.prod_recipe r ON r.id = wo.recipe_id
        JOIN production.prod_recipe_component rc ON rc.recipe_id = wo.recipe_id
        WHERE wo.tenant_id = :tenantId
          AND r.status = 'ACTIVE'
          AND r.is_active = true
          AND rc.is_active = true
          AND rc.fiber_id = :productId
          AND (:certification IS NULL OR rc.certification = :certification)
          AND (:origin IS NULL OR rc.origin = :origin)
          AND wo.recipe_id IS NOT NULL
        GROUP BY wo.recipe_id
        ORDER BY usage_count DESC
        LIMIT 1
        """;

    return querySingleUuid(
        sql,
        new MapSqlParameterSource("tenantId", tenantId)
            .addValue("productId", productId)
            .addValue("certification", certification, Types.VARCHAR)
            .addValue("origin", origin, Types.VARCHAR));
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private Optional<UUID> querySingleUuid(String sql, MapSqlParameterSource params) {
    return jdbc.query(
        sql,
        params,
        rs -> {
          if (rs.next()) {
            String val = rs.getString(1);
            return val != null ? Optional.of(UUID.fromString(val)) : Optional.empty();
          }
          return Optional.empty();
        });
  }
}
