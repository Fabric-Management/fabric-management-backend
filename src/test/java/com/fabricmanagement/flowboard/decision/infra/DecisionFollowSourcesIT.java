package com.fabricmanagement.flowboard.decision.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.flowboard.decision.infra.repository.DecisionFollowRepository;
import com.fabricmanagement.flowboard.routing.app.RoutingRepairService;
import com.fabricmanagement.flowboard.routing.domain.RoutingPoolKey;
import com.fabricmanagement.flowboard.task.app.TaskService;
import com.fabricmanagement.flowboard.task.domain.AssignedBy;
import com.fabricmanagement.flowboard.task.domain.event.TaskAssignedEvent;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.production.core.workorder.app.WorkOrderService;
import com.fabricmanagement.production.core.workorder.dto.CreateWorkOrderRequest;
import com.fabricmanagement.sales.salesorder.domain.event.OrderCoverCaseChangedEvent;
import com.fabricmanagement.sales.salesorder.domain.event.OrderCoverCaseOpenedEvent;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderConfirmedEvent;
import com.fabricmanagement.sales.salesorder.infra.OrderCoverIntegrationSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;

class DecisionFollowSourcesIT extends OrderCoverIntegrationSupport {
  @Autowired private DecisionFollowRepository follows;
  @Autowired private RoutingRepairService repair;
  @Autowired private TaskService tasks;
  @Autowired private EntityManager entityManager;
  @Autowired private SystemTransactionExecutor systemTransactions;
  @Autowired private ObjectMapper mapper;

  @Test
  void activeOrderCreatorProducesOneOpenedReason() {
    var cover = governed(1);
    awaitReason(cover.caseId(), actor.getId(), "OPENED", 1);
  }

  @Test
  void nullCreatorProducesNoOpenedReasonAndCompletesDelivery() {
    var cover = governedWithCreator(null);
    awaitOpenedDelivery(cover.caseId());
    assertThat(reasonCount(cover.caseId(), null, "OPENED")).isZero();
  }

  @Test
  void systemCreatorProducesNoOpenedReason() {
    var cover = governedWithCreator(SystemUser.ID);
    awaitOpenedDelivery(cover.caseId());
    assertThat(reasonCount(cover.caseId(), null, "OPENED")).isZero();
  }

  @Test
  void inactiveCreatorProducesNoOpenedReason() {
    var creator = user("Inactive creator", "sales:read");
    jdbc.update("update common_user.common_user set is_active=false where id=?", creator.getId());
    var cover = governedWithCreator(creator.getId());
    awaitOpenedDelivery(cover.caseId());
    assertThat(reasonCount(cover.caseId(), creator.getId(), "OPENED")).isZero();
  }

  @Test
  void creatorOutsideTheTenantProducesNoOpenedReason() {
    UUID foreignTenant = UUID.randomUUID();
    UUID foreignUser = UUID.randomUUID();
    systemTransactions.executeUpdate(
        """
        insert into common_user.common_user
          (id,tenant_id,uid,first_name,last_name,organization_id,user_type,is_active,
           created_at,updated_at,version)
        values (?,?,?,?,?,?,'INTERNAL',true,now(),now(),0)
        """,
        foreignUser,
        foreignTenant,
        "FOREIGN-" + foreignUser,
        "Foreign",
        "Creator",
        organization.getId());
    var cover = governedWithCreator(foreignUser);
    awaitOpenedDelivery(cover.caseId());
    assertThat(reasonCount(cover.caseId(), foreignUser, "OPENED")).isZero();
  }

  @Test
  void routingAssignmentPublishesItsAssigneeRowAsTheSourceReference() {
    var cover = governed(1);
    UUID assignmentId =
        jdbc.queryForObject(
            "select id from flowboard.task_assignee where tenant_id=? and task_id=? and user_id=? and is_active",
            UUID.class,
            tenant,
            cover.taskId(),
            actor.getId());
    awaitReason(cover.caseId(), actor.getId(), "ASSIGNED", 1);
    assertThat(sourceRef(cover.caseId(), actor.getId(), "ASSIGNED")).isEqualTo(assignmentId);
  }

  @Test
  void routingRepairAddsTheNewFollowerAndRetainsTheOldReason() {
    var cover = governed(1);
    var replacement =
        user("Replacement", "sales:read", "sales:write", "flowboard:read", "flowboard:write");
    awaitReason(cover.caseId(), actor.getId(), "ASSIGNED", 1);
    configurePool(replacement.getId());
    repair.repair(tenant, RoutingPoolKey.ORDER_COVER, true);
    awaitReason(cover.caseId(), replacement.getId(), "ASSIGNED", 1);
    assertThat(reasonCount(cover.caseId(), actor.getId(), "ASSIGNED")).isOne();
  }

