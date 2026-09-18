package com.fabricmanagement.flowboard.routing.app;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.platform.tenant.app.TenantTransactionalPurgeService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RoutingPurgeIT extends RoutingIntegrationSupport {
  @Autowired private TenantTransactionalPurgeService purge;

  @Test
  void systemPurgeRemovesAllSixTablesAndDeliveryKeyNotifications() {
    jdbc.update("UPDATE common_tenant.common_tenant SET demo_mode = true WHERE id = ?", tenant);
    var member = user("Member", "flowboard:write", "sales:write");
    user("Manager", "flowboard:manage-routing");
    seedNotificationTemplate();
    var task = task(member.getId());
    evaluate(task);
    assertThat(alerts.deliver(tenant, failure(task))).isTrue();
    configure(member.getId());
    evaluate(task);
    for (String table : tables()) assertThat(count(table)).as(table).isGreaterThan(0);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM notification.notification_log WHERE tenant_id = ? AND delivery_key IS NOT NULL",
                Integer.class,
                tenant))
        .isEqualTo(1);
    purge.purgeDemoData(tenant);
    for (String table : tables()) assertThat(count(table)).as(table).isZero();
    assertThat(count("notification.notification_log")).isZero();
  }

  private List<String> tables() {
    return List.of(
        "flowboard.routing_pool",
        "flowboard.routing_pool_member",
        "flowboard.routing_task_state",
        "flowboard.routing_failure",
        "flowboard.routing_failure_resolution",
        "flowboard.routing_failure_alert");
  }
}
