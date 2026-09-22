package com.fabricmanagement.flowboard.decision.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.decision.app.DecisionProjectionRebuildService;
import com.fabricmanagement.flowboard.decision.domain.DecisionSubjectProjection;
import com.fabricmanagement.flowboard.decision.dto.DecisionProjectionRebuildRequest;
import com.fabricmanagement.flowboard.decision.infra.repository.*;
import com.fabricmanagement.sales.salesorder.app.OrderCoverProjectionService;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverProjectionPort.Facts;
import com.fabricmanagement.sales.salesorder.infra.OrderCoverIntegrationSupport;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

class DecisionProjectionRebuildIT extends OrderCoverIntegrationSupport {
  @Autowired private DecisionProjectionRebuildService rebuild;
  @Autowired private DecisionSubjectProjectionWriter writer;
  @Autowired private DecisionSubjectProjectionRepository projections;
  @MockitoSpyBean private OrderCoverProjectionService source;

  @Test
  void fullRebuildRecreatesEverySourceWhenProjectionIsEmptyAndThenIsIdempotent() {
    Cover first = governed(1);
    Cover second = governed(1);
    awaitCaseEventsSettled(first.caseId());
    writer.delete(tenant, first.caseId());
    awaitCaseEventsSettled(second.caseId());
    writer.delete(tenant, second.caseId());

    var initial = rebuild.rebuild(tenant, List.of());
    assertThat(initial.inserted()).isGreaterThanOrEqualTo(2);
    assertThat(projections.findByTenantIdAndCaseId(tenant, first.caseId())).isPresent();
    assertThat(projections.findByTenantIdAndCaseId(tenant, second.caseId())).isPresent();
    assertThat(
            projections
                .findByTenantIdAndCaseId(tenant, first.caseId())
                .orElseThrow()
                .getOrderCreatedBy())
        .isEqualTo(actor.getId());
    var projectedAt =
        projections.findByTenantIdAndCaseId(tenant, first.caseId()).orElseThrow().getProjectedAt();

    var repeated = rebuild.rebuild(tenant, List.of());
    assertThat(repeated.inserted()).isZero();
    assertThat(repeated.updated()).isZero();
    assertThat(repeated.orphaned()).isZero();
    assertThat(
            projections
                .findByTenantIdAndCaseId(tenant, first.caseId())
                .orElseThrow()
                .getProjectedAt())
        .isEqualTo(projectedAt);
  }

  @Test
  void selectedRebuildRepairsOnlySelectedIds() {
    Cover selected = governed(1);
    Cover omitted = governed(1);
    rebuild.rebuild(tenant, List.of(selected.caseId(), omitted.caseId()));
    jdbc.update(
        "update flowboard.decision_subject_projection set subject_number='BROKEN' where case_id in (?,?)",
        selected.caseId(),
        omitted.caseId());

    var result = rebuild.rebuild(tenant, List.of(selected.caseId()));
    assertThat(result.updated()).isOne();
    assertThat(number(selected)).isNotEqualTo("BROKEN");
    assertThat(number(omitted)).isEqualTo("BROKEN");
    assertEquivalent(
        projections.findByTenantIdAndCaseId(tenant, selected.caseId()).orElseThrow(),
        source.facts(tenant, List.of(selected.caseId())).getFirst());
  }

  @Test
  void stableSourceAndFullProjectionAreEquivalent() {
    Cover cover = governed(1);
    rebuild.rebuild(tenant, List.of());
    var projected = projections.findByTenantIdAndCaseId(tenant, cover.caseId()).orElseThrow();
    assertEquivalent(projected, source.facts(tenant, List.of(cover.caseId())).getFirst());
  }

  @Test
  void fullRebuildDeletesAProjectionWithoutASourceCase() {
    Cover cover = governed(1);
    var sourceFacts = source.facts(tenant, List.of(cover.caseId())).getFirst();
    UUID missingCaseId = UUID.randomUUID();
    var orphan =
        new Facts(
            sourceFacts.tenantId(),
            missingCaseId,
            sourceFacts.orderId(),
            sourceFacts.orderNumber(),
            sourceFacts.orderCreatedBy(),
            sourceFacts.taskId(),
            sourceFacts.caseState(),
            sourceFacts.caseRevision(),
            sourceFacts.unresolvedLineCount(),
            sourceFacts.openedAt(),
            sourceFacts.closedAt(),
            sourceFacts.evidenceRevision(),
            sourceFacts.verdictCode());
    writer.apply(orphan, null);
    assertThat(projections.findByTenantIdAndCaseId(tenant, missingCaseId)).isPresent();

    var result = rebuild.rebuild(tenant, List.of());

    assertThat(result.orphaned()).isOne();
    assertThat(projections.findByTenantIdAndCaseId(tenant, missingCaseId)).isEmpty();
  }

