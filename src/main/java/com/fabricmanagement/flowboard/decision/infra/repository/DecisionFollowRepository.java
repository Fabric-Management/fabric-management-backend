package com.fabricmanagement.flowboard.decision.infra.repository;

import com.fabricmanagement.flowboard.decision.domain.DecisionFollowSource;
import com.fabricmanagement.platform.user.domain.SystemUser;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tenant-scoped idempotent writes and the canonical effective-follow predicate. */
@Repository
public class DecisionFollowRepository {
  public static final String EFFECTIVE_FOLLOW_PREDICATE =
      """
      EXISTS (
        SELECT 1
        FROM flowboard.decision_follow f
        WHERE f.tenant_id = :tenant AND f.case_id = c.id AND f.user_id = :caller)
      AND NOT EXISTS (
        SELECT 1
        FROM flowboard.decision_follow_suppression s
        WHERE s.tenant_id = :tenant AND s.case_id = c.id AND s.user_id = :caller)
      """;

  private final JdbcTemplate jdbc;
  private final NamedParameterJdbcTemplate namedJdbc;

  public DecisionFollowRepository(@Qualifier("dataSource") DataSource dataSource) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.namedJdbc = new NamedParameterJdbcTemplate(dataSource);
  }

  public boolean record(
      UUID tenantId, UUID caseId, UUID userId, DecisionFollowSource source, UUID sourceRef) {
    return jdbc.update(
            """
            INSERT INTO flowboard.decision_follow
              (id, tenant_id, uid, created_at, created_by, updated_at, updated_by,
               is_active, deleted_at, version, case_id, user_id, source, source_ref)
            VALUES
              (gen_random_uuid(), ?, gen_random_uuid()::text, now(), ?, now(), ?,
               true, null, 0, ?, ?, ?, ?)
            ON CONFLICT (tenant_id, case_id, user_id, source) DO NOTHING
            """,
            tenantId,
            SystemUser.ID,
            SystemUser.ID,
            caseId,
            userId,
            source.name(),
            sourceRef)
        == 1;
  }

  public boolean isEffective(UUID tenantId, UUID caseId, UUID userId) {
    return Boolean.TRUE.equals(
        namedJdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM sales_ord.order_cover_case c "
                + "WHERE c.tenant_id=:tenant AND c.id=:caseId AND ("
                + EFFECTIVE_FOLLOW_PREDICATE
                + "))",
            Map.of("tenant", tenantId, "caseId", caseId, "caller", userId),
            Boolean.class));
  }
}
