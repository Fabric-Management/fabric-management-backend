package com.fabricmanagement.flowboard.decision.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fabricmanagement.flowboard.decision.domain.DecisionFollowSource;
import com.fabricmanagement.flowboard.decision.infra.repository.DecisionFollowRepository;
import com.fabricmanagement.flowboard.task.domain.event.TaskAssignedEvent;
import com.fabricmanagement.platform.tenant.app.TenantTransactionalPurgeService;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.sales.salesorder.domain.event.OrderCoverCaseOpenedEvent;
import com.fabricmanagement.sales.salesorder.infra.OrderCoverIntegrationSupport;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DecisionFollowRuleIT extends OrderCoverIntegrationSupport {
  @Autowired private DecisionFollowRepository follows;
  @Autowired private TenantTransactionalPurgeService purge;

  @Test
  void oneReasonWithoutSuppressionIsEffective() {
    var fixture = followFixture();
    record(fixture, DecisionFollowSource.OPENED);
    assertThat(effective(fixture)).isTrue();
  }

  @Test
  void threeReasonsStillProduceOneEffectiveFollower() {
    var fixture = followFixture();
    record(fixture, DecisionFollowSource.OPENED);
    record(fixture, DecisionFollowSource.ASSIGNED);
    record(fixture, DecisionFollowSource.SETTLED);
    assertThat(effective(fixture)).isTrue();
    assertThat(reasonCount(fixture)).isEqualTo(3);
    assertThat(effectivePairCount(fixture)).isEqualTo(1);
  }

  @Test
  void suppressionMakesEveryExistingReasonIneffective() {
    var fixture = followFixture();
    record(fixture, DecisionFollowSource.OPENED);
    record(fixture, DecisionFollowSource.ASSIGNED);
    suppress(fixture);
    assertThat(effective(fixture)).isFalse();
  }

  @Test
  void newAutomaticReasonDoesNotRemoveSuppression() {
    var fixture = followFixture();
    record(fixture, DecisionFollowSource.OPENED);
    suppress(fixture);
    record(fixture, DecisionFollowSource.SETTLED);
    assertThat(reasonCount(fixture)).isEqualTo(2);
    assertThat(effective(fixture)).isFalse();
  }

  @Test
  void removingSuppressionReactivatesExistingReasons() {
    var fixture = followFixture();
    record(fixture, DecisionFollowSource.OPENED);
    suppress(fixture);
    jdbc.update(
        "delete from flowboard.decision_follow_suppression where tenant_id=? and case_id=? and user_id=?",
        tenant,
        fixture.cover().caseId(),
        fixture.user().getId());
    assertThat(effective(fixture)).isTrue();
  }

  @Test
  void closingTheCaseDoesNotRewriteFollowFacts() {
    var fixture = followFixture();
    record(fixture, DecisionFollowSource.OPENED);
    suppress(fixture);
    jdbc.update(
        "update sales_ord.order_cover_case set state='CANCELLED',closed_at=now() where id=?",
        fixture.cover().caseId());
    assertThat(reasonCount(fixture)).isOne();
    assertThat(suppressionCount(fixture)).isOne();
    assertThat(effective(fixture)).isFalse();
  }

  @Test
  void subjectAccessChangesDoNotAlterTheEffectiveFollowFact() {
    var fixture = followFixture();
    record(fixture, DecisionFollowSource.OPENED);
    jdbc.update(
        "delete from common_user.permission_template where role_code=? and resource='sales' and action='read'",
        fixture.user().getRole().getRoleCode());
    assertThat(reasonCount(fixture)).isOne();
    assertThat(effective(fixture)).isTrue();
  }

  @Test
  void tenantPurgeRemovesReasonsAndSuppressionsThroughTheProductionService() {
    var fixture = followFixture();
    record(fixture, DecisionFollowSource.OPENED);
    suppress(fixture);
    awaitFollowDeliveries(fixture.cover());
    jdbc.update("update common_tenant.common_tenant set demo_mode=true where id=?", tenant);

    purge.purgeDemoData(tenant);

    assertThat(count("flowboard.decision_follow")).isZero();
    assertThat(count("flowboard.decision_follow_suppression")).isZero();
  }

  private void awaitFollowDeliveries(Cover cover) {
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              Integer total =
                  jdbc.queryForObject(
                      """
                      select count(*) from event_publication
                      where listener_id like ? and (
                        (event_type=? and serialized_event like ?)
                        or (event_type=? and serialized_event like ?))
                      """,
                      Integer.class,
                      "%DecisionFollowEventListener%",
                      OrderCoverCaseOpenedEvent.class.getName(),
                      "%" + cover.caseId() + "%",
                      TaskAssignedEvent.class.getName(),
                      "%" + cover.taskId() + "%");
              Integer incomplete =
                  jdbc.queryForObject(
                      """
                      select count(*) from event_publication
                      where listener_id like ? and (
                        (event_type=? and serialized_event like ?)
                        or (event_type=? and serialized_event like ?))
                        and completion_date is null
                      """,
                      Integer.class,
                      "%DecisionFollowEventListener%",
                      OrderCoverCaseOpenedEvent.class.getName(),
                      "%" + cover.caseId() + "%",
                      TaskAssignedEvent.class.getName(),
                      "%" + cover.taskId() + "%");
              assertThat(total).isGreaterThanOrEqualTo(2);
              assertThat(incomplete).isZero();
            });
  }

  private FollowFixture followFixture() {
    Cover cover = governed(1);
    User follower = user("Follower", "sales:read", "flowboard:read");
    return new FollowFixture(cover, follower);
  }

  private void record(FollowFixture fixture, DecisionFollowSource source) {
    assertThat(
            follows.record(
                tenant,
                fixture.cover().caseId(),
                fixture.user().getId(),
                source,
                source == DecisionFollowSource.OPENED ? null : UUID.randomUUID()))
        .isTrue();
  }

  private void suppress(FollowFixture fixture) {
    jdbc.update(
        """
        insert into flowboard.decision_follow_suppression
          (id,tenant_id,uid,created_at,updated_at,is_active,version,case_id,user_id,suppressed_at)
        values (gen_random_uuid(),?,gen_random_uuid()::text,now(),now(),true,0,?,?,now())
        """,
        tenant,
        fixture.cover().caseId(),
        fixture.user().getId());
  }

  private boolean effective(FollowFixture fixture) {
    return follows.isEffective(tenant, fixture.cover().caseId(), fixture.user().getId());
  }

  private int reasonCount(FollowFixture fixture) {
    return jdbc.queryForObject(
        "select count(*) from flowboard.decision_follow where tenant_id=? and case_id=? and user_id=?",
        Integer.class,
        tenant,
        fixture.cover().caseId(),
        fixture.user().getId());
  }

  private int suppressionCount(FollowFixture fixture) {
    return jdbc.queryForObject(
        "select count(*) from flowboard.decision_follow_suppression where tenant_id=? and case_id=? and user_id=?",
        Integer.class,
        tenant,
        fixture.cover().caseId(),
        fixture.user().getId());
  }

  private int effectivePairCount(FollowFixture fixture) {
    return jdbc.queryForObject(
        """
        select count(*) from (
          select distinct case_id,user_id from flowboard.decision_follow f
          where f.tenant_id=? and f.case_id=? and f.user_id=?
            and not exists (
              select 1 from flowboard.decision_follow_suppression s
              where s.tenant_id=f.tenant_id and s.case_id=f.case_id and s.user_id=f.user_id)
        ) effective
        """,
        Integer.class,
        tenant,
        fixture.cover().caseId(),
        fixture.user().getId());
  }

  private record FollowFixture(Cover cover, User user) {}
}
