package com.fabricmanagement.flowboard.routing.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.flowboard.routing.domain.RoutingPoolKey;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Guards the real two-datasource wiring before exercising the routing race protocol. */
class RoutingTransactionIT extends RoutingIntegrationSupport {
  @PersistenceContext private EntityManager entityManager;

  @Test
  void primaryJdbcAndJpaSharePostgresSessionTransactionAndTenant() {
    tx(
        () -> {
          int jpaPid =
              ((Number)
                      entityManager.createNativeQuery("SELECT pg_backend_pid()").getSingleResult())
                  .intValue();
          long jpaTransaction =
              ((Number) entityManager.createNativeQuery("SELECT txid_current()").getSingleResult())
                  .longValue();

          assertThat(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class))
              .as("JPA and routing JDBC must use the same PostgreSQL connection")
              .isEqualTo(jpaPid);
          assertThat(jdbc.queryForObject("SELECT txid_current()", Long.class))
              .as("JDBC must join the JPA transaction, not an autocommit system connection")
              .isEqualTo(jpaTransaction);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT current_setting('app.current_tenant', true)", String.class))
              .as("Routing JDBC inherits the Hibernate tenant binding")
              .isEqualTo(tenant.toString());
          return null;
        });
  }

  @Test
  void poolHeaderAndMembershipAreVisibleTogetherAndRollbackTogether() {
    var member = user("Member", "flowboard:write", "sales:write");
    assertThatThrownBy(
            () ->
                tx(
                    () -> {
                      var pool =
                          configuration.configure(
                              tenant, RoutingPoolKey.ORDER_COVER, null, Set.of(member.getId()));
                      assertThat(repository.pool(tenant, RoutingPoolKey.ORDER_COVER))
                          .contains(pool);
                      assertThat(repository.members(tenant, pool.id()))
                          .singleElement()
                          .satisfies(row -> assertThat(row.userId()).isEqualTo(member.getId()));
                      throw new IllegalStateException("forced pool rollback");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("forced pool rollback");

    assertThat(count("flowboard.routing_pool")).isZero();
    assertThat(count("flowboard.routing_pool_member")).isZero();
  }

  @Test
  void newTaskAndRoutingStateAreVisibleTogetherAndRollbackTogether() {
    var creator = user("Creator");
    assertThatThrownBy(
            () ->
                tx(
                    () -> {
                      var task = task(creator.getId());
                      evaluate(task);
                      assertThat(repository.openFailures(tenant, task.getId())).hasSize(1);
                      assertThat(count("flowboard.routing_task_state")).isEqualTo(1);
                      throw new IllegalStateException("forced task rollback");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("forced task rollback");

    assertThat(count("flowboard.task")).isZero();
    assertThat(count("flowboard.routing_task_state")).isZero();
    assertThat(count("flowboard.routing_failure")).isZero();
  }

  @Test
  void realMandatoryNotificationAndDeliveredStatusRollbackTogetherAndCanRetry() {
    user("Manager", "flowboard:manage-routing");
    seedNotificationTemplate();
    var task = task(user("Creator").getId());
    evaluate(task);
    UUID failure = failure(task);
    var pending = tx(() -> alerts.reconcile(tenant, failure)).getFirst();

    assertThatThrownBy(
            () ->
                tx(
                    () -> {
                      alerts.deliverOne(tenant, failure, pending.id(), new int[] {-1});
                      assertThat(repository.alerts(tenant, failure).getFirst().status())
                          .isEqualTo("DELIVERED");
                      assertThat(count("notification.notification_log")).isEqualTo(1);
                      throw new IllegalStateException("forced delivery rollback");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("forced delivery rollback");

    assertThat(count("notification.notification_log")).isZero();
    var rolledBack = repository.alerts(tenant, failure).getFirst();
    assertThat(rolledBack.status()).isEqualTo("PENDING");
    assertThat(rolledBack.attempts()).isZero();
    assertThat(rolledBack.deliveredAt()).isNull();

    // The delivery routine suspends this outer transaction. Its short transactions commit
    // independently and must survive an exception in the caller, as they do in a listener.
    assertThatThrownBy(
            () ->
                tx(
                    () -> {
                      assertThat(alerts.deliver(tenant, failure)).isTrue();
                      throw new IllegalStateException("forced caller rollback");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("forced caller rollback");
    assertThat(count("notification.notification_log")).isEqualTo(1);
    var delivered = repository.alerts(tenant, failure).getFirst();
    assertThat(delivered.status()).isEqualTo("DELIVERED");
    assertThat(delivered.attempts()).isEqualTo(1);
  }
}
