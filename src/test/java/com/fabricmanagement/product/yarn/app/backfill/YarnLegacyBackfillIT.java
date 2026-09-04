package com.fabricmanagement.product.yarn.app.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillQueueReason;
import com.fabricmanagement.product.yarn.infra.repository.YarnArticleRepository;
import com.fabricmanagement.product.yarn.infra.repository.YarnBackfillReconciliationRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;

class YarnLegacyBackfillIT extends YarnBackfillIntegrationSupport {

  private static final Instant BASE_TIME = Instant.parse("2026-08-31T12:00:00Z");

  @SpyBean private ProductRepository productRepository;
  @SpyBean private YarnArticleRepository articleRepository;
  @SpyBean private YarnBackfillReconciliationRepository reconciliationRepository;
  @Autowired private EntityManagerFactory entityManagerFactory;

  @Test
  void matrixCreatesEmptyChosenAndAmbiguousDraftsWithAuditsAndNoCanonicalFields() {
    TenantFixture fixture = insertTenant("matrix", 3);
    insertBatch(fixture, 1, " ne 30/2 ", BASE_TIME.minusSeconds(30));
    insertBatch(fixture, 1, "NE 30/2", BASE_TIME.minusSeconds(20));
    insertBatch(fixture, 1, "Ne 30/2", BASE_TIME);
    insertBatch(fixture, 2, "Ne 20/1", BASE_TIME);
    insertBatch(fixture, 2, "Ne 40/1", BASE_TIME.minusSeconds(1));

    YarnLegacyBackfillReport first = backfill(fixture);

    assertThat(first.productsScanned()).isEqualTo(3);
    assertThat(first.productsSkipped()).isZero();
    assertThat(first.articlesCreated()).isEqualTo(3);
    assertThat(first.candidatesWritten()).isEqualTo(1);
    assertThat(first.queueRowsCreated()).containsEntry(YarnBackfillQueueReason.AMBIGUOUS, 1L);
    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_yarn_article "
                    + "WHERE tenant_id=? AND status='DRAFT'",
                Long.class,
                fixture.tenantId()))
        .isEqualTo(3L);
    assertThat(sourceDesignation(fixture, 0)).isNull();
    assertThat(sourceDesignation(fixture, 1)).isEqualTo("Ne 30/2");
    assertThat(sourceDesignation(fixture, 2)).isNull();
    for (int index = 0; index < 3; index++) {
      assertThat(articleName(fixture, index)).isEqualTo(fixture.productUid(index));
    }
    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_yarn_article_audit "
                    + "WHERE tenant_id=? AND event_type='CREATED'",
                Long.class,
                fixture.tenantId()))
        .isEqualTo(3L);
    assertThat(canonicalWriteCount(fixture)).isZero();
    String payload =
        queryOne(
            "SELECT candidates::text FROM production.prod_yarn_backfill_reconciliation "
                + "WHERE tenant_id=? AND product_id=?",
            String.class,
            fixture.tenantId(),
            fixture.productId(2));
    assertThat(payload).contains("\"schemaVersion\": 1", "Ne 20/1", "Ne 40/1", "BATCH_ACTUAL");

    long articleCount = articleCount(fixture);
    long queueCount = queueCount(fixture);
    YarnLegacyBackfillReport second = backfill(fixture);
    assertThat(second.articlesCreated()).isZero();
    assertThat(second.totalQueueRowsCreated()).isZero();
    assertThat(second.productsSkipped()).isEqualTo(second.productsScanned());
    assertThat(articleCount(fixture)).isEqualTo(articleCount);
    assertThat(queueCount(fixture)).isEqualTo(queueCount);
  }

  @Test
  void overlengthIsNeverTruncatedAndAmbiguityStillWinsWithOneOpenRow() {
    TenantFixture fixture = insertTenant("overlength", 2);
    String full = "\uD83E\uDDF5".repeat(300);
    insertBatch(fixture, 0, full, BASE_TIME);
    insertBatch(fixture, 1, full, BASE_TIME);
    insertBatch(fixture, 1, "Ne 20/1", BASE_TIME.minusSeconds(1));
    insertBatch(fixture, 1, "Ne 40/1", BASE_TIME.minusSeconds(2));

    YarnLegacyBackfillReport report = backfill(fixture);

    assertThat(sourceDesignation(fixture, 0)).isNull();
    assertThat(sourceDesignation(fixture, 1)).isNull();
    assertThat(report.queueRowsCreated())
        .containsEntry(YarnBackfillQueueReason.OVERLENGTH, 1L)
        .containsEntry(YarnBackfillQueueReason.AMBIGUOUS, 1L);
    assertThat(
            queryOne(
                "SELECT reason FROM production.prod_yarn_backfill_reconciliation "
                    + "WHERE tenant_id=? AND product_id=?",
                String.class,
                fixture.tenantId(),
                fixture.productId(1)))
        .isEqualTo("AMBIGUOUS");
    assertThat(
            queryOne(
                "SELECT candidates #>> '{candidates,0,rawValue}' "
                    + "FROM production.prod_yarn_backfill_reconciliation "
                    + "WHERE tenant_id=? AND product_id=?",
                String.class,
                fixture.tenantId(),
                fixture.productId(0)))
        .isEqualTo(full);
    assertThat(queueCount(fixture)).isEqualTo(2L);
  }

  @Test
  void queueFailureRollsArticleAndCreatedAuditBackThenCleanRetrySucceeds() {
    TenantFixture fixture = insertTenant("atomic", 1);
    insertBatch(fixture, 0, "Ne 20/1", BASE_TIME);
    insertBatch(fixture, 0, "Ne 40/1", BASE_TIME.minusSeconds(1));
    doThrow(new DataIntegrityViolationException("forced queue failure"))
        .when(reconciliationRepository)
        .save(any());

    assertThatThrownBy(() -> backfill(fixture)).isInstanceOf(DataIntegrityViolationException.class);
    assertThat(articleCount(fixture)).isZero();
    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_yarn_article_audit WHERE tenant_id=?",
                Long.class,
                fixture.tenantId()))
        .isZero();

    reset(reconciliationRepository);
    YarnLegacyBackfillReport retry = backfill(fixture);
    assertThat(retry.articlesCreated()).isEqualTo(1);
    assertThat(retry.totalQueueRowsCreated()).isEqualTo(1);
    assertThat(articleCount(fixture)).isEqualTo(1);
    assertThat(queueCount(fixture)).isEqualTo(1);
  }

  @Test
  void selectCountIsConstantAndBackfillNeverUsesUuidArticleCreationReads() {
    TenantFixture one = insertTenant("query-one", 1);
    TenantFixture ten = insertTenant("query-ten", 10);
    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    reset(productRepository, articleRepository);
    statistics.clear();

    backfill(one);
    long oneProductSelects = statistics.getQueryExecutionCount();

    statistics.clear();
    backfill(ten);
    long tenProductSelects = statistics.getQueryExecutionCount();

    assertThat(tenProductSelects).isEqualTo(oneProductSelects);
    verify(productRepository, never()).findByTenantIdAndId(any(), any());
    verify(articleRepository, never()).existsByTenantIdAndProduct_Id(any(), any());
  }

  @Test
  void rerunLeavesHumanEditedNameAndSourceDesignationUntouched() {
    TenantFixture fixture = insertTenant("human", 1);
    insertBatch(fixture, 0, "Ne 30/2", BASE_TIME);
    backfill(fixture);
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "UPDATE production.prod_yarn_article "
                  + "SET name='Human yarn', source_designation='Human designation' "
                  + "WHERE tenant_id=? AND product_id=?",
              fixture.tenantId(),
              fixture.productId(0));
          return null;
        });

    YarnLegacyBackfillReport rerun = backfill(fixture);

    assertThat(rerun.productsSkipped()).isEqualTo(1);
    assertThat(articleName(fixture, 0)).isEqualTo("Human yarn");
    assertThat(sourceDesignation(fixture, 0)).isEqualTo("Human designation");
  }

  private String sourceDesignation(TenantFixture fixture, int productIndex) {
    return queryOne(
        "SELECT source_designation FROM production.prod_yarn_article "
            + "WHERE tenant_id=? AND product_id=?",
        String.class,
        fixture.tenantId(),
        fixture.productId(productIndex));
  }

  private String articleName(TenantFixture fixture, int productIndex) {
    return queryOne(
        "SELECT name FROM production.prod_yarn_article WHERE tenant_id=? AND product_id=?",
        String.class,
        fixture.tenantId(),
        fixture.productId(productIndex));
  }

  private long canonicalWriteCount(TenantFixture fixture) {
    return queryOne(
        "SELECT count(*) FROM production.prod_yarn_article a WHERE a.tenant_id=? AND ("
            + "a.original_count_system IS NOT NULL OR a.original_count_value IS NOT NULL OR "
            + "a.count_basis IS NOT NULL OR a.structure_type IS NOT NULL OR a.fold_count IS NOT NULL OR "
            + "a.filament_count IS NOT NULL OR a.twist_contraction_percent IS NOT NULL OR "
            + "a.resultant_linear_density_tex IS NOT NULL OR a.canonical_designation IS NOT NULL OR "
            + "a.material_form IS NOT NULL OR a.spinning_technology_family IS NOT NULL OR "
            + "a.spinning_system_id IS NOT NULL OR a.filament_form IS NOT NULL OR "
            + "a.canonical_key IS NOT NULL OR EXISTS (SELECT 1 FROM production.prod_yarn_article_composition c "
            + "WHERE c.article_id=a.id) OR EXISTS (SELECT 1 FROM production.prod_yarn_article_structure_component c "
            + "WHERE c.article_id=a.id) OR EXISTS (SELECT 1 FROM production.prod_yarn_article_twist_stage t "
            + "WHERE t.article_id=a.id) OR EXISTS (SELECT 1 FROM production.prod_yarn_article_construction_feature f "
            + "WHERE f.article_id=a.id))",
        Long.class,
        fixture.tenantId());
  }

  private long articleCount(TenantFixture fixture) {
    return queryOne(
        "SELECT count(*) FROM production.prod_yarn_article WHERE tenant_id=?",
        Long.class,
        fixture.tenantId());
  }

  private long queueCount(TenantFixture fixture) {
    return queryOne(
        "SELECT count(*) FROM production.prod_yarn_backfill_reconciliation WHERE tenant_id=?",
        Long.class,
        fixture.tenantId());
  }
}
