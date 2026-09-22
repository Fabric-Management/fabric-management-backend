package com.fabricmanagement.flowboard.decision.infra;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.flowboard.decision.app.*;
import com.fabricmanagement.flowboard.decision.app.listener.DecisionFollowEventListener;
import com.fabricmanagement.flowboard.decision.domain.DecisionQueueBucket;
import com.fabricmanagement.flowboard.task.app.DecisionBlockedException;
import com.fabricmanagement.flowboard.task.app.OrderCoverCaseOpenedListener;
import com.fabricmanagement.flowboard.task.domain.event.TaskAssignedEvent;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.UserDepartment;
import com.fabricmanagement.platform.user.infra.repository.UserDepartmentRepository;
import com.fabricmanagement.sales.salesorder.app.SalesOrderAccessPolicy;
import com.fabricmanagement.sales.salesorder.domain.event.OrderCoverCaseOpenedEvent;
import com.fabricmanagement.sales.salesorder.infra.OrderCoverIntegrationSupport;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

class DecisionQueueSecurityIT extends OrderCoverIntegrationSupport {
  @Autowired private DecisionQueueService queue;
  @Autowired private DecisionProjectionRebuildService rebuild;
  @Autowired private DepartmentRepository departments;
  @Autowired private UserDepartmentRepository userDepartments;
  @Autowired private SalesOrderAccessPolicy salesOrderAccess;
  @Autowired private IncompleteEventPublications incompletePublications;
  @MockitoSpyBean private OrderCoverCaseOpenedListener taskCreationListener;

