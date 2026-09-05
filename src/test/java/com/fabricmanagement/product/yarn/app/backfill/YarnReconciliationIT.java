package com.fabricmanagement.product.yarn.app.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.yarn.app.YarnArticleService;
import com.fabricmanagement.product.yarn.app.port.LegacyDesignationSourceKind;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillQueueStatus;
import com.fabricmanagement.product.yarn.domain.exception.YarnReconciliationException;
import com.fabricmanagement.product.yarn.dto.YarnReconciliationCandidatePageDto;
import com.fabricmanagement.product.yarn.dto.YarnReconciliationChooseRequest;
import com.fabricmanagement.product.yarn.infra.repository.YarnBackfillReconciliationRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.domain.PageRequest;

class YarnReconciliationIT extends YarnBackfillIntegrationSupport {

  private static final Instant BASE_TIME = Instant.parse("2026-09-01T12:00:00Z");

  @Autowired private YarnReconciliationService reconciliationService;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @SpyBean private YarnArticleService articleService;
  @SpyBean private YarnBackfillReconciliationRepository reconciliationRepository;

  @Test
  void chooseAdoptsStoredBytesBumpsAndAuditsThenListReturnsTypedResolution() {
    TenantFixture fixture = insertTenant("reconciliation-choose", 1);
    UUID chosenRecord = insertBatch(fixture, 0, " Ne 30/1 ", BASE_TIME);
    insertBatch(fixture, 0, "NE 40/1", BASE_TIME.minusSeconds(1));
    backfill(fixture);
    UUID reconciliationId = reconciliationId(fixture);
    UUID articleId = articleId(fixture);

    inTenant(
        fixture,
        () ->
            reconciliationService.choose(
                reconciliationId,
                new YarnReconciliationChooseRequest(
                    LegacyDesignationSourceKind.BATCH_ACTUAL, chosenRecord.toString())));

    assertThat(
            queryOne(
                "SELECT source_designation FROM production.prod_yarn_article WHERE id=?",
                String.class,
                articleId))
        .isEqualTo(" Ne 30/1 ");
    assertThat(
            queryOne(
                "SELECT article_spec_version FROM production.prod_yarn_article WHERE id=?",
                Integer.class,
                articleId))
        .isEqualTo(2);
    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_yarn_article_audit "
                    + "WHERE article_id=? AND event_type='SPEC_UPDATED' "
                    + "AND spec_version_from=1 AND spec_version_to=2 "
                    + "AND changed_summary #> '{sourceDesignation,old}' = 'null'::jsonb "
                    + "AND changed_summary #>> '{sourceDesignation,new}' = ' Ne 30/1 '",
                Long.class,
                articleId))
        .isEqualTo(1L);
    assertThat(
            queryOne(
                "SELECT resolved_candidate ->> 'rawValue' "
                    + "FROM production.prod_yarn_backfill_reconciliation WHERE id=?",
                String.class,
                reconciliationId))
        .isEqualTo(" Ne 30/1 ");
    assertThat(
            queryOne(
                "SELECT reconciliation.resolved_candidate = candidate.elem "
                    + "FROM production.prod_yarn_backfill_reconciliation reconciliation "
                    + "CROSS JOIN LATERAL jsonb_array_elements("
                    + "reconciliation.candidates -> 'candidates') candidate(elem) "
                    + "WHERE reconciliation.id=? "
                    + "AND candidate.elem ->> 'sourceRecordId'=?",
                Boolean.class,
                reconciliationId,
                chosenRecord.toString()))
        .isTrue();

    var resolved =
        inTenant(
                fixture,
                () ->
                    reconciliationService.list(
                        YarnBackfillQueueStatus.RESOLVED, PageRequest.of(0, 20)))
            .getContent()
            .getFirst();
    assertThat(resolved.resolutionAction().name()).isEqualTo("CHOSEN");
    assertThat(resolved.resolvedCandidate().rawValue()).isEqualTo(" Ne 30/1 ");
    assertThat(resolved.resolvedCandidate().sourceRecordId()).isEqualTo(chosenRecord.toString());
    assertThat(resolved.candidateOccurrenceCount()).isEqualTo(2);
  }

  @Test
  void dismissClosesOnlyTheQueueAndLeavesArticleAndAuditUntouched() {
    TenantFixture fixture = insertTenant("reconciliation-dismiss", 1);
    insertBatch(fixture, 0, "Ne 20/1", BASE_TIME);
    insertBatch(fixture, 0, "Ne 40/1", BASE_TIME.minusSeconds(1));
    backfill(fixture);
    UUID reconciliationId = reconciliationId(fixture);
    UUID articleId = articleId(fixture);
    long auditsBefore = auditCount(articleId);

    inTenant(fixture, () -> reconciliationService.dismiss(reconciliationId));

    assertThat(
            queryOne(
                "SELECT resolution_action FROM production.prod_yarn_backfill_reconciliation "
                    + "WHERE id=?",
                String.class,
                reconciliationId))
        .isEqualTo("DISMISSED");
    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_yarn_backfill_reconciliation "
                    + "WHERE id=? AND resolved_candidate IS NULL",
                Long.class,
                reconciliationId))
        .isEqualTo(1L);
    assertThat(
            queryOne(
                "SELECT article_spec_version FROM production.prod_yarn_article WHERE id=?",
                Integer.class,
                articleId))
        .isEqualTo(1);
    assertThat(auditCount(articleId)).isEqualTo(auditsBefore);
  }

  @Test
  void candidatesAreByteGroupedInProvenanceOrderAndOverlengthIsRecomputed() {
    TenantFixture fixture = insertTenant("reconciliation-groups", 1);
    UUID first = insertBatch(fixture, 0, "Ne 30/2", BASE_TIME);
    insertBatch(fixture, 0, "Ne 30/2", BASE_TIME.minusSeconds(1));
    insertBatch(fixture, 0, "Ne 30/2", BASE_TIME.minusSeconds(2));
    insertBatch(fixture, 0, "NE 30/2", BASE_TIME.minusSeconds(3));
    insertBatch(fixture, 0, "N\u00E9 30", BASE_TIME.minusSeconds(4));
    insertBatch(fixture, 0, "Ne\u0301 30", BASE_TIME.minusSeconds(5));
    backfill(fixture);
    UUID reconciliationId = reconciliationId(fixture);

    YarnReconciliationCandidatePageDto firstPage =
        inTenant(fixture, () -> reconciliationService.candidates(reconciliationId, 0, 2));
    YarnReconciliationCandidatePageDto secondPage =
        inTenant(fixture, () -> reconciliationService.candidates(reconciliationId, 1, 2));
    var groups =
        java.util.stream.Stream.concat(firstPage.groups().stream(), secondPage.groups().stream())
            .toList();

    assertThat(firstPage.totalGroups()).isEqualTo(4);
    assertThat(secondPage.totalGroups()).isEqualTo(4);
    assertThat(groups)
        .extracting("rawValue")
        .containsExactly("Ne 30/2", "NE 30/2", "N\u00E9 30", "Ne\u0301 30");
    assertThat(groups.getFirst().occurrences()).isEqualTo(3);
    assertThat(groups.getFirst().sourceRecordId()).isEqualTo(first.toString());

    String overlength = "x".repeat(256);
    TenantFixture overlengthFixture = insertTenant("reconciliation-overlength", 1);
    UUID overlengthRecord = insertBatch(overlengthFixture, 0, overlength, BASE_TIME);
    backfill(overlengthFixture);
    UUID overlengthRow = reconciliationId(overlengthFixture);
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "UPDATE production.prod_yarn_backfill_reconciliation SET candidates = "
                  + "jsonb_set(candidates, '{candidates,0,overlength}', 'false'::jsonb) "
                  + "WHERE id=?",
              overlengthRow);
          return null;
        });

    assertThatThrownBy(
            () ->
                inTenant(
                    overlengthFixture,
                    () ->
                        reconciliationService.choose(
                            overlengthRow,
                            new YarnReconciliationChooseRequest(
                                LegacyDesignationSourceKind.BATCH_ACTUAL,
                                overlengthRecord.toString()))))
        .isInstanceOf(YarnReconciliationException.class)
        .satisfies(
            error ->
                assertThat(((YarnReconciliationException) error).getErrorCode())
                    .isEqualTo("YARN_RECONCILIATION_CANDIDATE_OVERLENGTH"));
    assertThat(
            queryOne(
                "SELECT status FROM production.prod_yarn_backfill_reconciliation WHERE id=?",
                String.class,
                overlengthRow))
        .isEqualTo("OPEN");
    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_yarn_article "
                    + "WHERE tenant_id=? AND source_designation IS NULL "
                    + "AND article_spec_version=1",
                Long.class,
                overlengthFixture.tenantId()))
        .isEqualTo(1L);
  }

  @Test
  void doubleResolveSerializesToOneSuccessAndTheNotOpenCode() throws Exception {
    TenantFixture fixture = insertTenant("reconciliation-race", 1);
    UUID chosenRecord = insertBatch(fixture, 0, "Ne 20/1", BASE_TIME);
    insertBatch(fixture, 0, "Ne 40/1", BASE_TIME.minusSeconds(1));
    backfill(fixture);
    UUID reconciliationId = reconciliationId(fixture);
    CountDownLatch start = new CountDownLatch(1);

    CompletableFuture<String> choose =
        CompletableFuture.supplyAsync(
            () -> {
              await(start);
              try {
                inTenant(
                    fixture,
                    () ->
                        reconciliationService.choose(
                            reconciliationId,
                            new YarnReconciliationChooseRequest(
                                LegacyDesignationSourceKind.BATCH_ACTUAL,
                                chosenRecord.toString())));
                return "CHOOSE_OK";
              } catch (YarnReconciliationException error) {
                return error.getErrorCode();
              }
            });
    CompletableFuture<String> dismiss =
        CompletableFuture.supplyAsync(
            () -> {
              await(start);
              try {
                inTenant(fixture, () -> reconciliationService.dismiss(reconciliationId));
                return "DISMISS_OK";
              } catch (YarnReconciliationException error) {
                return error.getErrorCode();
              }
            });
    start.countDown();

    List<String> outcomes =
        List.of(choose.get(10, TimeUnit.SECONDS), dismiss.get(10, TimeUnit.SECONDS));
    assertThat(outcomes)
        .contains("YARN_RECONCILIATION_NOT_OPEN")
        .anyMatch(value -> value.equals("CHOOSE_OK") || value.equals("DISMISS_OK"));
    assertThat(outcomes).noneMatch(value -> value.equals("OPTIMISTIC_LOCK"));
    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_yarn_backfill_reconciliation "
                    + "WHERE id=? AND status='RESOLVED'",
                Long.class,
                reconciliationId))
        .isEqualTo(1L);
  }

  @Test
  void chooseRejectsAnIdentityThatIsNotInTheStoredArray() {
    TenantFixture fixture = insertTenant("reconciliation-missing-candidate", 1);
    insertBatch(fixture, 0, "Ne 20/1", BASE_TIME);
    insertBatch(fixture, 0, "Ne 40/1", BASE_TIME.minusSeconds(1));
    backfill(fixture);
    UUID reconciliationId = reconciliationId(fixture);

    assertThatThrownBy(
            () ->
                inTenant(
                    fixture,
                    () ->
                        reconciliationService.choose(
                            reconciliationId,
                            new YarnReconciliationChooseRequest(
                                LegacyDesignationSourceKind.BATCH_ACTUAL, "missing-record"))))
        .isInstanceOf(YarnReconciliationException.class)
        .satisfies(
            error ->
                assertThat(((YarnReconciliationException) error).getErrorCode())
                    .isEqualTo("YARN_RECONCILIATION_CANDIDATE_NOT_FOUND"));
    assertThat(
            queryOne(
                "SELECT status FROM production.prod_yarn_backfill_reconciliation WHERE id=?",
                String.class,
                reconciliationId))
        .isEqualTo("OPEN");
  }

  @Test
  void failureAfterArticleAdoptionRollsBackArticleAuditAndOpenRowTogether() {
    TenantFixture fixture = insertTenant("reconciliation-atomic", 1);
    UUID chosenRecord = insertBatch(fixture, 0, "Ne 20/1", BASE_TIME);
    insertBatch(fixture, 0, "Ne 40/1", BASE_TIME.minusSeconds(1));
    backfill(fixture);
    UUID reconciliationId = reconciliationId(fixture);
    UUID articleId = articleId(fixture);
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw new ForcedFailure();
            })
        .when(articleService)
        .adoptSourceDesignation(any(UUID.class), anyString());

    assertThatThrownBy(
            () ->
                inTenant(
                    fixture,
                    () ->
                        reconciliationService.choose(
                            reconciliationId,
                            new YarnReconciliationChooseRequest(
                                LegacyDesignationSourceKind.BATCH_ACTUAL,
                                chosenRecord.toString()))))
        .isInstanceOf(ForcedFailure.class);
    reset(articleService);

    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_yarn_article "
                    + "WHERE id=? AND source_designation IS NULL AND article_spec_version=1",
                Long.class,
                articleId))
        .isEqualTo(1L);
    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_yarn_article_audit "
                    + "WHERE article_id=? AND event_type='SPEC_UPDATED'",
                Long.class,
                articleId))
        .isZero();
    assertThat(
            queryOne(
                "SELECT status FROM production.prod_yarn_backfill_reconciliation WHERE id=?",
                String.class,
                reconciliationId))
        .isEqualTo("OPEN");
  }

  @Test
  void failureFlushingTheResolvedRowRollsBackTheEarlierArticleAdoptionAndAudit() {
    TenantFixture fixture = insertTenant("reconciliation-reverse-atomic", 1);
    UUID chosenRecord = insertBatch(fixture, 0, "Ne 20/1", BASE_TIME);
    insertBatch(fixture, 0, "Ne 40/1", BASE_TIME.minusSeconds(1));
    backfill(fixture);
    UUID reconciliationId = reconciliationId(fixture);
    UUID articleId = articleId(fixture);
    doThrow(new ForcedFailure()).when(reconciliationRepository).flush();

    assertThatThrownBy(
            () ->
                inTenant(
                    fixture,
                    () ->
                        reconciliationService.choose(
                            reconciliationId,
                            new YarnReconciliationChooseRequest(
                                LegacyDesignationSourceKind.BATCH_ACTUAL,
                                chosenRecord.toString()))))
        .isInstanceOf(ForcedFailure.class);
    reset(reconciliationRepository);

    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_yarn_article "
                    + "WHERE id=? AND source_designation IS NULL AND article_spec_version=1",
                Long.class,
                articleId))
        .isEqualTo(1L);
    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_yarn_article_audit "
                    + "WHERE article_id=? AND event_type='SPEC_UPDATED'",
                Long.class,
                articleId))
        .isZero();
    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_yarn_backfill_reconciliation "
                    + "WHERE id=? AND status='OPEN' AND resolution_action IS NULL "
                    + "AND resolved_candidate IS NULL",
                Long.class,
                reconciliationId))
        .isEqualTo(1L);
  }

  @Test
  void listStatementCountIsConstantInTheNumberOfQueueRows() {
    TenantFixture one = insertTenant("reconciliation-statements-one", 1);
    insertBatch(one, 0, "Ne 20/1", BASE_TIME);
    insertBatch(one, 0, "Ne 40/1", BASE_TIME.minusSeconds(1));
    backfill(one);
    TenantFixture ten = insertTenant("reconciliation-statements-ten", 10);
    for (int index = 0; index < 10; index++) {
      insertBatch(ten, index, "Ne 20/1", BASE_TIME);
      insertBatch(ten, index, "Ne 40/1", BASE_TIME.minusSeconds(1));
    }
    backfill(ten);
    var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);

    statistics.clear();
    inTenant(
        one,
        () -> {
          reconciliationService.list(YarnBackfillQueueStatus.OPEN, PageRequest.of(0, 20));
        });
    long oneRowStatements = statistics.getPrepareStatementCount();
    statistics.clear();
    inTenant(
        ten,
        () -> {
          reconciliationService.list(YarnBackfillQueueStatus.OPEN, PageRequest.of(0, 20));
        });
    long tenRowStatements = statistics.getPrepareStatementCount();

    assertThat(tenRowStatements).isEqualTo(oneRowStatements);
  }

  @Test
  void listUsesIdAsTheTieBreakAcrossPagesWhenCreatedAtMatches() {
    TenantFixture fixture = insertTenant("reconciliation-order", 2);
    for (int index = 0; index < 2; index++) {
      insertBatch(fixture, index, "Ne 20/1", BASE_TIME);
      insertBatch(fixture, index, "Ne 40/1", BASE_TIME.minusSeconds(1));
    }
    backfill(fixture);
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "UPDATE production.prod_yarn_backfill_reconciliation "
                  + "SET created_at=CAST('2026-09-01T00:00:00Z' AS timestamptz) "
                  + "WHERE tenant_id=?",
              fixture.tenantId());
          return null;
        });
    assertThat(
            queryOne(
                "SELECT count(DISTINCT created_at) "
                    + "FROM production.prod_yarn_backfill_reconciliation WHERE tenant_id=?",
                Long.class,
                fixture.tenantId()))
        .isEqualTo(1L);
    List<UUID> expected =
        systemTransactions.executeInTransaction(
            jdbc ->
                jdbc.queryForList(
                    "SELECT id FROM production.prod_yarn_backfill_reconciliation "
                        + "WHERE tenant_id=? ORDER BY id ASC",
                    UUID.class,
                    fixture.tenantId()));

    UUID first =
        inTenant(
                fixture,
                () ->
                    reconciliationService.list(YarnBackfillQueueStatus.OPEN, PageRequest.of(0, 1)))
            .getContent()
            .getFirst()
            .id();
    UUID second =
        inTenant(
                fixture,
                () ->
                    reconciliationService.list(YarnBackfillQueueStatus.OPEN, PageRequest.of(1, 1)))
            .getContent()
            .getFirst()
            .id();

    assertThat(List.of(first, second)).containsExactlyElementsOf(expected);
  }

  private UUID reconciliationId(TenantFixture fixture) {
    return queryOne(
        "SELECT id FROM production.prod_yarn_backfill_reconciliation WHERE tenant_id=?",
        UUID.class,
        fixture.tenantId());
  }

  private UUID articleId(TenantFixture fixture) {
    return queryOne(
        "SELECT id FROM production.prod_yarn_article WHERE tenant_id=?",
        UUID.class,
        fixture.tenantId());
  }

  private long auditCount(UUID articleId) {
    return queryOne(
        "SELECT count(*) FROM production.prod_yarn_article_audit WHERE article_id=?",
        Long.class,
        articleId);
  }

  private <T> T inTenant(TenantFixture fixture, java.util.function.Supplier<T> work) {
    return TenantContext.executeInTenantContext(fixture.tenantId(), work);
  }

  private void inTenant(TenantFixture fixture, Runnable work) {
    TenantContext.executeInTenantContext(fixture.tenantId(), work);
  }

  private void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private static final class ForcedFailure extends RuntimeException {}
}