  @Test
  void rebuildRequiresRoutingManagementAndRejectsMoreThanOneHundredIds() throws Exception {
    performAuthenticated(
            post("/api/v1/flowboard/decisions/projection/rebuild")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());

    var router =
        user("Rebuild operator", "sales:read", "flowboard:read", "flowboard:manage-routing");
    authenticate(router);
    var request =
        new DecisionProjectionRebuildRequest(
            IntStream.range(0, 101).mapToObj(ignored -> java.util.UUID.randomUUID()).toList());
    performAuthenticated(
            post("/api/v1/flowboard/decisions/projection/rebuild")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void eventProjectionCreatedAfterTheScanStartsSurvivesOrphanCleanup() throws Exception {
    governed(1);
    CountDownLatch sourcePageRead = new CountDownLatch(1);
    CountDownLatch continueRebuild = new CountDownLatch(1);
    AtomicBoolean pauseOnce = new AtomicBoolean(true);
    doAnswer(
            invocation -> {
              Object result = invocation.callRealMethod();
              if (pauseOnce.compareAndSet(true, false)) {
                sourcePageRead.countDown();
                assertThat(continueRebuild.await(20, TimeUnit.SECONDS)).isTrue();
              }
              return result;
            })
        .when(sourceTarget())
        .caseIdsAfter(eq(tenant), isNull(), eq(100));

    CompletableFuture<?> running =
        CompletableFuture.supplyAsync(
            () ->
                TenantContext.executeInTenantContext(
                    tenant, () -> rebuild.rebuild(tenant, List.of())));
    assertThat(sourcePageRead.await(20, TimeUnit.SECONDS)).isTrue();
    Cover late = governed(1);
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () ->
                assertThat(projections.findByTenantIdAndCaseId(tenant, late.caseId())).isPresent());

    continueRebuild.countDown();
    running.get(20, TimeUnit.SECONDS);

    assertThat(projections.findByTenantIdAndCaseId(tenant, late.caseId())).isPresent();
  }

  private void assertEquivalent(
      DecisionSubjectProjection projected,
      com.fabricmanagement.sales.salesorder.domain.port.OrderCoverProjectionPort.Facts facts) {
    assertThat(projected.getTenantId()).isEqualTo(facts.tenantId());
    assertThat(projected.getCaseId()).isEqualTo(facts.caseId());
    assertThat(projected.getKind()).isEqualTo("ORDER_COVER");
    assertThat(projected.getSubjectType()).isEqualTo("SALES_ORDER");
    assertThat(projected.getSubjectId()).isEqualTo(facts.orderId());
    assertThat(projected.getSubjectNumber()).isEqualTo(facts.orderNumber());
    assertThat(projected.getOrderCreatedBy()).isEqualTo(facts.orderCreatedBy());
    assertThat(projected.getTaskId()).isEqualTo(facts.taskId());
    assertThat(projected.getCaseState()).isEqualTo(facts.caseState());
    assertThat(projected.getCaseRevision()).isEqualTo(facts.caseRevision());
    assertThat(projected.getUnresolvedLineCount()).isEqualTo(facts.unresolvedLineCount());
    assertThat(projected.getCaseOpenedAt()).isEqualTo(facts.openedAt());
    assertThat(projected.getCaseClosedAt()).isEqualTo(facts.closedAt());
    assertThat(projected.getEvidenceRevision()).isEqualTo(facts.evidenceRevision());
    assertThat(projected.getVerdictCode()).isEqualTo(facts.verdictCode().name());
  }

  private String number(Cover cover) {
    return projections
        .findByTenantIdAndCaseId(tenant, cover.caseId())
        .orElseThrow()
        .getSubjectNumber();
  }

  private OrderCoverProjectionService sourceTarget() {
    return AopTestUtils.getUltimateTargetObject(source);
  }
}
