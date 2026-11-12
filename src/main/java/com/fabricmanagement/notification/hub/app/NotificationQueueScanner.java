package com.fabricmanagement.notification.hub.app;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Finds queue rows that are due, across every tenant.
 *
 * <p>The queue worker is a {@code @Scheduled} job: its thread has no tenant, and every hub table
 * enforces row-level security. A JPA query there is bound to the system tenant, matches nothing,
 * and the worker concludes the queue is empty — nothing is delivered and nothing is logged, the
 * same blindness the e-mail outbox had. So the due list is read as {@code fabric_system}
 * (BYPASSRLS), and only identities leave this class: each row is then loaded and updated inside its
 * own tenant context, where RLS applies normally.
 */
@Component
@RequiredArgsConstructor
public class NotificationQueueScanner {

  /** Identity of a queue row; enough to re-enter its tenant and load it there. */
  public record QueueRef(UUID id, UUID tenantId, String eventType) {}

  static final String PENDING_SQL =
      """
      SELECT id, tenant_id, event_type
      FROM notification.notification_queue
      WHERE status = 'PENDING'
        AND retry_count < 3
        AND is_active = true
        AND deleted_at IS NULL
      ORDER BY created_at ASC
      LIMIT ?
      """;

  static final String STUCK_SQL =
      """
      SELECT id, tenant_id, event_type
      FROM notification.notification_queue
      WHERE status = 'PROCESSING'
        AND updated_at < ?
        AND is_active = true
        AND deleted_at IS NULL
      """;

  private final SystemTransactionExecutor systemTransactionExecutor;

  /** Oldest pending rows first, at most {@code limit}. */
  public List<QueueRef> pending(int limit) {
    return systemTransactionExecutor.executeQuery(PENDING_SQL, this::map, limit);
  }

  /** Rows that have been PROCESSING since before {@code stuckBefore}. */
  public List<QueueRef> stuck(Instant stuckBefore) {
    return systemTransactionExecutor.executeQuery(
        STUCK_SQL, this::map, Timestamp.from(stuckBefore));
  }

  private QueueRef map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
    return new QueueRef(
        rs.getObject("id", UUID.class),
        rs.getObject("tenant_id", UUID.class),
        rs.getString("event_type"));
  }
}
