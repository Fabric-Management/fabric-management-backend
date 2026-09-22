package com.fabricmanagement.flowboard.decision.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.flowboard.decision.app.*;
import com.fabricmanagement.flowboard.decision.domain.DecisionFollowSource;
import com.fabricmanagement.flowboard.decision.domain.DecisionQueueBucket;
import com.fabricmanagement.flowboard.decision.infra.repository.DecisionFollowRepository;
import com.fabricmanagement.flowboard.routing.domain.RoutingPoolKey;
import com.fabricmanagement.sales.salesorder.app.OrderCoverQueryService;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverDetail.DecisionBlockedReasonCode;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverDetail.DecisionCapability;
import com.fabricmanagement.sales.salesorder.infra.OrderCoverIntegrationSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DecisionQueueCapabilityIT extends OrderCoverIntegrationSupport {
  @Autowired private DecisionQueueService queue;
  @Autowired private DecisionProjectionRebuildService rebuild;
  @Autowired private OrderCoverQueryService detail;
  @Autowired private DecisionFollowRepository follows;

  @Test
  void queueAndDetailReturnTheSameCapabilityDecision() {
    Cover cover = governed(1);
    rebuild.rebuild(tenant, java.util.List.of(cover.caseId()));
    assertParity(cover, actor.getId(), DecisionQueueBucket.MINE);
  }

  @Test
  void queueAndDetailBothDenyAnOrderOutsideFreshWriteScope() {
    Cover cover = governed(1);
    var foreignCreator = user("Foreign creator", "sales:read");
    jdbc.update(
        "update sales_ord.sales_order set created_by=? where id=?",
        foreignCreator.getId(),
        cover.orderId());
    jdbc.update(
        "update common_user.permission_template set data_scope='OWN' "
            + "where role_code=? and resource='sales' and action='write'",
        actor.getRole().getRoleCode());
    rebuild.rebuild(tenant, java.util.List.of(cover.caseId()));

    DecisionCapability action = assertParity(cover, actor.getId(), DecisionQueueBucket.MINE);

    assertThat(action.allowed()).isFalse();
    assertThat(action.reason().code()).isEqualTo(DecisionBlockedReasonCode.PERMISSION_DENIED);
  }

  @Test
  void queueAndDetailBothReportRemovalFromTheRoutingPoolBeforeScope() {
    Cover cover = governed(1);
    rebuild.rebuild(tenant, java.util.List.of(cover.caseId()));
    configurePool();

    DecisionCapability action = assertParity(cover, actor.getId(), DecisionQueueBucket.MINE);

    assertThat(action.allowed()).isFalse();
    assertThat(action.reason().code()).isEqualTo(DecisionBlockedReasonCode.OUTSIDE_ROUTING_POOL);
  }

  @Test
  void listableFixtureMatrixMatchesDetailAcrossEveryGateCombination() {
    Cover unknown = governed(1);
    Cover actionable = governed(1);
    refresh(actionable);
    rebuild.rebuild(tenant, java.util.List.of(unknown.caseId(), actionable.caseId()));
    var caller =
        user(
            "Capability matrix caller",
            "sales:read",
            "sales:write",
            "flowboard:read",
            "flowboard:write",
            "flowboard:manage-routing");
    UUID poolId = routing.pool(tenant, RoutingPoolKey.ORDER_COVER).orElseThrow().id();
    jdbc.update(
        """
        insert into flowboard.routing_pool_member
          (id,tenant_id,uid,pool_id,user_id,active,is_active,created_at,updated_at,version)
        values (gen_random_uuid(),?,gen_random_uuid()::text,?,?,true,true,now(),now(),0)
        """,
        tenant,
        poolId,
        caller.getId());
    for (Cover cover : java.util.List.of(unknown, actionable)) {
      follows.record(tenant, cover.caseId(), caller.getId(), DecisionFollowSource.OPENED, null);
      jdbc.update(
          """
          insert into flowboard.task_assignee
            (id,tenant_id,uid,task_id,user_id,assigned_by,assigned_at,is_active,
             created_at,updated_at,version)
          values (gen_random_uuid(),?,gen_random_uuid()::text,?,?,'SYSTEM',now(),false,now(),now(),0)
          """,
          tenant,
          cover.taskId(),
          caller.getId());
    }
    authenticate(caller);

    for (Cover cover : java.util.List.of(unknown, actionable)) {
      for (Assignment assignment : Assignment.values()) {
        for (boolean poolMember : java.util.List.of(false, true)) {
          for (boolean writeScope : java.util.List.of(false, true)) {
            jdbc.update(
                "update flowboard.routing_pool_member set active=? where pool_id=? and user_id=?",
                poolMember,
                poolId,
                caller.getId());
            jdbc.update(
                "update common_user.permission_template set data_scope=? "
                    + "where role_code=? and resource='sales' and action='write'",
                writeScope ? "GLOBAL" : "OWN",
                caller.getRole().getRoleCode());
            setAssignment(cover, caller.getId(), assignment);

            assertParity(cover, caller.getId(), assignment.bucket);
          }
        }
      }
    }
  }

  private void setAssignment(Cover cover, java.util.UUID caller, Assignment assignment) {
    jdbc.update(
        "update flowboard.task_assignee set is_active=false where task_id=?", cover.taskId());
    if (assignment == Assignment.DIRECT) {
      jdbc.update(
          "update flowboard.task_assignee set is_active=true where task_id=? and user_id=?",
          cover.taskId(),
          caller);
    } else if (assignment == Assignment.ELSEWHERE) {
      jdbc.update(
          "update flowboard.task_assignee set is_active=true where task_id=? and user_id=?",
          cover.taskId(),
          actor.getId());
    }
  }

  private DecisionCapability assertParity(
      Cover cover, java.util.UUID caller, DecisionQueueBucket bucket) {
    var queueAction =
        queue.list(tenant, caller, bucket, 0, 20).getContent().getFirst().actions().getFirst();
    var detailAction = detail.detail(cover.orderId(), caller).actions().getFirst();
    assertThat(queueAction.action()).isEqualTo(detailAction.action());
    assertThat(queueAction.allowed()).isEqualTo(detailAction.allowed());
    assertThat(queueAction.reason()).isEqualTo(detailAction.reason());
    assertThat(queueAction.routesTo()).isEqualTo(detailAction.routesTo());
    assertThat(queueAction.needsApproval()).isEqualTo(detailAction.needsApproval());
    assertThat(queueAction.requiredPermissions()).isEqualTo(detailAction.requiredPermissions());
    return queueAction;
  }

  private enum Assignment {
    DIRECT(DecisionQueueBucket.MINE),
    ELSEWHERE(DecisionQueueBucket.WAITING),
    UNASSIGNED(DecisionQueueBucket.UNASSIGNED);

    private final DecisionQueueBucket bucket;

    Assignment(DecisionQueueBucket bucket) {
      this.bucket = bucket;
    }
  }
}
