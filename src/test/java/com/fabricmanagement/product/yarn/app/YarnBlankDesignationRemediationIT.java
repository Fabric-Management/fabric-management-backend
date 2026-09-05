package com.fabricmanagement.product.yarn.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.yarn.app.backfill.YarnBlankDesignationRemediationService;
import com.fabricmanagement.product.yarn.app.backfill.YarnReadinessService;
import com.fabricmanagement.product.yarn.app.backfill.YarnReconciliationService;
import com.fabricmanagement.product.yarn.app.port.LegacyDesignationSourceKind;
import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleSpecSerializer;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import com.fabricmanagement.product.yarn.dto.YarnReconciliationChooseRequest;
import jakarta.persistence.OptimisticLockException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.hibernate.StaleObjectStateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class YarnBlankDesignationRemediationIT extends YarnArticleIntegrationSupport {

  @Autowired private YarnBlankDesignationRemediationService remediationService;
  @Autowired private YarnReconciliationService reconciliationService;
  @Autowired private YarnReadinessService readinessService;
  @SpyBean private YarnArticleService articleServiceSpy;
  @SpyBean private YarnArticleSpecSerializer serializerSpy;

  @AfterEach
  void resetInterleavingSpies() {
    reset(articleServiceSpy, serializerSpy);
  }

  @Test
  void remediatesDraftAndActiveThroughVersionedAuditsAndSecondPassWritesNothing() {
    Fixture draftFixture = insertFixture("blank-draft");
    var draft = create(draftFixture);
    seedDesignation(draft.getId(), " ");
    Fixture activeFixture = insertFixture("blank-active");
    var active = create(activeFixture);
    use(activeFixture);
    service.activate(active.getId());
    seedDesignation(active.getId(), "\t");

    use(draftFixture);
    assertThat(remediationService.remediateTenant(draftFixture.tenantId())).isEqualTo(1);
    use(activeFixture);
    assertThat(remediationService.remediateTenant(activeFixture.tenantId())).isEqualTo(1);

    assertRemediated(draft.getId(), 2);
    assertRemediated(active.getId(), 2);
    assertThat(specUpdatedAuditCount(draft.getId())).isEqualTo(1);
    assertThat(specUpdatedAuditCount(active.getId())).isEqualTo(1);
    assertThat(lastSpecUpdatedOldDesignation(draft.getId())).isEqualTo(" ");
    assertThat(lastSpecUpdatedOldDesignation(active.getId())).isEqualTo("\t");
    use(draftFixture);
    assertThat(remediationService.remediateTenant(draftFixture.tenantId())).isZero();
    use(activeFixture);
    assertThat(remediationService.remediateTenant(activeFixture.tenantId())).isZero();
    assertThat(specUpdatedAuditCount(draft.getId())).isEqualTo(1);
    assertThat(specUpdatedAuditCount(active.getId())).isEqualTo(1);
    use(draftFixture);
    service.updateSpec(draft.getId(), command(draftFixture, "20", null));
    service.activate(draft.getId());
    assertThat(service.findById(draft.getId()).orElseThrow().getStatus().name())
        .isEqualTo("ACTIVE");
  }

  @Test
  void concurrentPassesBlockThenConvergeToOneBumpAndOneAudit() throws Exception {
    Fixture fixture = insertFixture("blank-concurrent");
    var article = create(fixture);
    seedDesignation(article.getId(), "\n");
    CountDownLatch start = new CountDownLatch(1);

    CompletableFuture<Long> first = concurrentPass(fixture, start);
    CompletableFuture<Long> second = concurrentPass(fixture, start);
    start.countDown();

    assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
        .containsExactlyInAnyOrder(0L, 1L);
    assertRemediated(article.getId(), 2);
    assertThat(specUpdatedAuditCount(article.getId())).isEqualTo(1);
  }

  @Test
  void scanUsesCharacterWhitespaceExactlyIncludingUnicodeDivergence() {
    Fixture tabFixture = insertFixture("blank-tab");
    var tab = create(tabFixture);
    seedDesignation(tab.getId(), "\t");
    Fixture newlineFixture = insertFixture("blank-newline");
    var newline = create(newlineFixture);
    seedDesignation(newline.getId(), "\n");
    Fixture unicodeFixture = insertFixture("blank-unicode");
    var unicode = create(unicodeFixture);
    seedDesignation(unicode.getId(), "\u2000");
    Fixture nbspFixture = insertFixture("blank-nbsp");
    var nbsp = create(nbspFixture);
    seedDesignation(nbsp.getId(), "\u00A0");

    assertThat(run(tabFixture)).isEqualTo(1);
    assertThat(run(newlineFixture)).isEqualTo(1);
    assertThat(run(unicodeFixture)).isEqualTo(1);
    assertThat(run(nbspFixture)).isZero();
    assertThat(sourceDesignation(nbsp.getId())).isEqualTo("\u00A0");
  }

  @Test
  void obsoleteBlankIsInertAndAggregateIndependentlyThrowsI17() {
    Fixture fixture = insertFixture("blank-obsolete");
    var article = create(fixture);
    use(fixture);
    service.activate(article.getId());
    service.markObsolete(article.getId());
    seedDesignation(article.getId(), " ");
    java.util.UUID reconciliationId = insertQueue(fixture, article.getId(), "cannot adopt");

    assertThat(run(fixture)).isZero();
    use(fixture);
    assertThatThrownBy(
            () ->
                reconciliationService.choose(
                    reconciliationId,
                    new YarnReconciliationChooseRequest(
                        LegacyDesignationSourceKind.BATCH_ACTUAL, "legacy-1")))
        .isInstanceOf(YarnDomainException.class)
        .satisfies(
            error -> assertThat(((YarnDomainException) error).getInvariantIds()).contains("I17"));
    assertThat(
            queryOne(
                "SELECT status FROM production.prod_yarn_backfill_reconciliation WHERE id=?",
                String.class,
                reconciliationId))
        .isEqualTo("OPEN");
    var obsolete = service.findById(article.getId()).orElseThrow();
    assertThatThrownBy(obsolete::clearBlankSourceDesignation)
        .isInstanceOf(YarnDomainException.class)
        .satisfies(
            error -> assertThat(((YarnDomainException) error).getInvariantIds()).contains("I17"));
    assertThat(sourceDesignation(article.getId())).isEqualTo(" ");
    assertThat(specUpdatedAuditCount(article.getId())).isZero();
  }

  @Test
  void completedPassRemediatesAValueWrittenLaterByAnOldReplicaOnTheNextPass() {
    Fixture fixture = insertFixture("blank-convergence");
    var article = create(fixture);
    seedDesignation(article.getId(), " ");
    assertThat(run(fixture)).isEqualTo(1);
    seedDesignation(article.getId(), "\t");

    assertThat(run(fixture)).isEqualTo(1);

    assertRemediated(article.getId(), 3);
    assertThat(specUpdatedAuditCount(article.getId())).isEqualTo(2);
  }

  @Test
  void normalWriteAndObsoleteTransitionThatWinBeforeTheLockedLoadArePreserved() {
    Fixture writeFixture = insertFixture("blank-normal-write");
    var written = create(writeFixture);
    seedDesignation(written.getId(), " ");
    use(writeFixture);
    service.updateSpec(written.getId(), command(writeFixture, "20", "operator value"));

    assertThat(run(writeFixture)).isZero();
    assertThat(sourceDesignation(written.getId())).isEqualTo("operator value");
    assertThat(specUpdatedAuditCount(written.getId())).isEqualTo(1);

    Fixture obsoleteFixture = insertFixture("blank-obsolete-before-lock");
    var obsolete = create(obsoleteFixture);
    use(obsoleteFixture);
    service.activate(obsolete.getId());
    seedDesignation(obsolete.getId(), " ");
    service.markObsolete(obsolete.getId());

    assertThat(run(obsoleteFixture)).isZero();
    assertThat(sourceDesignation(obsolete.getId())).isEqualTo(" ");
  }

  @Test
  void projectedBlankFixedBeforeTheLockedLoadKeepsOnlyTheOperatorWrite() throws Exception {
    Fixture fixture = insertFixture("blank-write-wins");
    var article = create(fixture);
    seedDesignation(article.getId(), " ");
    CountDownLatch projected = new CountDownLatch(1);
    CountDownLatch allowLockedLoad = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              projected.countDown();
              awaitLatch(allowLockedLoad);
              return invocation.callRealMethod();
            })
        .when(articleServiceSpy)
        .remediateBlankSourceDesignation(article.getId());

    CompletableFuture<Long> pass =
        CompletableFuture.supplyAsync(
            () ->
                TenantContext.executeInTenantContext(
                    fixture.tenantId(),
                    () -> remediationService.remediateTenant(fixture.tenantId())));
    awaitLatch(projected);
    use(fixture);
    service.updateSpec(article.getId(), command(fixture, "20", "operator value"));
    allowLockedLoad.countDown();

    assertThat(pass.get(10, TimeUnit.SECONDS)).isZero();
    assertThat(sourceDesignation(article.getId())).isEqualTo("operator value");
    assertThat(
            queryOne(
                "SELECT article_spec_version FROM production.prod_yarn_article WHERE id=?",
                Integer.class,
                article.getId()))
        .isEqualTo(2);
    assertThat(specUpdatedAuditCount(article.getId())).isEqualTo(1);
  }

  @Test
  void projectedActiveBlankObsoletedBeforeTheLockedLoadIsSkipped() throws Exception {
    Fixture fixture = insertFixture("blank-obsolete-wins");
    var article = create(fixture);
    use(fixture);
    service.activate(article.getId());
    seedDesignation(article.getId(), " ");
    CountDownLatch projected = new CountDownLatch(1);
    CountDownLatch allowLockedLoad = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              projected.countDown();
              awaitLatch(allowLockedLoad);
              return invocation.callRealMethod();
            })
        .when(articleServiceSpy)
        .remediateBlankSourceDesignation(article.getId());

    CompletableFuture<Long> pass =
        CompletableFuture.supplyAsync(
            () ->
                TenantContext.executeInTenantContext(
                    fixture.tenantId(),
                    () -> remediationService.remediateTenant(fixture.tenantId())));
    awaitLatch(projected);
    use(fixture);
    service.markObsolete(article.getId());
    allowLockedLoad.countDown();

    assertThat(pass.get(10, TimeUnit.SECONDS)).isZero();
    assertThat(sourceDesignation(article.getId())).isEqualTo(" ");
    assertThat(
            queryOne(
                "SELECT status FROM production.prod_yarn_article WHERE id=?",
                String.class,
                article.getId()))
        .isEqualTo("OBSOLETE");
    assertThat(specUpdatedAuditCount(article.getId())).isZero();
    var obsolete = service.findById(article.getId()).orElseThrow();
    assertThatThrownBy(obsolete::clearBlankSourceDesignation)
        .isInstanceOf(YarnDomainException.class)
        .satisfies(
            error -> assertThat(((YarnDomainException) error).getInvariantIds()).contains("I17"));
  }

  @Test
  void runnerLockWinningMakesTheLateWriteOptimisticThenReplaySucceeds() throws Exception {
    Fixture fixture = insertFixture("blank-runner-wins");
    var article = create(fixture);
    seedDesignation(article.getId(), " ");
    CountDownLatch runnerMutatedUnderLock = new CountDownLatch(1);
    CountDownLatch operatorLoadedOldVersion = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              Object result = invocation.callRealMethod();
              runnerMutatedUnderLock.countDown();
              awaitLatch(operatorLoadedOldVersion);
              return result;
            })
        .when(articleServiceSpy)
        .remediateBlankSourceDesignation(article.getId());
    doAnswer(
            invocation -> {
              YarnArticle loaded = invocation.getArgument(0);
              Object result = invocation.callRealMethod();
              // updateSpec snapshots the managed article immediately after its real repository
              // read.
              // The runner takes its own snapshots before announcing the uncommitted mutation.
              if (runnerMutatedUnderLock.getCount() == 0
                  && operatorLoadedOldVersion.getCount() != 0
                  && article.getId().equals(loaded.getId())) {
                assertThat(loaded.getArticleSpecVersion()).isEqualTo(1);
                assertThat(loaded.getSourceDesignation()).isEqualTo(" ");
                operatorLoadedOldVersion.countDown();
              }
              return result;
            })
        .when(serializerSpy)
        .auditSnapshot(any(YarnArticle.class));

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      CompletableFuture<Long> pass =
          CompletableFuture.supplyAsync(
              () ->
                  TenantContext.executeInTenantContext(
                      fixture.tenantId(),
                      () -> remediationService.remediateTenant(fixture.tenantId())),
              executor);
      awaitLatch(runnerMutatedUnderLock);
      CompletableFuture<Throwable> lateWrite =
          CompletableFuture.supplyAsync(
              () ->
                  TenantContext.executeInTenantContext(
                      fixture.tenantId(),
                      () -> {
                        try {
                          service.updateSpec(
                              article.getId(), command(fixture, "20", "late operator value"));
                          return null;
                        } catch (Throwable failure) {
                          return failure;
                        } finally {
                          // Release the runner even if the operator fails before the snapshot hook.
                          operatorLoadedOldVersion.countDown();
                        }
                      }),
              executor);

      Throwable failure = lateWrite.get(10, TimeUnit.SECONDS);
      assertThat(hasOptimisticLockCause(failure))
          .as("late update must fail with optimistic locking; actual failure: %s", failure)
          .isTrue();
      assertThat(pass.get(10, TimeUnit.SECONDS)).isEqualTo(1);
    }
    reset(articleServiceSpy, serializerSpy);
    use(fixture);
    service.updateSpec(article.getId(), command(fixture, "20", "replayed operator value"));

    assertThat(sourceDesignation(article.getId())).isEqualTo("replayed operator value");
    assertThat(
            queryOne(
                "SELECT article_spec_version FROM production.prod_yarn_article WHERE id=?",
                Integer.class,
                article.getId()))
        .isEqualTo(3);
    assertThat(specUpdatedAuditCount(article.getId())).isEqualTo(2);
  }

  @Test
  void resolvingTheLastOpenRowDoesNotMakeADraftReadyButActivationDoes() {
    Fixture fixture = insertFixture("readiness-transition");
    var article = create(fixture);
    java.util.UUID reconciliationId = insertQueue(fixture, article.getId(), "chosen verbatim");
    insertOpenWorkOrder(fixture);
    use(fixture);

    var before = readinessService.readiness(50);
    reconciliationService.choose(
        reconciliationId,
        new YarnReconciliationChooseRequest(LegacyDesignationSourceKind.BATCH_ACTUAL, "legacy-1"));
    var afterChoose = readinessService.readiness(50);
    service.activate(article.getId());
    var afterActivation = readinessService.readiness(50);

    assertThat(before.ready()).isFalse();
    assertThat(before.openReconciliationCount()).isEqualTo(1);
    assertThat(afterChoose.ready()).isFalse();
    assertThat(afterChoose.openReconciliationCount()).isZero();
    assertThat(afterActivation.ready()).isTrue();
  }

  @Test
  void chooseOnAnActiveFullyValidArticleRevalidatesAndSucceeds() {
    Fixture fixture = insertFixture("active-choose");
    var article = create(fixture);
    use(fixture);
    service.activate(article.getId());
    java.util.UUID reconciliationId = insertQueue(fixture, article.getId(), " active bytes ");

    reconciliationService.choose(
        reconciliationId,
        new YarnReconciliationChooseRequest(LegacyDesignationSourceKind.BATCH_ACTUAL, "legacy-1"));

    assertThat(sourceDesignation(article.getId())).isEqualTo(" active bytes ");
    assertThat(
            queryOne(
                "SELECT status FROM production.prod_yarn_backfill_reconciliation WHERE id=?",
                String.class,
                reconciliationId))
        .isEqualTo("RESOLVED");
  }

  private CompletableFuture<Long> concurrentPass(Fixture fixture, CountDownLatch start) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            start.await();
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
          }
          return TenantContext.executeInTenantContext(
              fixture.tenantId(), () -> remediationService.remediateTenant(fixture.tenantId()));
        });
  }

  private void awaitLatch(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("concurrency coordination timed out");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("concurrency coordination was interrupted", exception);
    }
  }

  private boolean hasOptimisticLockCause(Throwable failure) {
    Throwable current = failure;
    while (current != null) {
      if (current instanceof ObjectOptimisticLockingFailureException
          || current instanceof OptimisticLockException
          || current instanceof StaleObjectStateException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private long run(Fixture fixture) {
    use(fixture);
    return remediationService.remediateTenant(fixture.tenantId());
  }

  private java.util.UUID insertQueue(Fixture fixture, java.util.UUID articleId, String rawValue) {
    java.util.UUID id = java.util.UUID.randomUUID();
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO production.prod_yarn_backfill_reconciliation "
                  + "(id, tenant_id, uid, product_id, article_id, reason, status, candidates) "
                  + "VALUES (?, ?, ?, ?, ?, 'AMBIGUOUS', 'OPEN', "
                  + "jsonb_build_object('schemaVersion', 1, 'candidates', jsonb_build_array("
                  + "jsonb_build_object('rawValue', ?, 'sourceKind', 'BATCH_ACTUAL', "
                  + "'recordedAt', '2026-09-01T00:00:00Z', "
                  + "'sourceRecordId', 'legacy-1', 'overlength', false))))",
              id,
              fixture.tenantId(),
              "YBR-QUEUE-" + id,
              fixture.yarnProductId(),
              articleId,
              rawValue);
          return null;
        });
    return id;
  }

  private void insertOpenWorkOrder(Fixture fixture) {
    java.util.UUID id = java.util.UUID.randomUUID();
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO production.prod_work_order "
                  + "(id, tenant_id, uid, created_at, updated_at, work_order_number, "
                  + "output_product_id, module_type, production_specs, fulfillment_type, "
                  + "planned_qty, unit, status, is_active) VALUES (?, ?, ?, NOW(), NOW(), ?, ?, "
                  + "'SPINNING', '{\"specType\":\"SPINNING\"}'::jsonb, 'INTERNAL', 1, 'KG', "
                  + "'DRAFT', TRUE)",
              id,
              fixture.tenantId(),
              "YBR-WO-" + id,
              "YBR-WO-NO-" + id,
              fixture.yarnProductId());
          return null;
        });
  }

  private void seedDesignation(java.util.UUID articleId, String value) {
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "UPDATE production.prod_yarn_article SET source_designation=? WHERE id=?",
              value,
              articleId);
          return null;
        });
  }

  private void assertRemediated(java.util.UUID articleId, int expectedVersion) {
    assertThat(sourceDesignation(articleId)).isNull();
    assertThat(
            queryOne(
                "SELECT article_spec_version FROM production.prod_yarn_article WHERE id=?",
                Integer.class,
                articleId))
        .isEqualTo(expectedVersion);
    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_yarn_article_audit "
                    + "WHERE article_id=? AND event_type='SPEC_UPDATED' "
                    + "AND changed_summary #> '{sourceDesignation,old}' IS NOT NULL",
                Long.class,
                articleId))
        .isEqualTo(expectedVersion - 1L);
  }

  private String sourceDesignation(java.util.UUID articleId) {
    return queryOne(
        "SELECT source_designation FROM production.prod_yarn_article WHERE id=?",
        String.class,
        articleId);
  }

  private long specUpdatedAuditCount(java.util.UUID articleId) {
    return queryOne(
        "SELECT count(*) FROM production.prod_yarn_article_audit "
            + "WHERE article_id=? AND event_type='SPEC_UPDATED'",
        Long.class,
        articleId);
  }

  private String lastSpecUpdatedOldDesignation(java.util.UUID articleId) {
    return queryOne(
        "SELECT changed_summary #>> '{sourceDesignation,old}' "
            + "FROM production.prod_yarn_article_audit "
            + "WHERE article_id=? AND event_type='SPEC_UPDATED' ORDER BY created_at DESC LIMIT 1",
        String.class,
        articleId);
  }
}