  @Test
  void activeReadableCallerSeesOneRowDespiteDuplicateAssignmentReasons() {
    Cover cover = governed(1);
    rebuild.rebuild(tenant, java.util.List.of(cover.caseId()));
    jdbc.update(
        """
        insert into flowboard.task_assignee
          (id,tenant_id,uid,task_id,user_id,assigned_by,assigned_at,is_active,
           created_at,updated_at,version)
        values (gen_random_uuid(),?,gen_random_uuid()::text,?,?,'SYSTEM',now(),true,now(),now(),0),
               (gen_random_uuid(),?,gen_random_uuid()::text,?,?,'SYSTEM',now(),true,now(),now(),0)
        """,
        tenant,
        cover.taskId(),
        actor.getId(),
        tenant,
        cover.taskId(),
        actor.getId());

    var page = queue.list(tenant, actor.getId(), DecisionQueueBucket.MINE, 0, 20);
    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getTotalElements()).isOne();
  }

  @Test
  void dueDatesSortAsCalendarDatesWithNullLast() {
    Cover later = governed(1);
    Cover none = governed(1);
    Cover earlier = governed(1);
    jdbc.update(
        "update flowboard.task set deadline=? where id=?",
        LocalDate.of(2026, 10, 2),
        later.taskId());
    jdbc.update("update flowboard.task set deadline=null where id=?", none.taskId());
    jdbc.update(
        "update flowboard.task set deadline=? where id=?",
        LocalDate.of(2026, 10, 1),
        earlier.taskId());
    rebuild.rebuild(tenant, java.util.List.of(later.caseId(), none.caseId(), earlier.caseId()));

    var items = queue.list(tenant, actor.getId(), DecisionQueueBucket.MINE, 0, 20).getContent();
    assertThat(items)
        .extracting(item -> item.dueDate())
        .containsSubsequence(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), null);
  }

  @Test
  void dueDateHttpContractUsesCalendarDatesAndExplicitNull() throws Exception {
    Cover dated = governed(1);
    Cover undated = governed(1);
    jdbc.update(
        "update flowboard.task set deadline=? where id=?",
        LocalDate.of(2026, 10, 1),
        dated.taskId());
    jdbc.update("update flowboard.task set deadline=null where id=?", undated.taskId());
    rebuild.rebuild(tenant, java.util.List.of(dated.caseId(), undated.caseId()));

    performAuthenticated(get("/api/v1/flowboard/decisions").param("size", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].dueDate").value("2026-10-01"))
        .andExpect(jsonPath("$.data.content[1].dueDate").isEmpty());
  }

  @Test
  void deactivatedCallerCannotUseAnExistingAuthentication() throws Exception {
    Cover cover = governed(1);
    rebuild.rebuild(tenant, java.util.List.of(cover.caseId()));
    jdbc.update("update common_user.common_user set is_active=false where id=?", actor.getId());
    assertThatThrownBy(() -> queue.list(tenant, actor.getId(), DecisionQueueBucket.MINE, 0, 20))
        .isInstanceOf(AccessDeniedException.class);
    performAuthenticated(get("/api/v1/flowboard/decisions"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.data").doesNotExist());
    performAuthenticated(get("/api/v1/flowboard/decisions/summary"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  void summaryIsStaleOnlyAfterARelevantPublicationHasWaitedThirtySeconds() throws Exception {
    assertThat(queue.summary(tenant, actor.getId()).stale()).isFalse();
    UUID publicationId = UUID.randomUUID();
    var event =
        new TaskAssignedEvent(
            tenant, UUID.randomUUID(), UUID.randomUUID(), actor.getId(), actor.getId());
    String listenerId =
        DecisionFollowEventListener.class.getName()
            + ".onTaskAssigned("
            + TaskAssignedEvent.class.getName()
            + ")";
    try {
      jdbc.update(
          """
          insert into event_publication
            (id,listener_id,event_type,serialized_event,publication_date,completion_date)
          values (?,?,?,?,now() - interval '29 seconds',null)
          """,
          publicationId,
          listenerId,
          TaskAssignedEvent.class.getName(),
          mapper.writeValueAsString(event));
      assertThat(queue.summary(tenant, actor.getId()).stale()).isFalse();
      jdbc.update(
          "update event_publication set publication_date=now() - interval '31 seconds' where id=?",
          publicationId);
      assertThat(queue.summary(tenant, actor.getId()).stale()).isTrue();
    } finally {
      jdbc.update("delete from event_publication where id=?", publicationId);
    }
    assertThat(queue.summary(tenant, actor.getId()).stale()).isFalse();
  }

  @Test
  void unassignedBucketRequiresRoutingPermissionAndThenReturnsTheOwnerlessTask() {
    Cover cover = governed(1);
    jdbc.update(
        "update flowboard.task_assignee set is_active=false where task_id=?", cover.taskId());
    rebuild.rebuild(tenant, java.util.List.of(cover.caseId()));
    assertThatThrownBy(
            () -> queue.list(tenant, actor.getId(), DecisionQueueBucket.UNASSIGNED, 0, 20))
        .isInstanceOf(DecisionBlockedException.class);

    var router =
        user(
            "Router",
            "sales:read",
            "sales:write",
            "flowboard:read",
            "flowboard:write",
            "flowboard:manage-routing");
    authenticate(router);
    assertThat(
            queue.list(tenant, router.getId(), DecisionQueueBucket.UNASSIGNED, 0, 20).getContent())
        .extracting(item -> item.id())
        .containsExactly(cover.caseId());
  }

  @Test
  void departmentAssignmentUsesCurrentMembershipBeforePaging() {
    Cover cover = governed(1);
    Department department =
        departments.saveAndFlush(
            Department.create(
                organization.getId(), "Decision team", "DQ-" + suffix, "Decision queue test"));
    userDepartments.saveAndFlush(UserDepartment.create(actor, department, true, actor.getId()));
    jdbc.update(
        "update flowboard.task_assignee set is_active=false where task_id=?", cover.taskId());
    jdbc.update(
        """
        insert into flowboard.task_assignee
          (id,tenant_id,uid,task_id,department_id,assigned_by,assigned_at,is_active,
           created_at,updated_at,version)
        values (gen_random_uuid(),?,gen_random_uuid()::text,?,?,'SYSTEM',now(),true,now(),now(),0)
        """,
        tenant,
        cover.taskId(),
        department.getId());
    rebuild.rebuild(tenant, java.util.List.of(cover.caseId()));
    assertThat(
            queue.list(tenant, actor.getId(), DecisionQueueBucket.DEPARTMENT, 0, 20).getContent())
        .extracting(item -> item.id())
        .containsExactly(cover.caseId());
  }

  @Test
  void caseWithoutTaskIsExcludedFromEveryBucketAndSummaryCount() {
    Cover cover = governed(1);
    rebuild.rebuild(tenant, java.util.List.of(cover.caseId()));
    jdbc.update(
        "update flowboard.decision_subject_projection set task_id=null where case_id=?",
        cover.caseId());
    for (DecisionQueueBucket bucket : DecisionQueueBucket.values()) {
      if (bucket != DecisionQueueBucket.UNASSIGNED) {
        assertThat(queue.list(tenant, actor.getId(), bucket, 0, 20).getContent()).isEmpty();
      }
    }
    var summary = queue.summary(tenant, actor.getId());
    assertThat(summary.mineCount()).isZero();
    assertThat(summary.departmentCount()).isZero();
    assertThat(summary.waitingCount()).isZero();
  }

  @Test
  void failedTaskCreationIsStaleAndRedeliveryMakesTheCaseVisible() {
    doThrow(new IllegalStateException("injected task creation failure"))
        .when(taskCreationListenerTarget())
        .onOpened(any(OrderCoverCaseOpenedEvent.class));
    activation.activate();
    UUID orderId = draft(1);
    sales.confirmOrder(orderId, actor.getId());
    UUID caseId =
        jdbc.queryForObject(
            "select id from sales_ord.order_cover_case where sales_order_id=?",
            UUID.class,
            orderId);

    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () ->
                assertThat(
                        jdbc.queryForObject(
                            """
                            select count(*) from event_publication
                            where listener_id like '%OrderCoverCaseOpenedListener.%'
                              and serialized_event like ? and completion_date is null
                            """,
                            Integer.class, "%" + caseId + "%"))
                    .isOne());
    assertThat(queue.list(tenant, actor.getId(), DecisionQueueBucket.MINE, 0, 20).getContent())
        .isEmpty();
    jdbc.update(
        """
        update event_publication set publication_date=now() - interval '31 seconds'
        where listener_id like '%OrderCoverCaseOpenedListener.%'
          and serialized_event like ? and completion_date is null
        """,
        "%" + caseId + "%");
    assertThat(queue.summary(tenant, actor.getId()).stale()).isTrue();

    doCallRealMethod()
        .when(taskCreationListenerTarget())
        .onOpened(any(OrderCoverCaseOpenedEvent.class));
    incompletePublications.resubmitIncompletePublicationsOlderThan(Duration.ZERO);

    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              assertThat(
                      queue
                          .list(tenant, actor.getId(), DecisionQueueBucket.MINE, 0, 20)
                          .getContent())
                  .extracting(item -> item.id())
                  .contains(caseId);
              assertThat(queue.summary(tenant, actor.getId()).stale()).isFalse();
            });
  }

  @Test
  void queueReadScopeMatchesSalesOrderAccessPolicyForEveryScopeAndCreatorShape() {
    List<Cover> covers =
        java.util.stream.IntStream.range(0, 4).mapToObj(ignored -> governed(1)).toList();
    Department department =
        departments.saveAndFlush(
            Department.create(
                organization.getId(), "Read scope team", "DQ-SCOPE-" + suffix, "Scope parity"));
    var colleague = user("Scope colleague");
    var outsider = user("Scope outsider");
    userDepartments.saveAndFlush(UserDepartment.create(colleague, department, true, actor.getId()));

    for (DataScope scope :
        Arrays.asList(
            DataScope.GLOBAL, DataScope.ORGANIZATION, DataScope.DEPARTMENT, DataScope.OWN, null)) {
      var reader = user("Reader " + String.valueOf(scope), "sales:read", "flowboard:read");
      userDepartments.saveAndFlush(UserDepartment.create(reader, department, true, actor.getId()));
      if (scope == null) {
        jdbc.update(
            "delete from common_user.permission_template "
                + "where role_code=? and resource='sales' and action='read'",
            reader.getRole().getRoleCode());
      } else {
        jdbc.update(
            "update common_user.permission_template set data_scope=? "
                + "where role_code=? and resource='sales' and action='read'",
            scope.name(),
            reader.getRole().getRoleCode());
      }

      List<UUID> creators = List.of(reader.getId(), colleague.getId(), outsider.getId());
      for (int index = 0; index < covers.size(); index++) {
        Cover cover = covers.get(index);
        UUID creator = index < creators.size() ? creators.get(index) : null;
        jdbc.update(
            "update sales_ord.sales_order set created_by=? where id=?", creator, cover.orderId());
        jdbc.update(
            """
            insert into flowboard.task_assignee
              (id,tenant_id,uid,task_id,user_id,assigned_by,assigned_at,is_active,
               created_at,updated_at,version)
            values (gen_random_uuid(),?,gen_random_uuid()::text,?,?,'SYSTEM',now(),true,now(),now(),0)
            """,
            tenant,
            cover.taskId(),
            reader.getId());
      }
      rebuild.rebuild(tenant, covers.stream().map(Cover::caseId).toList());
      authenticate(reader);

      Set<UUID> expected =
          covers.stream()
              .filter(
                  cover ->
                      salesOrderAccess.canRead(
                          tenant, reader.getId(), orders.findById(cover.orderId()).orElseThrow()))
              .map(Cover::caseId)
              .collect(java.util.stream.Collectors.toSet());
      Set<UUID> actual =
          queue.list(tenant, reader.getId(), DecisionQueueBucket.MINE, 0, 100).getContent().stream()
              .map(item -> item.id())
              .collect(java.util.stream.Collectors.toSet());

      assertThat(actual).as("scope %s", scope).isEqualTo(expected);
      assertThat(queue.summary(tenant, reader.getId()).mineCount()).isEqualTo(expected.size());
    }
  }

  @Test
  void unsupportedClientSortIsRejected() throws Exception {
    performAuthenticated(get("/api/v1/flowboard/decisions").param("sort", "dueDate,desc"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void callerWithoutFlowboardReadReceivesForbidden() throws Exception {
    var salesOnly = user("Sales-only reader", "sales:read");
    authenticate(salesOnly);

    performAuthenticated(get("/api/v1/flowboard/decisions")).andExpect(status().isForbidden());
  }

  @Test
  void callerIdentityFiltersUnknownBucketAndOversizedPageAreRejected() throws Exception {
    performAuthenticated(get("/api/v1/flowboard/decisions").param("tenantId", tenant.toString()))
        .andExpect(status().isBadRequest());
    performAuthenticated(
            get("/api/v1/flowboard/decisions").param("userId", actor.getId().toString()))
        .andExpect(status().isBadRequest());
    performAuthenticated(get("/api/v1/flowboard/decisions").param("bucket", "UNKNOWN"))
        .andExpect(status().isBadRequest());
    performAuthenticated(get("/api/v1/flowboard/decisions").param("size", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void explicitUnassignedRequestReturnsTypedManageRoutingRequirement() throws Exception {
    performAuthenticated(get("/api/v1/flowboard/decisions").param("bucket", "UNASSIGNED"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.reason.code").value("PERMISSION_DENIED"))
        .andExpect(
            jsonPath("$.reason.parameters.requiredPermission").value("flowboard:manage-routing"));
  }

  private OrderCoverCaseOpenedListener taskCreationListenerTarget() {
    return AopTestUtils.getUltimateTargetObject(taskCreationListener);
  }
}
