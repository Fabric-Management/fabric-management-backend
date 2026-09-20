package com.fabricmanagement.sales.salesorder.infra;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fabricmanagement.approval.domain.*;
import com.fabricmanagement.approval.infra.repository.ApprovalPolicyRepository;
import com.fabricmanagement.common.infrastructure.persistence.SalesOrderLineFulfilmentLock;
import com.fabricmanagement.inventory.reservation.app.StockReservationService;
import com.fabricmanagement.production.core.workorder.app.WorkOrderService;
import com.fabricmanagement.production.core.workorder.domain.event.WorkOrderApprovedEvent;
import com.fabricmanagement.production.core.workorder.dto.CreateWorkOrderRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverResultDto;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;

class OrderCoverSettlementIT extends OrderCoverIntegrationSupport {
  @Autowired SalesOrderLineFulfilmentLock fulfilmentLock;
  @Autowired StockReservationService reservations;
  @Autowired ApprovalPolicyRepository policies;
  @Autowired EntityManager entityManager;

  @Test
  void unknownSuitabilityCreatesFullQuantityDraftsAndImmutableProfileBoundReceipt() {
    var cover = governed(2);
    tx(
        () -> {
          var partlyShipped = lines.findById(cover.lineIds().getFirst()).orElseThrow();
          partlyShipped.setRequestedQty(new BigDecimal("12.000"));
          partlyShipped.setShippedQty(new BigDecimal("2.000"));
          lines.saveAndFlush(partlyShipped);
          return null;
        });
    var snapshot = refresh(cover);
    assertThat(snapshot.lines())
        .allSatisfy(
            line ->
                assertThat(line.suitability())
                    .isEqualTo(OrderCoverEvidenceDto.Suitability.UNKNOWN));
    policies.saveAndFlush(
        new ApprovalPolicy(
            tenant,
            ApprovalEntityType.WORK_ORDER,
            PolicyTargetLevel.ALL,
            ApproverRole.TENANT_ADMIN,
            10,
            48));
    clearInvocations(approval, events);

    var result =
        settle(
            cover,
            request(
                cover,
                snapshot,
                cover.lineIds(),
                UUID.randomUUID(),
                "Customer requires new production despite unresolved stock suitability"));

    assertThat(result.result().rationale()).contains("Customer requires");
    assertThat(result.result().lines())
        .hasSize(2)
        .allSatisfy(
            line -> {
              assertThat(line.suitabilityAtDecision())
                  .isEqualTo(OrderCoverEvidenceDto.Suitability.UNKNOWN);
              assertThat(line.outcome()).isEqualTo(OrderCoverResultDto.Outcome.MAKE_TO_ORDER);
              assertThat(new BigDecimal(line.quantity().value())).isEqualByComparingTo("10");
              UUID workOrderId = line.downstream().getFirst().id();
              var persisted =
                  jdbc.queryForMap(
                      """
                      select status,planned_qty,sales_order_id,sales_order_line_id,product_code,
                             requirement_profile_id,requirement_profile_version,
                             requirement_profile_snapshot->'unmodelledConstraints'->0->>'field' as constraint_field
                      from production.prod_work_order where id=?
                      """,
                      workOrderId);
              assertThat(persisted)
                  .containsEntry("status", "DRAFT")
                  .containsEntry("sales_order_id", cover.orderId())
                  .containsEntry("sales_order_line_id", line.lineId())
                  .containsEntry("requirement_profile_id", line.requirementProfileId())
                  .containsEntry("requirement_profile_version", 1)
                  .containsEntry("constraint_field", "composition");
              assertThat((String) persisted.get("product_code"))
                  .startsWith("Customer specified textile");
              assertThat((BigDecimal) persisted.get("planned_qty")).isEqualByComparingTo("10");
            });
    assertThat(count("production.prod_work_order")).isEqualTo(2);
    assertThat(count("sales_ord.order_cover_result")).isEqualTo(1);
    assertThat(count("sales_ord.order_cover_line_result")).isEqualTo(2);
    verify(approval, never()).requiresApproval(eq(tenant), any(), eq("WORK_ORDER"), any());
    verify(approval, never())
        .requiresApproval(eq(tenant), any(), eq("WORK_ORDER"), any(), any(), any());
    verify(events, never()).publish(isA(WorkOrderApprovedEvent.class));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from event_publication where event_type=? and serialized_event"
                    + " like ?",
                Integer.class,
                WorkOrderApprovedEvent.class.getName(),
                "%" + tenant + "%"))
        .isZero();
    assertThat(count("common_approval.approval_request")).isZero();
  }

  @Test
  void lostResponseReplayReturnsSameReceiptAndWorkOrdersBeforeCheckingStaleVersion()
      throws Exception {
    var cover = governed(1);
    var command =
        request(cover, refresh(cover), cover.lineIds(), UUID.randomUUID(), "Produce to order");
    var first = settle(cover, command);
    assertThat(taskVersion(cover)).isGreaterThan(command.expectedVersion());
    var replay = settle(cover, command);
    assertThat(replay.replayed()).isTrue();
    assertThat(replay.result()).isEqualTo(first.result());
    assertThat(replay.taskVersion()).isEqualTo(taskVersion(cover));
    postTransition(cover, command)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.replayed").value(true))
        .andExpect(jsonPath("$.data.result.id").value(first.result().id().toString()));
    assertThat(count("production.prod_work_order")).isEqualTo(1);
    assertThat(count("sales_ord.order_cover_result")).isEqualTo(1);
    assertThat(attempts(command.idempotencyKey())).isEqualTo(1);
  }

  @Test
  void changedPayloadCannotReuseACommittedKey() throws Exception {
    var cover = governed(1);
    var snapshot = refresh(cover);
    UUID key = UUID.randomUUID();
    settle(cover, request(cover, snapshot, cover.lineIds(), key, "First decision"));
    postTransition(cover, request(cover, snapshot, cover.lineIds(), key, "Different decision"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    assertThat(attempts(key)).isEqualTo(1);
    assertThat(count("production.prod_work_order")).isEqualTo(1);
    assertThat(count("sales_ord.order_cover_result")).isEqualTo(1);
  }

  @Test
  void supersededEvidenceRollsBackClaimAndSameKeySucceedsWithCurrentRevision() throws Exception {
    var cover = governed(1);
    var before = refresh(cover);
    var current = evidence.rebuild(cover.orderId(), cover.caseId());
    assertThat(current.revision()).isGreaterThan(before.revision());
    UUID key = UUID.randomUUID();
    postTransition(cover, request(cover, before, cover.lineIds(), key, "Produce to order"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EVIDENCE_CHANGED"));
    assertThat(attempts(key)).isZero();
    assertNoSettlement();
    assertThat(
            settle(cover, request(cover, current, cover.lineIds(), key, "Produce to order"))
                .result()
                .evidenceId())
        .isEqualTo(current.id());
  }

  @Test
  void staleTaskVersionRollsBackClaimAndSameKeySucceedsWithCurrentVersion() throws Exception {
    var cover = governed(1);
    var snapshot = refresh(cover);
    var current = request(cover, snapshot, cover.lineIds(), UUID.randomUUID(), "Produce to order");
    var stale =
        new com.fabricmanagement.flowboard.task.dto.DecisionTransitionRequest(
            current.action(),
            current.expectedVersion() + 1,
            current.idempotencyKey(),
            current.payload());
    postTransition(cover, stale)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TASK_VERSION_CONFLICT"));
    assertThat(attempts(current.idempotencyKey())).isZero();
    assertNoSettlement();
    settle(cover, current);
    assertThat(attempts(current.idempotencyKey())).isEqualTo(1);
  }

  @Test
  void changedLiveRequirementIsRejectedEvenWithoutANewerStoredEvidenceRevision() throws Exception {
    var cover = governed(1);
    var before = refresh(cover);
    tx(
        () -> {
          var line = lines.findById(cover.lineIds().getFirst()).orElseThrow();
          attachProfile(line, line.getRequirementProfileId(), 2, true);
          return null;
        });
    UUID key = UUID.randomUUID();
    postTransition(cover, request(cover, before, cover.lineIds(), key, "Produce to order"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EVIDENCE_CHANGED"));
    assertThat(attempts(key)).isZero();
    assertNoSettlement();
    settle(cover, request(cover, refresh(cover), cover.lineIds(), key, "Produce to order"));
  }

  @Test
  void missingRationaleCommitsOnlyTheRejectedAttemptAndConsumesItsKey() throws Exception {
    var cover = governed(1);
    var snapshot = refresh(cover);
    UUID key = UUID.randomUUID();
    long version = taskVersion(cover);
    var command = request(cover, snapshot, cover.lineIds(), key, null);
    postTransition(cover, command)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("COVER_PRECONDITION_FAILED"));
    assertThat(
            jdbc.queryForMap(
                "select outcome,rejection_code,rejection_message from"
                    + " flowboard.task_transition_attempt where tenant_id=? and idempotency_key=?",
                tenant,
                key.toString()))
        .containsEntry("outcome", "REJECTED_BUSINESS")
        .containsEntry("rejection_code", "COVER_PRECONDITION_FAILED")
        .containsEntry("rejection_message", "RATIONALE_REQUIRED");
    assertThat(taskVersion(cover)).isEqualTo(version);
    assertNoSettlement();
    postTransition(cover, command).andExpect(status().isConflict());
    postTransition(cover, request(cover, snapshot, cover.lineIds(), key, "Now supplying a reason"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    assertThat(attempts(key)).isEqualTo(1);
  }

  @Test
  void incompleteRequirementRefusesTheWholeSelectedSubsetWithoutProduction() throws Exception {
    var cover = governed(2);
    tx(
        () -> {
          var line = lines.findById(cover.lineIds().getLast()).orElseThrow();
          attachProfile(line, line.getRequirementProfileId(), 2, false);
          return null;
        });
    UUID key = UUID.randomUUID();
    postTransition(
            cover,
            request(cover, refresh(cover), cover.lineIds(), key, "Cannot override incompleteness"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("COVER_PRECONDITION_FAILED"));
    assertThat(
            jdbc.queryForObject(
                "select rejection_message from flowboard.task_transition_attempt where tenant_id=?"
                    + " and idempotency_key=?",
                String.class,
                tenant,
                key.toString()))
        .contains("UNSPECIFIED:WIDTH");
    assertNoSettlement();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void fulfilmentCommittedAfterEvidenceIsFoundByTheRealServerRecheck(boolean competingProduction)
      throws Exception {
    var cover = governed(1);
    var snapshot = refresh(cover);
    UUID lineId = cover.lineIds().getFirst();
    var stock = competingProduction ? null : reservationStock();
    // A separate committed writer runs after the client obtained its evidence.
    try (var executor = Executors.newSingleThreadExecutor()) {
      executor
          .submit(
              () ->
                  inActor(
                      () -> {
                        if (competingProduction) {
                          production.createWorkOrder(
                              CreateWorkOrderRequest.builder()
                                  .salesOrderId(cover.orderId())
                                  .salesOrderLineId(lineId)
                                  .tradingPartnerId(partner)
                                  .plannedQty(BigDecimal.TEN)
                                  .unit("kg")
                                  .currency("GBP")
                                  .build());
                        } else {
                          reservations.createReservation(
                              lineId,
                              stock.locationId(),
                              stock.productId(),
                              "RIVAL-" + suffix,
                              null,
                              BigDecimal.TEN);
                        }
                        return null;
                      }))
          .get(15, TimeUnit.SECONDS);
    }
    UUID key = UUID.randomUUID();
    postTransition(
            cover,
            request(cover, snapshot, cover.lineIds(), key, "Client believed the line was free"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("COVER_PRECONDITION_FAILED"));
    assertThat(
            jdbc.queryForObject(
                "select rejection_message from flowboard.task_transition_attempt where tenant_id=?"
                    + " and idempotency_key=?",
                String.class,
                tenant,
                key.toString()))
        .isEqualTo(competingProduction ? "ACTIVE_PRODUCTION_EXISTS" : "ACTIVE_RESERVATION_EXISTS");
    assertThat(count("production.prod_work_order")).isEqualTo(competingProduction ? 1 : 0);
    assertThat(count("sales_ord.order_cover_result")).isZero();
    assertThat(count("sales_ord.order_cover_line_result")).isZero();
  }

  @Test
  void technicalFailureAfterFlushedWorkOrderRollsBackEverythingAndSameCommandCanRetry() {
    var cover = governed(1);
    var command =
        request(cover, refresh(cover), cover.lineIds(), UUID.randomUUID(), "Produce to order");
    WorkOrderService target = AopTestUtils.getUltimateTargetObject(production);
    AtomicBoolean failOnce = new AtomicBoolean(true);
    doAnswer(
            invocation -> {
              Object created = invocation.callRealMethod();
              entityManager.flush();
              if (failOnce.getAndSet(false))
                throw new IllegalStateException("Failure after work-order insert");
              return created;
            })
        .when(target)
        .createWorkOrder(any(CreateWorkOrderRequest.class));
    assertThatThrownBy(() -> settle(cover, command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Failure after work-order insert");
    assertThat(attempts(command.idempotencyKey())).isZero();
    assertNoSettlement();
    assertThat(taskVersion(cover)).isEqualTo(command.expectedVersion());
    assertThat(settle(cover, command).replayed()).isFalse();
    assertThat(count("production.prod_work_order")).isEqualTo(1);
    assertThat(count("sales_ord.order_cover_result")).isEqualTo(1);
  }

  @Test
  void exhaustedLineLockReturnsTypedConflictAndReleasesTheIdempotencyKey() throws Exception {
    var cover = governed(1);
    var command =
        request(cover, refresh(cover), cover.lineIds(), UUID.randomUUID(), "Produce to order");
    CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var holder =
          executor.submit(
              () ->
                  inActor(
                      () ->
                          tx(
                              () -> {
                                fulfilmentLock.lock(tenant, cover.lineIds().getFirst());
                                held.countDown();
                                try {
                                  if (!release.await(15, TimeUnit.SECONDS))
                                    throw new AssertionError("Holder timeout");
                                } catch (InterruptedException error) {
                                  Thread.currentThread().interrupt();
                                  throw new AssertionError(error);
                                }
                                return true;
                              })));
      assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
      Instant started = Instant.now();
      postTransition(cover, command)
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("LOCK_TIMEOUT"));
      assertThat(Duration.between(started, Instant.now())).isLessThan(Duration.ofSeconds(12));
      assertThat(attempts(command.idempotencyKey())).isZero();
      assertNoSettlement();
      release.countDown();
      assertThat(holder.get(10, TimeUnit.SECONDS)).isTrue();
      settle(cover, command);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void manualReceiptSchemaAllowsOnlyMakeToOrderAndPinsRequirementProfile() {
    String definition =
        jdbc.queryForObject(
            "select pg_get_constraintdef(oid) from pg_constraint where"
                + " conname='chk_order_cover_manual_outcome'",
            String.class);
    assertThat(definition).contains("MAKE_TO_ORDER").doesNotContain("STOCK");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_schema='production'"
                    + " and table_name='prod_work_order' and column_name in"
                    + " ('requirement_profile_id','requirement_profile_version','requirement_profile_snapshot')",
                Integer.class))
        .isEqualTo(3);
  }
}
