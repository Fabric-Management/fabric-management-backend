package com.fabricmanagement.product.yarn.app.backfill;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.product.yarn.app.port.LegacyDesignationSourceKind;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class YarnBackfillTenantIsolationIT extends YarnBackfillIntegrationSupport {

  @Test
  void tenantBackfillReadsAndWritesOnlyThatTenantsEvidence() {
    TenantFixture tenantA = insertTenant("isolation-a", 1);
    TenantFixture tenantB = insertTenant("isolation-b", 1);
    insertBatch(tenantA, 0, "Tenant A Ne 30/2", Instant.parse("2026-08-31T12:00:00Z"));
    insertBatch(tenantB, 0, "Tenant B Ne 20/1", Instant.parse("2026-08-31T12:00:00Z"));

    YarnLegacyBackfillReport reportA = backfill(tenantA);

    assertThat(reportA.recordsContributed())
        .containsEntry(LegacyDesignationSourceKind.BATCH_ACTUAL, 1L);
    assertThat(articleCount(tenantA)).isEqualTo(1L);
    assertThat(articleCount(tenantB)).isZero();
    assertThat(sourceDesignation(tenantA)).isEqualTo("Tenant A Ne 30/2");

    YarnLegacyBackfillReport reportB = backfill(tenantB);
    assertThat(reportB.recordsContributed())
        .containsEntry(LegacyDesignationSourceKind.BATCH_ACTUAL, 1L);
    assertThat(sourceDesignation(tenantB)).isEqualTo("Tenant B Ne 20/1");
  }

  @Test
  void inactiveProductIsOutsideScanAndReactivationSelfHealsOnNextRun() {
    TenantFixture fixture = insertTenant("reactivation", 1);
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "UPDATE production.prod_product SET is_active=FALSE, deleted_at=now() "
                  + "WHERE id=?",
              fixture.productId(0));
          return null;
        });

    YarnLegacyBackfillReport inactive = backfill(fixture);
    assertThat(inactive.productsScanned()).isZero();
    assertThat(articleCount(fixture)).isZero();

    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "UPDATE production.prod_product SET is_active=TRUE, deleted_at=NULL WHERE id=?",
              fixture.productId(0));
          return null;
        });
    YarnLegacyBackfillReport reactivated = backfill(fixture);
    assertThat(reactivated.productsScanned()).isEqualTo(1);
    assertThat(reactivated.articlesCreated()).isEqualTo(1);
  }

  private long articleCount(TenantFixture fixture) {
    return queryOne(
        "SELECT count(*) FROM production.prod_yarn_article WHERE tenant_id=?",
        Long.class,
        fixture.tenantId());
  }

  private String sourceDesignation(TenantFixture fixture) {
    return queryOne(
        "SELECT source_designation FROM production.prod_yarn_article WHERE tenant_id=?",
        String.class,
        fixture.tenantId());
  }
}
