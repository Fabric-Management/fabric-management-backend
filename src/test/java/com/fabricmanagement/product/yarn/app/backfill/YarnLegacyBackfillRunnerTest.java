package com.fabricmanagement.product.yarn.app.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.yarn.app.bootstrap.YarnCatalogueBackfillRunner;
import com.fabricmanagement.product.yarn.app.bootstrap.YarnLegacyBackfillRunner;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.Order;

@ExtendWith(MockitoExtension.class)
class YarnLegacyBackfillRunnerTest {

  @Mock private YarnLegacyBackfillService service;
  @Mock private SystemTransactionExecutor systemTransactions;

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void runsEveryTenantInsideItsFullContextAndIsOrderedAfterCatalogueBackfill()
      throws NoSuchMethodException {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    when(systemTransactions.executeInTransaction(any()))
        .thenReturn(List.of(Map.entry(first, "TENANT-FIRST"), Map.entry(second, "TENANT-SECOND")));
    when(service.backfillTenant(first)).thenAnswer(invocation -> completed(first, "TENANT-FIRST"));
    when(service.backfillTenant(second))
        .thenAnswer(invocation -> completed(second, "TENANT-SECOND"));

    new YarnLegacyBackfillRunner(service, systemTransactions).run();

    verify(service).backfillTenant(first);
    verify(service).backfillTenant(second);
    Order catalogueOrder =
        YarnCatalogueBackfillRunner.class.getDeclaredMethod("run").getAnnotation(Order.class);
    Order legacyOrder =
        YarnLegacyBackfillRunner.class.getDeclaredMethod("run").getAnnotation(Order.class);
    assertThat(catalogueOrder).isNotNull();
    assertThat(legacyOrder).isNotNull();
    assertThat(legacyOrder.value()).isEqualTo(230).isGreaterThan(catalogueOrder.value());
    assertThat(TenantContext.isSet()).isFalse();
    assertThat(TenantContext.getCurrentTenantUid()).isNull();
  }

  private YarnLegacyBackfillReport completed(UUID tenantId, String tenantUid) {
    assertThat(TenantContext.requireTenantId()).isEqualTo(tenantId);
    assertThat(TenantContext.getCurrentTenantUid()).isEqualTo(tenantUid);
    return new YarnLegacyBackfillReport(
        tenantId, YarnLegacyBackfillOutcome.COMPLETED, 0, 0, 0, 0, Map.of(), Map.of(), Map.of());
  }
}
