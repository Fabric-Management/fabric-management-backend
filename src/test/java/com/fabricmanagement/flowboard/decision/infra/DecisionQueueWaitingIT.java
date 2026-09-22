package com.fabricmanagement.flowboard.decision.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.flowboard.decision.app.*;
import com.fabricmanagement.flowboard.decision.domain.*;
import com.fabricmanagement.flowboard.decision.infra.repository.DecisionFollowRepository;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.sales.salesorder.infra.OrderCoverIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DecisionQueueWaitingIT extends OrderCoverIntegrationSupport {
  @Autowired private DecisionQueueService queue;
  @Autowired private DecisionProjectionRebuildService rebuild;
  @Autowired private DecisionFollowRepository follows;
  @Autowired private DepartmentRepository departments;

  @Test
  void effectiveFollowShowsAnElsewhereAssignedCaseOnceAndSuppressionHidesIt() {
    Cover cover = governed(1);
    User follower = user("Follower", "sales:read", "flowboard:read");
    follows.record(tenant, cover.caseId(), follower.getId(), DecisionFollowSource.OPENED, null);
    rebuild.rebuild(tenant, java.util.List.of(cover.caseId()));
    authenticate(follower);

    assertThat(
            queue.list(tenant, follower.getId(), DecisionQueueBucket.WAITING, 0, 20).getContent())
        .extracting(item -> item.id())
        .containsExactly(cover.caseId());

    jdbc.update(
        """
        insert into flowboard.decision_follow_suppression
          (id,tenant_id,uid,created_at,updated_at,is_active,version,case_id,user_id,suppressed_at)
        values (gen_random_uuid(),?,gen_random_uuid()::text,now(),now(),true,0,?,?,now())
        """,
        tenant,
        cover.caseId(),
        follower.getId());
    assertThat(
            queue.list(tenant, follower.getId(), DecisionQueueBucket.WAITING, 0, 20).getContent())
        .isEmpty();
  }

  @Test
  void ownerlessDraftProductionPlaceholderIsNotAWaitingDecision() {
    Cover cover = governed(1);
    User follower = user("Follower", "sales:read", "flowboard:read");
    follows.record(tenant, cover.caseId(), follower.getId(), DecisionFollowSource.OPENED, null);
    jdbc.update(
        "update flowboard.task_assignee set is_active=false where task_id=?", cover.taskId());
    rebuild.rebuild(tenant, java.util.List.of(cover.caseId()));
    authenticate(follower);
    assertThat(
            queue.list(tenant, follower.getId(), DecisionQueueBucket.WAITING, 0, 20).getContent())
        .isEmpty();
  }

  @Test
  void departmentOnlyHolderIsNotAnotherUserAndThereforeNotWaiting() {
    Cover cover = governed(1);
    User follower = user("Department-only follower", "sales:read", "flowboard:read");
    Department department =
        departments.saveAndFlush(
            Department.create(
                organization.getId(),
                "Foreign decision team",
                "DQ-WAIT-" + suffix,
                "Department-only waiting test"));
    follows.record(tenant, cover.caseId(), follower.getId(), DecisionFollowSource.OPENED, null);
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
    authenticate(follower);

    assertThat(
            queue.list(tenant, follower.getId(), DecisionQueueBucket.WAITING, 0, 20).getContent())
        .isEmpty();
    assertThat(queue.summary(tenant, follower.getId()).waitingCount()).isZero();
  }

  @Test
  void salesReadScopeIsAppliedBeforeWaitingCountAndPage() {
    Cover cover = governed(1);
    User ownOnly = user("Own reader", "sales:read", "flowboard:read");
    jdbc.update(
        "update common_user.permission_template set data_scope='OWN' "
            + "where role_code=? and resource='sales' and action='read'",
        ownOnly.getRole().getRoleCode());
    follows.record(tenant, cover.caseId(), ownOnly.getId(), DecisionFollowSource.OPENED, null);
    rebuild.rebuild(tenant, java.util.List.of(cover.caseId()));
    authenticate(ownOnly);
    assertThat(queue.list(tenant, ownOnly.getId(), DecisionQueueBucket.WAITING, 0, 20).getContent())
        .isEmpty();
    assertThat(queue.summary(tenant, ownOnly.getId()).waitingCount()).isZero();
  }
}
