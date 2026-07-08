package com.fabricmanagement.production.execution.batch.app;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import java.time.Clock;
import java.time.LocalDate;
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
public class BatchLotQuantityIntentExpiryJob {

  static final String ACTIVE_TENANTS_SQL =
      "SELECT id FROM common_tenant.common_tenant WHERE is_active = true";

  private final BatchLotQuantityIntentService intentService;
  private final SystemTransactionExecutor systemExecutor;
  private final Clock clock;

  @Value("${sales.lot-intent.expiry.enabled:true}")
  private boolean enabled;

  @Scheduled(cron = "${sales.lot-intent.expiry.cron:0 15 2 * * ?}")
  public void releaseExpiredIntents() {
    if (!enabled) {
      log.debug("BatchLotQuantityIntentExpiryJob disabled; skipping.");
      return;
    }
    LocalDate today = LocalDate.now(clock);
    List<UUID> tenantIds =
        systemExecutor.executeQuery(
            ACTIVE_TENANTS_SQL, (rs, rowNum) -> UUID.fromString(rs.getString("id")));
    int released =
        tenantIds.stream()
            .mapToInt(
                tenantId ->
                    TenantContext.executeInTenantContext(
                        tenantId, () -> intentService.releaseExpiredIntents(today)))
            .sum();
    if (released > 0) {
      log.info("BatchLotQuantityIntentExpiryJob released {} expired intents", released);
    }
  }
}
