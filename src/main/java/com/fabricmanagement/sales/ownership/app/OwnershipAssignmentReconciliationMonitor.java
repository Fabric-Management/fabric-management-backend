package com.fabricmanagement.sales.ownership.app;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OwnershipAssignmentReconciliationMonitor {

  private final SystemTransactionExecutor systemTransactionExecutor;
  private final MeterRegistry meterRegistry;
  private final OwnershipTriageProcessor triageProcessor;
  private final AtomicLong missingAssignmentCount = new AtomicLong();
  private final AtomicLong openTriageCount = new AtomicLong();
  private final AtomicLong oldestTriageAgeHours = new AtomicLong();
  private final AtomicLong triageCaseLogConflictCount = new AtomicLong();

  private static final String ACTIVE_TENANTS_SQL =
      """
      SELECT id
      FROM common_tenant.common_tenant
      WHERE is_active = TRUE
        AND type NOT IN ('SYSTEM', 'TEMPLATE')
      ORDER BY id
      """;

  @PostConstruct
  void registerGauge() {
    Gauge.builder(
            "sales.ownership.assignment.missing", missingAssignmentCount, AtomicLong::doubleValue)
        .description("Customers with an acquirer but no commercial assignment outside exempt mode")
        .register(meterRegistry);
    Gauge.builder("sales.ownership.triage.open", openTriageCount, AtomicLong::doubleValue)
        .description("Current customers requiring a commercial ownership assignment")
        .register(meterRegistry);
    Gauge.builder(
            "sales.ownership.triage.oldest_age_hours",
            oldestTriageAgeHours,
            AtomicLong::doubleValue)
        .description("Age in hours of the oldest current ownership triage case")
        .register(meterRegistry);
    Gauge.builder(
            "sales.ownership.triage.case_log_conflicts",
            triageCaseLogConflictCount,
            AtomicLong::doubleValue)
        .description("Unresolved case-log rows that disagree with the derived triage query")
        .register(meterRegistry);
  }

  @Scheduled(fixedDelayString = "${sales.ownership.reconciliation.interval-ms:300000}")
  public void reconcile() {
    Long count =
        systemTransactionExecutor.executeInTransaction(
            jdbc ->
                jdbc.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM common_company.common_trading_partner partner
                    JOIN sales.ownership_policy policy
                      ON policy.tenant_id = partner.tenant_id
                    WHERE partner.partner_type IN ('CUSTOMER', 'BOTH')
                      AND partner.is_active = TRUE
                      AND partner.acquired_by_id IS NOT NULL
                      AND policy.default_mode <> 'EXEMPT'
                      AND NOT EXISTS (
                          SELECT 1
                          FROM sales.customer_commercial_assignment assignment
                          WHERE assignment.tenant_id = partner.tenant_id
                            AND assignment.customer_id = partner.id
                      )
                    """,
                    Long.class));
    long safeCount = count != null ? count : 0L;
    missingAssignmentCount.set(safeCount);
    if (safeCount > 0) {
      log.warn(
          "Commercial assignment reconciliation detected customers with missing truth rows: "
              + "count={}",
          safeCount);
    }

    List<UUID> tenantIds =
        systemTransactionExecutor.executeQuery(
            ACTIVE_TENANTS_SQL, (rs, rowNumber) -> rs.getObject("id", UUID.class));
    long openCases = 0;
    long oldestAge = 0;
    long conflicts = 0;
    for (UUID tenantId : tenantIds) {
      try {
        OwnershipTriageProcessor.ProcessingSummary summary =
            TenantContext.executeInTenantContext(
                tenantId, () -> triageProcessor.processTenant(tenantId));
        openCases += summary.openCases();
        oldestAge = Math.max(oldestAge, summary.oldestAgeHours());
        conflicts += summary.caseLogConflicts();
      } catch (RuntimeException exception) {
        log.error("Ownership triage processing failed for tenantId={}", tenantId, exception);
      }
    }
    openTriageCount.set(openCases);
    oldestTriageAgeHours.set(oldestAge);
    triageCaseLogConflictCount.set(conflicts);
  }
}