  @Test
  void manualAssignmentUsesTheExistingEventPath() {
    var cover = governed(1);
    var follower = user("Manual follower", "sales:read", "flowboard:read");
    tasks.assignToUser(cover.taskId(), follower.getId(), AssignedBy.MANAGER, actor.getId());
    awaitReason(cover.caseId(), follower.getId(), "ASSIGNED", 1);
    assertThat(sourceRef(cover.caseId(), follower.getId(), "ASSIGNED")).isNotNull();
  }

  @Test
  void assignmentEventForANonGovernedTaskProducesNoReason() {
    UUID unrelatedTask = UUID.randomUUID();
    UUID assignment = UUID.randomUUID();
    var event =
        new TaskAssignedEvent(tenant, unrelatedTask, assignment, actor.getId(), SystemUser.ID);
    tx(
        () -> {
          events.publish(event);
          return null;
        });
    awaitEventDelivery(event.getEventId());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from flowboard.decision_follow where tenant_id=? and source_ref=?",
                Integer.class,
                tenant,
                assignment))
        .isZero();
  }

  @Test
  void successfulSettlementProducesAReceiptBackedReasonAndChangedEvent() {
    var cover = governed(1);
    var result =
        settle(
            cover,
            request(cover, refresh(cover), cover.lineIds(), UUID.randomUUID(), "Produce to order"));
    awaitReason(cover.caseId(), actor.getId(), "SETTLED", 1);
    assertThat(sourceRef(cover.caseId(), actor.getId(), "SETTLED")).isEqualTo(result.result().id());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from event_publication where event_type=? and serialized_event like ?",
                Integer.class,
                OrderCoverCaseChangedEvent.class.getName(),
                "%" + result.result().id() + "%"))
        .isPositive();
  }

  @Test
  void rejectedSettlementProducesNoSettledReason() throws Exception {
    var cover = governed(1);
    var command = request(cover, refresh(cover), cover.lineIds(), UUID.randomUUID(), null);
    postTransition(cover, command)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("COVER_PRECONDITION_FAILED"));
    assertThat(reasonCount(cover.caseId(), actor.getId(), "SETTLED")).isZero();
    assertThat(attempts(command.idempotencyKey())).isOne();
  }

  @Test
  void rollbackAfterWorkOrderInsertLeavesNoReceiptOrFollowToReplay() {
    var cover = governed(1);
    var command =
        request(cover, refresh(cover), cover.lineIds(), UUID.randomUUID(), "Produce to order");
    WorkOrderService target = AopTestUtils.getUltimateTargetObject(production);
    AtomicBoolean failOnce = new AtomicBoolean(true);
    doAnswer(
            invocation -> {
              Object created = invocation.callRealMethod();
              entityManager.flush();
              if (failOnce.getAndSet(false)) {
                throw new IllegalStateException("Failure after work-order insert");
              }
              return created;
            })
        .when(target)
        .createWorkOrder(any(CreateWorkOrderRequest.class));
    assertThatThrownBy(() -> settle(cover, command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Failure after work-order insert");
    assertThat(reasonCount(cover.caseId(), actor.getId(), "SETTLED")).isZero();
    assertThat(count("sales_ord.order_cover_result")).isZero();
  }

  @Test
  void redeliveryOfStoredEventsDoesNotDuplicateReasons() throws Exception {
    var cover = governed(1);
    var receipt =
        settle(
                cover,
                request(
                    cover, refresh(cover), cover.lineIds(), UUID.randomUUID(), "Produce to order"))
            .result()
            .id();
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> assertThat(reasonCount(cover.caseId(), actor.getId(), null)).isEqualTo(3));
    UUID assignment = sourceRef(cover.caseId(), actor.getId(), "ASSIGNED");
    var confirmed = storedEvent(SalesOrderConfirmedEvent.class, cover.orderId());
    var assigned = storedEvent(TaskAssignedEvent.class, cover.taskId());
    var changed = storedEvent(OrderCoverCaseChangedEvent.class, receipt);
    List<DomainEvent> replayed = List.of(confirmed, assigned, changed);
    List<Integer> publicationCounts =
        replayed.stream().map(event -> publicationCount(event.getEventId())).toList();

    tx(
        () -> {
          events.publish(confirmed);
          events.publish(assigned);
          events.publish(changed);
          return null;
        });

    for (int index = 0; index < replayed.size(); index++) {
      awaitRepublishedDelivery(replayed.get(index).getEventId(), publicationCounts.get(index));
    }
    assertThat(reasonCount(cover.caseId(), actor.getId(), null)).isEqualTo(3);
    assertThat(sourceRef(cover.caseId(), actor.getId(), "ASSIGNED")).isEqualTo(assignment);
    assertThat(sourceRef(cover.caseId(), actor.getId(), "SETTLED")).isEqualTo(receipt);
  }

  @Test
  void creatorAssigneeAndSettlerHasThreeReasonsButOneEffectiveFollow() {
    var cover = governed(1);
    settle(
        cover,
        request(cover, refresh(cover), cover.lineIds(), UUID.randomUUID(), "Produce to order"));
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> assertThat(reasonCount(cover.caseId(), actor.getId(), null)).isEqualTo(3));
    assertThat(follows.isEffective(tenant, cover.caseId(), actor.getId())).isTrue();
  }

  private Cover governedWithCreator(UUID createdBy) {
    activation.activate();
    UUID orderId = draft(1);
    jdbc.update("update sales_ord.sales_order set created_by=? where id=?", createdBy, orderId);
    sales.confirmOrder(orderId, actor.getId());
    return awaitCover(orderId);
  }

  private void awaitReason(UUID caseId, UUID userId, String source, int expected) {
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(() -> assertThat(reasonCount(caseId, userId, source)).isEqualTo(expected));
  }

  private int reasonCount(UUID caseId, UUID userId, String source) {
    return jdbc.queryForObject(
        """
        select count(*) from flowboard.decision_follow
        where tenant_id=? and case_id=?
          and (?::uuid is null or user_id=?)
          and (?::text is null or source=?)
        """,
        Integer.class,
        tenant,
        caseId,
        userId,
        userId,
        source,
        source);
  }

  private UUID sourceRef(UUID caseId, UUID userId, String source) {
    return jdbc.queryForObject(
        "select source_ref from flowboard.decision_follow where tenant_id=? and case_id=? and user_id=? and source=?",
        UUID.class,
        tenant,
        caseId,
        userId,
        source);
  }

  private void awaitOpenedDelivery(UUID caseId) {
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              Integer total =
                  jdbc.queryForObject(
                      """
                      select count(*) from event_publication
                      where listener_id like ? and event_type=? and serialized_event like ?
                      """,
                      Integer.class,
                      "%DecisionFollowEventListener%",
                      OrderCoverCaseOpenedEvent.class.getName(),
                      "%" + caseId + "%");
              Integer incomplete =
                  jdbc.queryForObject(
                      """
                      select count(*) from event_publication
                      where listener_id like ? and event_type=? and serialized_event like ?
                        and completion_date is null
                      """,
                      Integer.class,
                      "%DecisionFollowEventListener%",
                      OrderCoverCaseOpenedEvent.class.getName(),
                      "%" + caseId + "%");
              assertThat(total).isPositive();
              assertThat(incomplete).isZero();
            });
  }

  private <T extends DomainEvent> T storedEvent(Class<T> type, UUID subject) throws Exception {
    String serialized =
        jdbc.queryForObject(
            "select serialized_event from event_publication where event_type=? and serialized_event like ? limit 1",
            String.class,
            type.getName(),
            "%" + subject + "%");
    return mapper.readValue(serialized, type);
  }

  private int publicationCount(UUID eventId) {
    return jdbc.queryForObject(
        "select count(*) from event_publication where serialized_event like ?",
        Integer.class,
        "%" + eventId + "%");
  }

  private void awaitRepublishedDelivery(UUID eventId, int previousCount) {
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              assertThat(publicationCount(eventId)).isGreaterThan(previousCount);
              assertThat(
                      jdbc.queryForObject(
                          "select count(*) from event_publication where serialized_event like ? and completion_date is null",
                          Integer.class,
                          "%" + eventId + "%"))
                  .isZero();
            });
  }

  private void awaitEventDelivery(UUID reference) {
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              Integer total =
                  jdbc.queryForObject(
                      """
                      select count(*) from event_publication
                      where listener_id like ? and serialized_event like ?
                      """,
                      Integer.class,
                      "%DecisionFollowEventListener%",
                      "%" + reference + "%");
              Integer incomplete =
                  jdbc.queryForObject(
                      """
                      select count(*) from event_publication
                      where listener_id like ? and serialized_event like ?
                        and completion_date is null
                      """,
                      Integer.class,
                      "%DecisionFollowEventListener%",
                      "%" + reference + "%");
              assertThat(total).isPositive();
              assertThat(incomplete).isZero();
            });
  }
}
