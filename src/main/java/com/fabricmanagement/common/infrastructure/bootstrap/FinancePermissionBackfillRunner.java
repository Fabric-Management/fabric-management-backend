package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Backfills the 'finance' permission resource for the FINANCE department across all existing
 * tenants.
 *
 * <p>CRITICAL: This executes via {@link SystemTransactionExecutor} (fabric_system) to bypass
 * Row-Level Security (RLS). Doing this in a standard Flyway migration under `fabric_owner` would
 * fail silently because `fabric_owner` is subject to RLS and wouldn't see any rows to backfill
 * unless `app.current_tenant` is set.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FinancePermissionBackfillRunner {

  private final SystemTransactionExecutor systemTransactionExecutor;

  @EventListener(ApplicationReadyEvent.class)
  public void runBackfill() {
    log.info("Starting FinancePermissionBackfillRunner cross-tenant backfill...");

    String sqlSynthesis =
        """
        INSERT INTO common_user.permission_template (
            id, tenant_id, uid, role_code, department_code, resource, action, data_scope, is_active, created_at, updated_at
        )
        SELECT
            gen_random_uuid(), t.tenant_id, gen_random_uuid()::varchar, r.role_code, 'FINANCE', 'finance', r.action, r.data_scope, true, NOW(), NOW()
        FROM (
            SELECT DISTINCT tenant_id FROM common_user.permission_template
        ) t
        CROSS JOIN (
            VALUES ('WORKER', 'read', 'OWN'), ('WORKER', 'write', 'OWN'), ('SUPERVISOR', 'read', 'DEPARTMENT'),
                   ('SUPERVISOR', 'write', 'DEPARTMENT'), ('MANAGER', 'read', 'ORGANIZATION'), ('MANAGER', 'write', 'DEPARTMENT')
        ) AS r(role_code, action, data_scope)
        WHERE NOT EXISTS (
            SELECT 1 FROM common_user.permission_template pt
            WHERE COALESCE(pt.tenant_id, '00000000-0000-0000-0000-000000000000'::uuid) = COALESCE(t.tenant_id, '00000000-0000-0000-0000-000000000000'::uuid)
              AND pt.role_code = r.role_code AND pt.department_code = 'FINANCE' AND pt.resource = 'finance' AND pt.action = r.action
        );
        """;

    String sqlClone =
        """
        INSERT INTO common_user.permission_template (
            id, tenant_id, uid, role_code, department_code, resource, action, data_scope, is_active, created_at, updated_at
        )
        SELECT
            gen_random_uuid(), pt.tenant_id, gen_random_uuid()::varchar, pt.role_code, pt.department_code, 'finance', 'read', pt.data_scope, true, NOW(), NOW()
        FROM common_user.permission_template pt
        WHERE pt.resource = 'sales' AND pt.action = 'read'
          AND (pt.department_code = 'SALES' OR pt.department_code IS NULL)
          AND pt.role_code NOT LIKE 'PARTNER_%'
          AND NOT EXISTS (
              SELECT 1 FROM common_user.permission_template existing
              WHERE COALESCE(existing.tenant_id, '00000000-0000-0000-0000-000000000000'::uuid) = COALESCE(pt.tenant_id, '00000000-0000-0000-0000-000000000000'::uuid)
                AND existing.role_code = pt.role_code
                AND COALESCE(existing.department_code, 'NULL_DEPT') = COALESCE(pt.department_code, 'NULL_DEPT')
                AND existing.resource = 'finance'
                AND existing.action = 'read'
          );
        """;

    try {
      int rowsInserted =
          systemTransactionExecutor.executeInTransaction(
              jdbcTemplate -> {
                int count = jdbcTemplate.update(sqlSynthesis);
                count += jdbcTemplate.update(sqlClone);
                return count;
              });
      log.info(
          "FinancePermissionBackfillRunner completed successfully. Rows inserted: {}",
          rowsInserted);
    } catch (Exception e) {
      log.error(
          "CRITICAL: Failed to execute FinancePermissionBackfillRunner. Finance endpoints will be locked out!",
          e);
      throw new IllegalStateException(
          "FinancePermissionBackfillRunner failed during cross-tenant backfill", e);
    }
  }
}
