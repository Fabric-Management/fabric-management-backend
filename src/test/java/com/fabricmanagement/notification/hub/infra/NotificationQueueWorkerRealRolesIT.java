package com.fabricmanagement.notification.hub.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.notification.hub.app.NotificationQueueProcessor;
import com.fabricmanagement.testsupport.AbstractFabricAppIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * NOTIF-UNIFY-1 G12 on the production roles: the queue worker runs on a scheduler thread with no
 * tenant, and every hub table enforces row-level security. A repository query for "pending rows"
 * would see nothing there, so the worker has to find the rows as the system role and then deliver
 * each one inside its own tenant.
 *
 * <p>The superuser base cannot tell the two apart; this one connects as the real runtime roles, so
 * it is the automated stand-in for watching the queue drain in the running application.
 *
 * <p>The container is shared with the other tests on this base, which count rows that belong to no
 * tenant (for example {@code OnboardingAtomicityIT}), so the test removes everything it wrote.
 */
@DisplayName("Notification queue worker on the runtime roles")
class NotificationQueueWorkerRealRolesIT extends AbstractFabricAppIntegrationTest {

  @Autowired private NotificationQueueProcessor processor;

  /** Cross-tenant access (BYPASSRLS) for set-up and assertions only. */
  @Autowired
  @Qualifier("systemJdbcTemplate")
  private JdbcTemplate systemJdbc;

  private final UUID tenant = UUID.randomUUID();

  @AfterEach
  void removeWhatTheTestWrote() {
    for (String table :
        new String[] {"notification_log", "notification_queue", "notification_template"}) {
      systemJdbc.update("DELETE FROM notification." + table + " WHERE tenant_id = ?", tenant);
    }
  }

  @Test
  @DisplayName("delivers a queued in-app notification: row SENT, one log row for the recipient")
  void deliversAQueuedRow() {
    UUID recipient = UUID.randomUUID();
    UUID queueId = UUID.randomUUID();
    systemJdbc.update(
        """
        INSERT INTO notification.notification_template
          (tenant_id, event_type, channel, title_key, body_key, importance)
        VALUES (?, 'APPROVAL_PENDING', 'IN_APP',
                'notification.approval_pending.title', 'notification.approval_pending.body', 'HIGH')
        """,
        tenant);
    systemJdbc.update(
        """
        INSERT INTO notification.notification_queue
          (id, tenant_id, recipient_id, event_type, channel, importance)
        VALUES (?, ?, ?, 'APPROVAL_PENDING', 'IN_APP', 'HIGH')
        """,
        queueId,
        tenant,
        recipient);

    processor.processQueue();

    assertThat(
            systemJdbc.queryForMap(
                "SELECT status, last_error FROM notification.notification_queue WHERE id = ?",
                queueId))
        .as("queue row after one worker pass")
        .containsEntry("status", "SENT");
    assertThat(
            systemJdbc.queryForObject(
                "SELECT count(*) FROM notification.notification_log"
                    + " WHERE tenant_id = ? AND recipient_id = ? AND channel = 'IN_APP'",
                Long.class,
                tenant,
                recipient))
        .isEqualTo(1L);
  }
}
