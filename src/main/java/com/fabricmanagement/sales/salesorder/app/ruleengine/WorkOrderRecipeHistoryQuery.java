package com.fabricmanagement.sales.salesorder.app.ruleengine;

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
   * RecipeComponent table.
   */
  public Optional<UUID> findDefaultRecipeForProduct(UUID productId) {
    String sql =
        """
        SELECT r.id
        FROM production.prod_recipe r
        JOIN production.prod_recipe_component rc ON rc.recipe_id = r.id
        WHERE r.status = 'ACTIVE'
          AND r.is_active = true
          AND rc.fiber_id = :productId
        ORDER BY r.created_at DESC
        LIMIT 1
        """;

    return querySingleUuid(sql, "productId", productId);
  }

  /**
   * Step 2: Most recently used recipe for the same (customer, product) combination. Looks at
   * confirmed WorkOrders for this trading partner linked to the same product.
   */
  public Optional<UUID> findMostRecentRecipeForCustomerAndProduct(
      UUID tradingPartnerId, UUID productId) {
    String sql =
        """
        SELECT wo.recipe_id
        FROM production.prod_work_order wo
        JOIN production.prod_recipe_component rc ON rc.recipe_id = wo.recipe_id
        WHERE wo.trading_partner_id = :tradingPartnerId
          AND rc.fiber_id = :productId
          AND wo.recipe_id IS NOT NULL
          AND wo.is_active = true
        ORDER BY wo.created_at DESC
        LIMIT 1
        """;

    return jdbc.query(
        sql,
        new MapSqlParameterSource("tradingPartnerId", tradingPartnerId)
            .addValue("productId", productId),
        rs ->
            rs.next() ? Optional.of(UUID.fromString(rs.getString("recipe_id"))) : Optional.empty());
  }

  /**
   * Step 3: Most frequently used ACTIVE recipe for this product (ranked by number of WorkOrders
   * using it).
   */
  public Optional<UUID> findMostUsedRecipeForProduct(UUID productId) {
    String sql =
        """
        SELECT wo.recipe_id, COUNT(*) AS usage_count
        FROM production.prod_work_order wo
        JOIN production.prod_recipe r ON r.id = wo.recipe_id
        JOIN production.prod_recipe_component rc ON rc.recipe_id = wo.recipe_id
        WHERE r.status = 'ACTIVE'
          AND r.is_active = true
          AND rc.fiber_id = :productId
          AND wo.recipe_id IS NOT NULL
        GROUP BY wo.recipe_id
        ORDER BY usage_count DESC
        LIMIT 1
        """;

    return querySingleUuid(sql, "productId", productId);
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private Optional<UUID> querySingleUuid(String sql, String paramName, UUID paramValue) {
    return jdbc.query(
        sql,
        new MapSqlParameterSource(paramName, paramValue),
        rs -> {
          if (rs.next()) {
            String val = rs.getString(1);
            return val != null ? Optional.of(UUID.fromString(val)) : Optional.empty();
          }
          return Optional.empty();
        });
  }
}
