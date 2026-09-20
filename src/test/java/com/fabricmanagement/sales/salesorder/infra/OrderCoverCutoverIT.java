package com.fabricmanagement.sales.salesorder.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.domain.event.production.WorkOrderRecipeAssignmentNeededEvent;
import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.production.core.workorder.domain.event.WorkOrderApprovedEvent;
import com.fabricmanagement.sales.salesorder.app.ruleengine.SalesOrderRuleEngine;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverRegime;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderConfirmedEvent;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@ResourceLock("sales-order-creation-sequence")
class OrderCoverCutoverIT extends OrderCoverIntegrationSupport {
  @MockitoSpyBean SalesOrderRuleEngine ruleEngine;
  @Autowired com.fabricmanagement.approval.infra.repository.ApprovalPolicyRepository policies;

  @Test
  void installsStrictClockFreeBoundary() {
    Map<String, Object> sequence =
        jdbc.queryForMap(
            "select cache_size,cycle from pg_sequences where schemaname='sales_ord' and"
                + " sequencename='sales_order_creation_seq'");
    assertThat(((Number) sequence.get("cache_size")).longValue()).isEqualTo(1);
    assertThat(sequence.get("cycle")).isEqualTo(false);
    assertThat(
            jdbc.queryForList(
                "select column_name from information_schema.columns where table_schema='sales_ord'"
                    + " and table_name='sales_order' and column_name in"
                    + " ('creation_seq','cover_regime')",
                String.class))
        .containsExactlyInAnyOrder("creation_seq", "cover_regime");
  }

