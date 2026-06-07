package com.fabricmanagement.platform.tenant.app;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Background job to reap (soft-delete) expired playground tenants.
 *
 * <p>Playground tenants are ephemeral environments created for users exploring the platform. To
 * prevent database bloat, this job runs periodically and cleans up old sessions.
 *
 * <p><b>CR-2 Fix:</b> Uses {@link SystemTransactionExecutor} (BYPASSRLS) instead of JPA because
 * scheduled jobs run without {@code TenantContext}. Self-row RLS on the tenant table would cause
 * {@code findExpiredPlaygrounds()} to always return 0 rows via JPA.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlaygroundTTLReaperService {

  private final SystemTransactionExecutor systemExecutor;

  @Value("${playground.ttl.days:14}")
  private int ttlDays;

  /**
   * Run every day at 3 AM. Finds PLAYGROUND tenants older than ttlDays and marks them inactive.
   *
   * <p><b>Intentional:</b> This bulk UPDATE does NOT publish {@code TenantStatusChangedEvent} for
   * each affected tenant. Playground tenants are ephemeral — individual status-change audit trails
   * and downstream event listeners (billing, analytics) are not relevant for TTL cleanup. If
   * per-tenant event publishing becomes necessary (e.g., for webhook notifications), switch to an
   * iterative approach using {@code TenantSystemService.suspend()}.
   */
  @Scheduled(cron = "${playground.ttl.cron:0 0 3 * * ?}")
  public void reapExpiredPlaygrounds() {
    log.info("Starting Playground TTL Reaper job...");

    Instant threshold = Instant.now().minus(ttlDays, ChronoUnit.DAYS);

    int affected =
        systemExecutor.executeInTransaction(
            jdbc ->
                jdbc.update(
                    "UPDATE common_tenant.common_tenant "
                        + "SET is_active = false, updated_at = now(), version = version + 1 "
                        + "WHERE type = 'PLAYGROUND' AND is_active = true AND created_at < ?",
                    java.sql.Timestamp.from(threshold)));

    if (affected == 0) {
      log.info("No expired playgrounds found.");
    } else {
      log.info(
          "Soft-deleted {} expired playground tenant(s). No events published (intentional — ephemeral tenants).",
          affected);
    }
  }

  /**
   * Hard-purge job: permanently deletes playground tenants that have been inactive for 30+ days.
   * Runs daily at 4 AM, one hour after the soft-delete reaper.
   *
   * <p>Cascading delete order: UserContact → Contact → UserDepartment → User → Department →
   * Organization → Tenant.
   */
  @Scheduled(cron = "${playground.purge.cron:0 0 4 * * ?}")
  public void purgeInactivePlaygrounds() {
    log.info("Starting Playground Hard Purge job for tenants inactive for 30+ days...");
    // TODO(CR4-3): Implement cascading hard delete for playground tenant data.
    // Steps:
    //   1. Find tenants WHERE type=PLAYGROUND AND isActive=false AND updatedAt < now()-30d
    //   2. For each tenant, delete in order: notifications, user_contacts, contacts,
    //      user_departments, users, departments, organizations, tenant
    //   3. Log count of purged tenants
    log.info("Hard purge not yet implemented — skipping.");
  }
}
