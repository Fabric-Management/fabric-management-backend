package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Completes the template tenant, then brings every existing tenant up to it.
 *
 * <p>Two steps, in this order:
 *
 * <ol>
 *   <li>{@link PermissionTemplateSeeder#seed()} — inserts whatever the template tenant is missing.
 *       Runs in <b>all profiles</b>: {@code DevSeedDataRunner} is confined to local/dev/docker, so
 *       routing the seeder through it would leave production's template tenant holding only the
 *       rows some migration happened to write.
 *   <li>A differential copy of the template tenant's rows into every other tenant. Tenants created
 *       while the seeder was crippled hold a partial set and would otherwise stay broken forever —
 *       {@code CloneTemplatePermissionsStep} only runs once, at onboarding.
 * </ol>
 *
 * <p>Replaces {@code FinancePermissionBackfillRunner}, which backfilled one resource and drew its
 * tenant list from {@code SELECT DISTINCT tenant_id FROM permission_template} — a table that is
 * empty for precisely the tenants most in need of a backfill. The list now comes from the tenant
 * table, which is the only place that knows what a tenant is.
 *
 * <p><b>RLS:</b> both steps run through {@link SystemTransactionExecutor} (the {@code
 * fabric_system} BYPASSRLS role). Under {@code fabric_owner} a cross-tenant statement sees nothing
 * unless {@code app.current_tenant} is set, and would report success having copied zero rows.
 *
 * @see docs/platform/tickets/PERM-SEED-1-permission-template-seed-poisoning.md
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(200) // after DevSeedDataRunner (100), which creates the tenants this backfills
public class PermissionTemplateBackfillRunner {

  private static final String PERMISSIONS_CACHE = "permissions";

  private final PermissionTemplateSeeder permissionTemplateSeeder;
  private final SystemTransactionExecutor systemTransactionExecutor;
  private final CacheManager cacheManager;

  /**
   * Copies each template-tenant row into every tenant that lacks it.
   *
   * <p>Matching is on the effective key of {@code uq_permission_template_effective} — role,
   * department (nulls folded to a sentinel), resource, action — so a tenant that has deliberately
   * customised a row's {@code data_scope} keeps its version, and a row deactivated on purpose is
   * not resurrected. Only genuinely absent rules are added.
   */
  private static final String BACKFILL_SQL =
      """
      INSERT INTO common_user.permission_template (
          id, tenant_id, uid, role_code, department_code, resource, action,
          data_scope, is_active, created_at, updated_at, version
      )
      SELECT
          gen_random_uuid(), t.id, gen_random_uuid()::varchar, g.role_code, g.department_code,
          g.resource, g.action, g.data_scope, g.is_active, NOW(), NOW(), 0
      FROM common_tenant.common_tenant t
      CROSS JOIN (
          SELECT role_code, department_code, resource, action, data_scope, is_active
          FROM common_user.permission_template
          WHERE tenant_id = ?::uuid AND deleted_at IS NULL
      ) g
      WHERE t.id <> ?::uuid
        AND t.deleted_at IS NULL
        AND NOT EXISTS (
            SELECT 1 FROM common_user.permission_template pt
            WHERE pt.tenant_id = t.id
              AND pt.role_code = g.role_code
              AND COALESCE(pt.department_code, '__ALL__') = COALESCE(g.department_code, '__ALL__')
              AND pt.resource = g.resource
              AND pt.action = g.action
        )
      """;

  @EventListener(ApplicationReadyEvent.class)
  public void run() {
    try {
      permissionTemplateSeeder.seed();
    } catch (Exception e) {
      log.error(
          "CRITICAL: permission template seeding failed. New tenants will be cloned from an"
              + " incomplete template and their non-admin users locked out.",
          e);
      throw new IllegalStateException("PermissionTemplateSeeder failed", e);
    }

    int rowsInserted;
    try {
      String templateTenantId = TenantContext.TEMPLATE_TENANT_ID.toString();
      rowsInserted =
          systemTransactionExecutor.executeInTransaction(
              jdbcTemplate ->
                  jdbcTemplate.update(BACKFILL_SQL, templateTenantId, templateTenantId));
    } catch (Exception e) {
      log.error(
          "CRITICAL: cross-tenant permission template backfill failed. Tenants created before this"
              + " fix remain locked out for every non-admin user.",
          e);
      throw new IllegalStateException("PermissionTemplateBackfillRunner failed", e);
    }

    if (rowsInserted == 0) {
      log.info("Permission template backfill: all tenants already match the template tenant.");
      return;
    }

    log.info("Permission template backfill: inserted {} rows across tenants.", rowsInserted);
    evictPermissionCache();
  }

  /**
   * A user evaluated before the backfill holds an empty {@code PermissionResult} in the {@code
   * permissions} cache and would keep seeing 403s until the entry expired. Drop the cache whenever
   * rows were added.
   */
  private void evictPermissionCache() {
    Cache cache = cacheManager.getCache(PERMISSIONS_CACHE);
    if (cache == null) {
      log.warn(
          "Cache '{}' not configured; stale permission results may persist.", PERMISSIONS_CACHE);
      return;
    }
    cache.clear();
    log.info("Cleared '{}' cache after permission template backfill.", PERMISSIONS_CACHE);
  }
}