  @Test
  void neverUsedSequenceHasZeroBoundaryAndAllocatesOneFirst() {
    Map<String, Object> previous =
        jdbc.queryForMap("select last_value,is_called from sales_ord.sales_order_creation_seq");
    try {
      jdbc.queryForObject(
          "select setval('sales_ord.sales_order_creation_seq',1,false)", Long.class);

      assertThat(
              jdbc.queryForObject(
                  "select coalesce(pg_sequence_last_value('sales_ord.sales_order_creation_seq'),0)",
                  Long.class))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "select is_called from sales_ord.sales_order_creation_seq", Boolean.class))
          .isFalse();
      assertThat(activation.activate().boundarySeq()).isZero();
      UUID orderId = draft(1);
      assertThat(sequence(orderId)).isEqualTo(1L);
      sales.confirmOrder(orderId, actor.getId());
      awaitCover(orderId);
      assertThat(regime(orderId)).isEqualTo("GOVERNED");
    } finally {
      jdbc.queryForObject(
          "select setval('sales_ord.sales_order_creation_seq',?,?)",
          Long.class,
          ((Number) previous.get("last_value")).longValue(),
          previous.get("is_called"));
    }
  }

  @Test
  void preActivationDraftsRemainLegacyIncludingNullAndEqualityBoundaries() {
    UUID earlier = draft(1), equal = draft(1), oldDeployment = draft(1);
    // Simulate an existing row migrated without a sequence; ordinary inserts always get one.
    jdbc.update("update sales_ord.sales_order set creation_seq=null where id=?", oldDeployment);
    var boundary = activation.activate();
    // The global sequence can include other tenants; pin this fixture to the equality edge.
    jdbc.update(
        "update sales_ord.sales_order set creation_seq=? where id=?",
        boundary.boundarySeq(),
        equal);
    assertThat(sequence(earlier)).isLessThanOrEqualTo(boundary.boundarySeq());
    assertThat(sequence(equal)).isEqualTo(boundary.boundarySeq());
    // This matrix proves only the persisted cutover boundary and synchronous legacy branch. The
    // two dedicated legacy tests below cover the asynchronous production and Flowboard chain.
    doNothing().when(events).publish(any(DomainEvent.class));
    try {
      for (UUID orderId : List.of(earlier, equal, oldDeployment)) {
        sales.confirmOrder(orderId, actor.getId());
        assertThat(regime(orderId)).isEqualTo("LEGACY");
        verify(ruleEngine).processConfirmedOrder(argThat(order -> order.getId().equals(orderId)));
      }
    } finally {
      doCallRealMethod().when(events).publish(any(DomainEvent.class));
    }
    assertThat(count("sales_ord.order_cover_case")).isZero();
  }

  @Test
  void repeatedActivationThroughTheServiceKeepsBoundaryAuditTimeAndActor() {
    var first = activation.activate();
    draft(1);
    var other = user("Other", "sales:read", "flowboard:read");
    authenticate(other);
    assertThat(activation.activate()).isEqualTo(first);
    assertThat(count("sales_ord.order_cover_activation")).isEqualTo(1);
  }

  @Test
  void governedConfirmationAndStoredEventReplayCreateOnlyOneAssignedCoverTask() throws Exception {
    var cover = governed(2);
    assertThat(regime(cover.orderId())).isEqualTo("GOVERNED");
    var confirmed = storedEvent(SalesOrderConfirmedEvent.class, cover.orderId());
    awaitDelivery(confirmed);
    assertGovernedCounts(cover);
    tx(
        () -> {
          events.publish(confirmed);
          return null;
        });
    awaitDelivery(confirmed);
    var opened =
        storedEvent(
            com.fabricmanagement.sales.salesorder.domain.event.OrderCoverCaseOpenedEvent.class,
            cover.caseId());
    tx(
        () -> {
          events.publish(opened);
          return null;
        });
    awaitDelivery(opened);
    assertGovernedCounts(cover);
    verify(ruleEngine, never())
        .processConfirmedOrder(argThat(order -> order.getId().equals(cover.orderId())));
  }

  @Test
  void freeTextGovernedLineIsUnresolvedWithoutAnEarlyProductionTask() throws Exception {
    var cover = governed(1);
    awaitDelivery(storedEvent(SalesOrderConfirmedEvent.class, cover.orderId()));
    assertThat(
            jdbc.queryForObject(
                "select product_id from sales_ord.sales_order_line where id=?",
                UUID.class,
                cover.lineIds().getFirst()))
        .isNull();
    assertThat(
            jdbc.queryForList(
                "select sales_order_line_id from sales_ord.order_cover_case_line where case_id=?"
                    + " and result_id is null",
                UUID.class,
                cover.caseId()))
        .containsExactlyElementsOf(cover.lineIds());
    assertGovernedCounts(cover);
  }

  @Test
  void recipeMissingSettlementCreatesOneDownstreamTaskAndReplayCreatesNone() throws Exception {
    var cover = governed(1);
    var command =
        request(
            cover,
            refresh(cover),
            cover.lineIds(),
            UUID.randomUUID(),
            "Production requires recipe assignment");
    var first = settle(cover, command);
    UUID workOrder = first.result().lines().getFirst().downstream().getFirst().id();
    var recipeEvent = storedEvent(WorkOrderRecipeAssignmentNeededEvent.class, workOrder);
    awaitDelivery(recipeEvent);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from flowboard.task where tenant_id=? and entity_id=? and"
                    + " task_type='RECIPE_ASSIGNMENT'",
                Integer.class,
                tenant,
                workOrder))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForMap(
                "select status,recipe_id from production.prod_work_order where id=?", workOrder))
        .containsEntry("status", "DRAFT")
        .containsEntry("recipe_id", null);
    assertThat(settle(cover, command).result()).isEqualTo(first.result());
    tx(
        () -> {
          events.publish(recipeEvent);
          return null;
        });
    awaitDelivery(recipeEvent);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from flowboard.task where tenant_id=? and entity_id=? and"
                    + " task_type='RECIPE_ASSIGNMENT'",
                Integer.class,
                tenant,
                workOrder))
        .isEqualTo(1);
  }

  @Test
  void legacyConfirmationStillRunsRuleEnginePromotesOneDraftAndPublishesApproval()
      throws Exception {
    UUID orderId = draft(1);
    sales.confirmOrder(orderId, actor.getId());
    awaitLegacyProduction(orderId);
    verify(ruleEngine).processConfirmedOrder(argThat(order -> order.getId().equals(orderId)));
    assertThat(count("sales_ord.order_cover_case")).isZero();
    assertThat(count("production.prod_work_order")).isEqualTo(1);
    UUID workOrder =
        jdbc.queryForObject(
            "select id from production.prod_work_order where tenant_id=?", UUID.class, tenant);
    verify(approval).requiresApproval(eq(tenant), any(), eq("WORK_ORDER"), eq(workOrder));
    awaitDelivery(storedEvent(WorkOrderApprovedEvent.class, workOrder));
    awaitDelivery(storedEvent(SalesOrderConfirmedEvent.class, orderId));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from flowboard.task where tenant_id=? and"
                    + " entity_type='SALES_ORDER' and entity_id=? and task_type='PRODUCTION'",
                Integer.class,
                tenant,
                orderId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from flowboard.task where tenant_id=? and entity_type='WORK_ORDER'"
                    + " and entity_id=? and task_type='PRODUCTION'",
                Integer.class,
                tenant,
                workOrder))
        .isEqualTo(1);
  }

  @Test
  void legacyConfirmationStillHonoursARequiredWorkOrderApprovalPolicy() throws Exception {
    policies.saveAndFlush(
        new com.fabricmanagement.approval.domain.ApprovalPolicy(
            tenant,
            com.fabricmanagement.approval.domain.ApprovalEntityType.WORK_ORDER,
            com.fabricmanagement.approval.domain.PolicyTargetLevel.ALL,
            com.fabricmanagement.approval.domain.ApproverRole.TENANT_ADMIN,
            10,
            48));
    UUID orderId = draft(1);
    sales.confirmOrder(orderId, actor.getId());
    awaitDelivery(storedEvent(SalesOrderConfirmedEvent.class, orderId));
    assertThat(
            jdbc.queryForList(
                "select status from production.prod_work_order where tenant_id=? and"
                    + " sales_order_id=?",
                String.class,
                tenant,
                orderId))
        .containsExactly("PENDING_APPROVAL");
    assertThat(count("common_approval.approval_request")).isEqualTo(1);
    assertThat(count("sales_ord.order_cover_case")).isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from event_publication where event_type=? and serialized_event"
                    + " like ?",
                Integer.class,
                WorkOrderApprovedEvent.class.getName(),
                "%" + tenant + "%"))
        .isZero();
  }

  @Test
  void oldSerializedConfirmationReplaysAsLegacyAndKeepsExistingPromotionBehaviour()
      throws Exception {
    UUID orderId = draft(1);
    // A previously confirmed, pre-cutover order whose serialized event predates coverRegime.
    // Prepare that historical state without publishing a second, modern confirmation event.
    tx(
        () -> {
          var order = orders.findById(orderId).orElseThrow();
          order.confirm();
          orders.saveAndFlush(order);
          return null;
        });
    jdbc.update("update sales_ord.sales_order set creation_seq=null where id=?", orderId);
    UUID lineId =
        jdbc.queryForObject(
            "select id from sales_ord.sales_order_line where sales_order_id=?",
            UUID.class,
            orderId);
    var event =
        new SalesOrderConfirmedEvent(
            tenant,
            orderId,
            "OC-OLD",
            partner,
            "Customer",
            BigDecimal.TEN,
            "kg",
            null,
            List.of(
                new SalesOrderConfirmedEvent.SalesOrderLineSnapshot(
                    lineId, null, "Free-text textile", BigDecimal.TEN, "kg", null)),
            OrderCoverRegime.LEGACY);
    var json = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(event);
    json.remove("coverRegime");
    var restored = mapper.treeToValue(json, SalesOrderConfirmedEvent.class);
    assertThat(restored.getCoverRegime()).isEqualTo(OrderCoverRegime.LEGACY);
    tx(
        () -> {
          events.publish(restored);
          return null;
        });
    awaitDelivery(restored);
    awaitLegacyProduction(orderId);
    assertThat(count("sales_ord.order_cover_case")).isZero();
    assertThat(count("production.prod_work_order")).isEqualTo(1);
  }

  private void assertGovernedCounts(Cover cover) {
    assertThat(count("production.prod_work_order")).isZero();
    assertThat(count("sales_ord.order_cover_case")).isEqualTo(1);
    assertThat(count("sales_ord.order_cover_case_line")).isEqualTo(cover.lineIds().size());
    assertThat(
            jdbc.queryForList(
                "select task_type from flowboard.task where tenant_id=? and entity_id=?",
                String.class,
                tenant,
                cover.orderId()))
        .containsExactly("ORDER_COVER");
    assertThat(
            jdbc.queryForList(
                "select user_id from flowboard.task_assignee where task_id=? and is_active",
                UUID.class,
                cover.taskId()))
        .containsExactly(actor.getId());
  }

  private void awaitLegacyProduction(UUID orderId) {
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () ->
                assertThat(
                        jdbc.queryForList(
                            "select status from production.prod_work_order where tenant_id=? and"
                                + " sales_order_id=?",
                            String.class,
                            tenant,
                            orderId))
                    .containsExactly("APPROVED"));
  }

  private long sequence(UUID orderId) {
    return jdbc.queryForObject(
        "select creation_seq from sales_ord.sales_order where id=?", Long.class, orderId);
  }

  private String regime(UUID orderId) {
    return jdbc.queryForObject(
        "select cover_regime from sales_ord.sales_order where id=?", String.class, orderId);
  }

  private <T extends DomainEvent> T storedEvent(Class<T> type, UUID subject) throws Exception {
    String serialized =
        jdbc.queryForObject(
            "select serialized_event from event_publication where event_type=? and serialized_event"
                + " like ? limit 1",
            String.class,
            type.getName(),
            "%" + subject + "%");
    return mapper.readValue(serialized, type);
  }

  private void awaitDelivery(DomainEvent event) {
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              assertThat(
                      jdbc.queryForObject(
                          "select count(*) from event_publication where serialized_event like ?",
                          Integer.class,
                          "%" + event.getEventId() + "%"))
                  .isPositive();
              assertThat(
                      jdbc.queryForObject(
                          "select count(*) from event_publication where serialized_event like ? and"
                              + " completion_date is null",
                          Integer.class,
                          "%" + event.getEventId() + "%"))
                  .isZero();
            });
  }
}
