package com.fabricmanagement.notification.hub.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.notification.hub.app.NotificationQueueScanner;
import com.fabricmanagement.notification.hub.app.NotificationQueueScanner.QueueRef;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The scanner's SQL against the real schema: which rows count as due, and that rows of different
 * tenants are all found. (This base connects as a superuser, so it guards the statements; that the
 * production role needs the system connection is the reason the scanner exists.)
 */
@DisplayName("NotificationQueueScanner")
class NotificationQueueScannerIT extends AbstractIntegrationTest {

  private static final UUID TENANT_A = UUID.randomUUID();
  private static final UUID TENANT_B = UUID.randomUUID();

  @Autowired private NotificationQueueScanner scanner;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void cleanup() {
    jdbc.update(
        "DELETE FROM notification.notification_queue WHERE tenant_id IN (?, ?)",
        TENANT_A,
        TENANT_B);
  }

  @Test
  @DisplayName("finds pending rows of every tenant, oldest first, and nothing else")
  void findsPendingRowsAcrossTenants() {
    UUID olderA = insert(TENANT_A, "PENDING", 0, 600, 600);
    UUID newerB = insert(TENANT_B, "PENDING", 2, 300, 300);
    insert(TENANT_A, "SENT", 0, 900, 900);
    insert(TENANT_A, "FAILED", 3, 900, 900);
    insert(TENANT_B, "PENDING", 3, 900, 900); // out of retries
    insert(TENANT_B, "PROCESSING", 0, 900, 900);

    List<UUID> found =
        scanner.pending(1000).stream()
            .filter(ref -> ref.tenantId().equals(TENANT_A) || ref.tenantId().equals(TENANT_B))
            .map(QueueRef::id)
            .toList();

    assertThat(found).containsExactly(olderA, newerB);
  }

  @Test
  @DisplayName("carries the tenant and event type, so the row can be processed in its tenant")
  void carriesTenantAndEventType() {
    UUID id = insert(TENANT_A, "PENDING", 0, 60, 60);

    QueueRef ref =
        scanner.pending(1000).stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();

    assertThat(ref.tenantId()).isEqualTo(TENANT_A);
    assertThat(ref.eventType()).isEqualTo("APPROVAL_PENDING");
  }

  @Test
  @DisplayName("honours the limit")
  void honoursTheLimit() {
    insert(TENANT_A, "PENDING", 0, 30, 30);
    insert(TENANT_A, "PENDING", 0, 20, 20);

    assertThat(scanner.pending(1)).hasSize(1);
  }

  @Test
  @DisplayName("finds rows stuck in PROCESSING since before the cut-off only")
  void findsStuckRows() {
    UUID stale = insert(TENANT_A, "PROCESSING", 0, 3_600, 3_600);
    insert(TENANT_B, "PROCESSING", 0, 30, 30); // still within its lease
    insert(TENANT_B, "PENDING", 0, 3_600, 3_600);

    List<UUID> found =
        scanner.stuck(Instant.now().minusSeconds(300)).stream()
            .filter(ref -> ref.tenantId().equals(TENANT_A) || ref.tenantId().equals(TENANT_B))
            .map(QueueRef::id)
            .toList();

    assertThat(found).containsExactly(stale);
  }

  private UUID insert(UUID tenant, String status, int retries, int createdAgo, int updatedAgo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO notification.notification_queue
          (id, tenant_id, recipient_id, event_type, channel, status, retry_count, is_active,
           created_at, updated_at)
        VALUES (?, ?, ?, 'APPROVAL_PENDING', 'IN_APP', ?, ?, true,
                now() - (? * interval '1 second'), now() - (? * interval '1 second'))
        """,
        id,
        tenant,
        UUID.randomUUID(),
        status,
        retries,
        createdAgo,
        updatedAgo);
    return id;
  }
}
