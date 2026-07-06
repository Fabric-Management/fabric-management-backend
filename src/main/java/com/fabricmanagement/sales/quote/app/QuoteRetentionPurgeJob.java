package com.fabricmanagement.sales.quote.app;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuoteRetentionPurgeJob {

  static final String ACTIVE_TENANTS_SQL =
      "SELECT id FROM common_tenant.common_tenant WHERE is_active = true";

  static final String DELETE_ABANDONED_DRAFT_QUOTES_SQL =
      """
      DELETE FROM sales.quote q
      WHERE q.tenant_id = ?
        AND q.status = 'DRAFT'
        AND q.updated_at < ?
        AND NOT EXISTS (
          SELECT 1 FROM sales.quote_line ql
          WHERE ql.quote_id = q.id
        )
      """;

  static final String DELETE_DEAD_APPROVAL_TOKENS_SQL =
      """
      DELETE FROM sales.quote_approval_token t
      WHERE t.tenant_id = ?
        AND t.status IN ('EXPIRED', 'REVOKED')
        AND t.used_at IS NULL
        AND t.created_at < ?
      """;

  private final SystemTransactionExecutor systemExecutor;
  private final Clock clock;

  @Value("${retention.quote.enabled:false}")
  private boolean enabled;

  @Value("${retention.quote.abandonedDraftDays:90}")
  private int abandonedDraftDays;

  @Value("${retention.quote.deadTokenDays:365}")
  private int deadTokenDays;

  @Scheduled(cron = "${retention.quote.cron:0 30 3 * * ?}")
  public void purgeQuotesAndTokens() {
    if (!enabled) {
      log.debug("QuoteRetentionPurgeJob disabled; skipping.");
      return;
    }

    Instant now = Instant.now(clock);
    List<UUID> tenantIds =
        systemExecutor.executeQuery(
            ACTIVE_TENANTS_SQL, (rs, rowNum) -> UUID.fromString(rs.getString("id")));

    int totalQuotesDeleted = 0;
    int totalTokensDeleted = 0;
    for (UUID tenantId : tenantIds) {
      PurgeCounts counts =
          TenantContext.executeInTenantContext(tenantId, () -> purgeTenant(tenantId, now));
      totalQuotesDeleted += counts.abandonedDraftQuotes();
      totalTokensDeleted += counts.deadApprovalTokens();
      if (counts.hasDeletions()) {
        log.info(
            "QuoteRetentionPurgeJob tenant={} deleted abandonedDraftQuotes={}, deadApprovalTokens={}",
            tenantId,
            counts.abandonedDraftQuotes(),
            counts.deadApprovalTokens());
      }
    }

    if (totalQuotesDeleted > 0 || totalTokensDeleted > 0) {
      log.info(
          "QuoteRetentionPurgeJob completed: tenants={}, abandonedDraftQuotes={}, deadApprovalTokens={}",
          tenantIds.size(),
          totalQuotesDeleted,
          totalTokensDeleted);
    }
  }

  PurgeCounts purgeTenant(UUID tenantId, Instant now) {
    Instant abandonedDraftThreshold = now.minus(abandonedDraftDays, ChronoUnit.DAYS);
    Instant deadTokenThreshold = now.minus(deadTokenDays, ChronoUnit.DAYS);

    return systemExecutor.executeInTransaction(
        jdbc -> {
          int abandonedDraftQuotes =
              jdbc.update(
                  DELETE_ABANDONED_DRAFT_QUOTES_SQL,
                  tenantId,
                  Timestamp.from(abandonedDraftThreshold));
          int deadApprovalTokens =
              jdbc.update(
                  DELETE_DEAD_APPROVAL_TOKENS_SQL, tenantId, Timestamp.from(deadTokenThreshold));
          return new PurgeCounts(abandonedDraftQuotes, deadApprovalTokens);
        });
  }

  record PurgeCounts(int abandonedDraftQuotes, int deadApprovalTokens) {
    boolean hasDeletions() {
      return abandonedDraftQuotes > 0 || deadApprovalTokens > 0;
    }
  }
}
