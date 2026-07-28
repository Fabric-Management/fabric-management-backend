package com.fabricmanagement.sales.ownership.infra.repository;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.ownership.domain.OwnershipTriageCase;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class OwnershipTriageQueryRepository {

  static final String DERIVED_TRIAGE_CTE =
      """
      WITH latest_assignment_closure AS (
          SELECT assignment.tenant_id,
                 assignment.customer_id,
                 MAX(assignment.valid_to) AS latest_valid_to
          FROM sales.customer_commercial_assignment assignment
          WHERE assignment.tenant_id = :tenantId
            AND assignment.is_active = TRUE
            AND assignment.deleted_at IS NULL
          GROUP BY assignment.tenant_id, assignment.customer_id
      ),
      ownership_triage AS (
          SELECT partner.id AS customer_id,
                 CASE
                   WHEN NULLIF(BTRIM(partner.custom_name), '') IS NOT NULL
                     THEN partner.custom_name
                   ELSE registry.official_name
                 END AS customer_name,
                 COUNT(quote.id) AS unassigned_open_quote_count,
                 MIN(quote.created_at) AS oldest_unassigned_quote_at,
                 GREATEST(
                     partner.customer_relationship_established_at,
                     policy.mode_effective_at,
                     latest_assignment_closure.latest_valid_to
                 ) AS gap_started_at,
                 policy.triage_age_threshold_hours
          FROM common_company.common_trading_partner partner
          JOIN common_company.trading_partner_registry registry
            ON registry.id = partner.registry_id
          JOIN sales.ownership_policy policy
            ON policy.tenant_id = partner.tenant_id
           AND policy.is_active = TRUE
           AND policy.deleted_at IS NULL
          LEFT JOIN latest_assignment_closure
            ON latest_assignment_closure.tenant_id = partner.tenant_id
           AND latest_assignment_closure.customer_id = partner.id
          LEFT JOIN sales.quote quote
            ON quote.tenant_id = partner.tenant_id
           AND quote.customer_id = partner.id
           AND quote.assigned_to_id IS NULL
           AND quote.status IN ('DRAFT', 'EVALUATION', 'PENDING_APPROVAL', 'APPROVED')
           AND quote.is_active = TRUE
           AND quote.deleted_at IS NULL
          WHERE partner.tenant_id = :tenantId
            AND partner.partner_type IN ('CUSTOMER', 'BOTH')
            AND partner.status = 'ACTIVE'
            AND partner.is_active = TRUE
            AND partner.deleted_at IS NULL
            AND policy.default_mode = 'REQUIRED'
            AND NOT EXISTS (
                SELECT 1
                FROM sales.customer_commercial_assignment active_assignment
                JOIN common_user.common_user representative
                  ON representative.tenant_id = active_assignment.tenant_id
                 AND representative.id = active_assignment.representative_id
                 AND representative.is_active = TRUE
                 AND representative.deleted_at IS NULL
                WHERE active_assignment.tenant_id = partner.tenant_id
                  AND active_assignment.customer_id = partner.id
                  AND active_assignment.valid_to IS NULL
                  AND active_assignment.is_active = TRUE
                  AND active_assignment.deleted_at IS NULL
            )
          GROUP BY partner.id,
                   partner.custom_name,
                   registry.official_name,
                   partner.customer_relationship_established_at,
                   policy.mode_effective_at,
                   latest_assignment_closure.latest_valid_to,
                   policy.triage_age_threshold_hours
      )
      """;

  private static final String SELECT_PAGE_SQL =
      DERIVED_TRIAGE_CTE
          + """
          SELECT customer_id,
                 customer_name,
                 unassigned_open_quote_count,
                 oldest_unassigned_quote_at,
                 gap_started_at,
                 triage_age_threshold_hours
          FROM ownership_triage
          ORDER BY gap_started_at ASC, customer_id ASC
          LIMIT :limit OFFSET :offset
          """;

  private static final String SELECT_ALL_SQL =
      DERIVED_TRIAGE_CTE
          + """
          SELECT customer_id,
                 customer_name,
                 unassigned_open_quote_count,
                 oldest_unassigned_quote_at,
                 gap_started_at,
                 triage_age_threshold_hours
          FROM ownership_triage
          ORDER BY gap_started_at ASC, customer_id ASC
          """;

  private static final String COUNT_SQL =
      DERIVED_TRIAGE_CTE + "SELECT COUNT(*) FROM ownership_triage";

  private final NamedParameterJdbcTemplate jdbc;

  public Page<OwnershipTriageCase> findPage(UUID tenantId, Pageable pageable) {
    bindTenant(tenantId);
    Map<String, Object> params =
        Map.of(
            "tenantId", tenantId,
            "limit", pageable.getPageSize(),
            "offset", pageable.getOffset());
    List<OwnershipTriageCase> cases = jdbc.query(SELECT_PAGE_SQL, params, this::mapCase);
    Long count = jdbc.queryForObject(COUNT_SQL, Map.of("tenantId", tenantId), Long.class);
    return new PageImpl<>(cases, pageable, count == null ? 0 : count);
  }

  public List<OwnershipTriageCase> findAll(UUID tenantId) {
    bindTenant(tenantId);
    return jdbc.query(SELECT_ALL_SQL, Map.of("tenantId", tenantId), this::mapCase);
  }

  private void bindTenant(UUID tenantId) {
    UUID ambientTenantId = TenantContext.requireTenantId();
    if (!ambientTenantId.equals(tenantId)) {
      throw new IllegalStateException(
          "Triage query tenant mismatch: ambient=%s requested=%s"
              .formatted(ambientTenantId, tenantId));
    }
    jdbc.queryForObject(
        "SELECT set_config('app.current_tenant', :tenantId, true)",
        Map.of("tenantId", tenantId.toString()),
        String.class);
  }

  private OwnershipTriageCase mapCase(ResultSet rs, int rowNumber) throws SQLException {
    Timestamp oldestQuote = rs.getTimestamp("oldest_unassigned_quote_at");
    return new OwnershipTriageCase(
        rs.getObject("customer_id", UUID.class),
        rs.getString("customer_name"),
        rs.getLong("unassigned_open_quote_count"),
        oldestQuote == null ? null : oldestQuote.toInstant(),
        requiredInstant(rs, "gap_started_at"),
        rs.getInt("triage_age_threshold_hours"));
  }

  private Instant requiredInstant(ResultSet rs, String column) throws SQLException {
    return rs.getTimestamp(column).toInstant();
  }
}
