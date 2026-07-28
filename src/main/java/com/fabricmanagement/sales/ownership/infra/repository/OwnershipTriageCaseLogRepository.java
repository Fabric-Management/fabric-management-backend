package com.fabricmanagement.sales.ownership.infra.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class OwnershipTriageCaseLogRepository {

  private static final String INSERT_NOTIFICATION_REQUEST_SQL =
      """
      INSERT INTO sales.ownership_triage_case_log (
          id, tenant_id, uid, customer_id, gap_started_at,
          notification_requested_at, created_at, updated_at, is_active, version
      )
      VALUES (
          gen_random_uuid(), :tenantId, CAST(gen_random_uuid() AS VARCHAR),
          :customerId, :gapStartedAt,
          :requestedAt, :requestedAt, :requestedAt, TRUE, 0
      )
      ON CONFLICT (tenant_id, customer_id, gap_started_at) DO NOTHING
      RETURNING id
      """;

  private static final String MARK_AGING_ALERT_QUEUED_SQL =
      """
      UPDATE sales.ownership_triage_case_log
      SET aging_alert_queued_at = :queuedAt,
          updated_at = :queuedAt,
          version = version + 1
      WHERE tenant_id = :tenantId
        AND customer_id = :customerId
        AND gap_started_at = :gapStartedAt
        AND aging_alert_queued_at IS NULL
        AND resolved_at IS NULL
      RETURNING id
      """;

  private static final String RESOLVE_OPEN_CASES_SQL =
      """
      UPDATE sales.ownership_triage_case_log
      SET resolved_at = :resolvedAt,
          updated_at = :resolvedAt,
          version = version + 1
      WHERE tenant_id = :tenantId
        AND customer_id = :customerId
        AND resolved_at IS NULL
      """;

  private static final String COUNT_CONFLICTS_SQL =
      OwnershipTriageQueryRepository.DERIVED_TRIAGE_CTE
          + """
          SELECT COUNT(*)
          FROM sales.ownership_triage_case_log case_log
          WHERE case_log.tenant_id = :tenantId
            AND case_log.resolved_at IS NULL
            AND case_log.is_active = TRUE
            AND case_log.deleted_at IS NULL
            AND NOT EXISTS (
                SELECT 1
                FROM ownership_triage derived_case
                WHERE derived_case.customer_id = case_log.customer_id
                  AND derived_case.gap_started_at = case_log.gap_started_at
            )
          """;

  @PersistenceContext private EntityManager entityManager;

  public boolean tryRecordNotificationRequested(
      UUID tenantId, UUID customerId, Instant gapStartedAt, Instant requestedAt) {
    bindTenant(tenantId);
    return !entityManager
        .createNativeQuery(INSERT_NOTIFICATION_REQUEST_SQL)
        .setParameter("tenantId", tenantId)
        .setParameter("customerId", customerId)
        .setParameter("gapStartedAt", Timestamp.from(gapStartedAt))
        .setParameter("requestedAt", Timestamp.from(requestedAt))
        .getResultList()
        .isEmpty();
  }

  public boolean tryMarkAgingAlertQueued(
      UUID tenantId, UUID customerId, Instant gapStartedAt, Instant queuedAt) {
    bindTenant(tenantId);
    return !entityManager
        .createNativeQuery(MARK_AGING_ALERT_QUEUED_SQL)
        .setParameter("tenantId", tenantId)
        .setParameter("customerId", customerId)
        .setParameter("gapStartedAt", Timestamp.from(gapStartedAt))
        .setParameter("queuedAt", Timestamp.from(queuedAt))
        .getResultList()
        .isEmpty();
  }

  public int resolveOpenCases(UUID tenantId, UUID customerId, Instant resolvedAt) {
    bindTenant(tenantId);
    return entityManager
        .createNativeQuery(RESOLVE_OPEN_CASES_SQL)
        .setParameter("tenantId", tenantId)
        .setParameter("customerId", customerId)
        .setParameter("resolvedAt", Timestamp.from(resolvedAt))
        .executeUpdate();
  }

  public long countUnresolvedConflicts(UUID tenantId) {
    bindTenant(tenantId);
    Object result =
        entityManager
            .createNativeQuery(COUNT_CONFLICTS_SQL)
            .setParameter("tenantId", tenantId)
            .getSingleResult();
    return result == null ? 0 : ((Number) result).longValue();
  }

  private void bindTenant(UUID tenantId) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenant', :tenantId, true)")
        .setParameter("tenantId", tenantId.toString())
        .getSingleResult();
  }
}
