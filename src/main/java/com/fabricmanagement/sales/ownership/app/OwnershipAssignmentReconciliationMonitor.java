package com.fabricmanagement.sales.ownership.app;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
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
  private final AtomicLong missingAssignmentCount = new AtomicLong();

  @PostConstruct
  void registerGauge() {
    Gauge.builder(
            "sales.ownership.assignment.missing", missingAssignmentCount, AtomicLong::doubleValue)
        .description("Customers with an acquirer but no commercial assignment outside exempt mode")
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
  }
}
