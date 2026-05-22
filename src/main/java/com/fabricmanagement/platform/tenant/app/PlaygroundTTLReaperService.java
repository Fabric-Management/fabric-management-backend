package com.fabricmanagement.platform.tenant.app;

import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.domain.TenantType;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Background job to reap (soft-delete) expired playground tenants.
 *
 * <p>Playground tenants are ephemeral environments created for users exploring the platform. To
 * prevent database bloat, this job runs periodically and cleans up old sessions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlaygroundTTLReaperService {

  private final TenantRepository tenantRepository;

  @Value("${playground.ttl.days:14}")
  private int ttlDays;

  /** Run every day at 3 AM. Finds PLAYGROUND tenants older than ttlDays and marks them inactive. */
  @Scheduled(cron = "${playground.ttl.cron:0 0 3 * * ?}")
  @Transactional
  public void reapExpiredPlaygrounds() {
    log.info("Starting Playground TTL Reaper job...");

    Instant threshold = Instant.now().minus(ttlDays, ChronoUnit.DAYS);
    List<Tenant> expiredPlaygrounds =
        tenantRepository.findExpiredPlaygrounds(TenantType.PLAYGROUND, threshold);

    if (expiredPlaygrounds.isEmpty()) {
      log.info("No expired playgrounds found.");
      return;
    }

    log.info("Found {} expired playgrounds to reap.", expiredPlaygrounds.size());

    for (Tenant tenant : expiredPlaygrounds) {
      tenant.setIsActive(false);
      log.info(
          "Soft-deleted playground tenant: {} (UID: {}, Created At: {})",
          tenant.getId(),
          tenant.getUid(),
          tenant.getCreatedAt());
    }

    tenantRepository.saveAll(expiredPlaygrounds);
    log.info("Playground TTL Reaper job completed.");
  }

  /**
   * Hard-purge job: permanently deletes playground tenants that have been inactive for 30+ days.
   * Runs daily at 4 AM, one hour after the soft-delete reaper.
   *
   * <p>Cascading delete order: UserContact → Contact → UserDepartment → User → Department →
   * Organization → Tenant.
   */
  @Scheduled(cron = "${playground.purge.cron:0 0 4 * * ?}")
  @Transactional
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
