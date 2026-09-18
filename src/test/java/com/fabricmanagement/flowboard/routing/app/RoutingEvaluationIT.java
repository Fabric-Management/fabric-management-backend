package com.fabricmanagement.flowboard.routing.app;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.flowboard.routing.domain.*;
import com.fabricmanagement.platform.user.domain.DataScope;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RoutingEvaluationIT extends RoutingIntegrationSupport {
  @Test
  void noPoolAndEmptyPoolHaveDifferentFailureAndRevision() {
    var task = task(user("Creator").getId());
    evaluate(task);
    var absent = repository.openFailures(tenant, task.getId()).getFirst();
    assertThat(absent.reason()).isEqualTo(RoutingFailureReason.NO_POOL);
    assertThat(absent.poolRevision()).isNull();
    configure();
    evaluate(task);
    var empty = repository.openFailures(tenant, task.getId()).getFirst();
    assertThat(empty.reason()).isEqualTo(RoutingFailureReason.POOL_EMPTY);
    assertThat(empty.poolRevision()).isEqualTo(1);
    assertThat(repository.failure(tenant, absent.id()).resolvedAt()).isNotNull();
    assertThat(assignees(task)).isEmpty();
  }

  @Test
  void allEligibleMembersAreAssignedAndUnchangedEvaluationDoesNotBumpTask() {
    var a = user("A", "flowboard:write", "sales:write");
    var b = user("B", "flowboard:write", "sales:write");
    var task = task(a.getId());
    configure(a.getId(), b.getId());
    long initialVersion = tasks.findById(task.getId()).orElseThrow().getVersion();
    assertThat(evaluate(task).changed()).isTrue();
    assertThat(assignees(task)).containsExactlyInAnyOrder(a.getId(), b.getId());
    long version = tasks.findById(task.getId()).orElseThrow().getVersion();
    assertThat(version).isEqualTo(initialVersion + 1);
    assertThat(evaluate(task).changed()).isFalse();
    assertThat(tasks.findById(task.getId()).orElseThrow().getVersion()).isEqualTo(version);
    assertThat(repository.openFailures(tenant, task.getId())).isEmpty();
    configure(b.getId());
    assertThat(evaluate(task).changed()).isTrue();
    assertThat(assignees(task)).containsExactly(b.getId());
    assertThat(tasks.findById(task.getId()).orElseThrow().getVersion()).isEqualTo(version + 1);
    assertThat(evaluate(task).changed()).isFalse();
    assertThat(tasks.findById(task.getId()).orElseThrow().getVersion()).isEqualTo(version + 1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"inactive", "flowboard:write", "sales:write", "scope"})
  void invalidityDoesNotFallBackAndPartialValidityKeepsOtherMembers(String condition) {
    var invalid = user("Invalid", "flowboard:write", "sales:write");
    var valid = user("Valid", "flowboard:write", "sales:write");
    var task = task(valid.getId());
    configure(invalid.getId(), valid.getId());
    switch (condition) {
      case "inactive" ->
          jdbc.update(
              "UPDATE common_user.common_user SET is_active = false WHERE id = ?", invalid.getId());
      case "scope" ->
          jdbc.update(
              "UPDATE common_user.permission_template SET data_scope = 'OWN' WHERE tenant_id = ? AND role_code = ? AND resource = 'sales'",
              tenant,
              invalid.getRole().getRoleCode());
      default -> permission(invalid, condition, false);
    }
    evaluate(task);
    assertThat(assignees(task)).containsExactly(valid.getId());
    assertThat(repository.openFailures(tenant, task.getId()))
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.reason()).isEqualTo(RoutingFailureReason.RECIPIENT_INVALID);
              assertThat(f.userId()).isEqualTo(invalid.getId());
            });
  }

  @Test
  void routingSeesScopeNarrowedDirectlyInDatabaseDespiteWarmCache() {
    var member = user("Member", "flowboard:write", "sales:write");
    var task = task(user("Creator").getId());
    configure(member.getId());
    assertThat(
            evaluator
                .evaluate(tenant, member.getRole().getRoleCode(), List.of(), member.getId())
                .scopeOf("sales", "write"))
        .isEqualTo(DataScope.GLOBAL);
    jdbc.update(
        "UPDATE common_user.permission_template SET data_scope = 'OWN' WHERE tenant_id = ? AND role_code = ? AND resource = 'sales' AND action = 'write'",
        tenant,
        member.getRole().getRoleCode());
    assertThat(
            evaluator
                .evaluate(tenant, member.getRole().getRoleCode(), List.of(), member.getId())
                .scopeOf("sales", "write"))
        .isEqualTo(DataScope.GLOBAL);
    evaluate(task);
    assertThat(assignees(task)).isEmpty();
    assertThat(repository.openFailures(tenant, task.getId()))
        .extracting(f -> f.reason())
        .containsExactlyInAnyOrder(
            RoutingFailureReason.NO_VALID_RECIPIENT, RoutingFailureReason.RECIPIENT_INVALID);
  }

  @Test
  void inactiveMembershipIsExcludedEvenWhenUserKeepsBothPermissions() {
    var member = user("Member", "flowboard:write", "sales:write");
    var task = task(member.getId());
    configure(member.getId());
    evaluate(task);
    configure();
    evaluate(task);
    assertThat(assignees(task)).isEmpty();
    assertThat(repository.openFailures(tenant, task.getId()))
        .singleElement()
        .satisfies(f -> assertThat(f.reason()).isEqualTo(RoutingFailureReason.POOL_EMPTY));
    assertThat(queries.eligibility(tenant, task.getId()).members())
        .singleElement()
        .satisfies(
            m -> {
              assertThat(m.eligible()).isFalse();
              assertThat(m.reasons()).containsExactly(RoutingReason.NOT_A_MEMBER);
            });
  }
}
