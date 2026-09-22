package com.fabricmanagement.flowboard.routing.infra.repository;

import com.fabricmanagement.flowboard.routing.domain.*;
import com.fabricmanagement.flowboard.routing.domain.RoutingRecords.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Explicit PostgreSQL locking and append-only writes; every statement carries the tenant. */
@Repository
public class RoutingRepository {
  private final JdbcTemplate jdbc;
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

  public RoutingRepository(@Qualifier("dataSource") DataSource dataSource) {
    // An unqualified JdbcTemplate resolves to systemJdbcTemplate in this application.
    // Bind to the same primary DataSource as the JPA transaction and tenant connection provider.
    this.jdbc = new JdbcTemplate(dataSource);
  }

  public void lockPool(UUID tenant, RoutingPoolKey key, boolean exclusive) {
    // MD5's first 64 bits are stable across JVMs and include both components.
    String function = exclusive ? "pg_advisory_xact_lock" : "pg_advisory_xact_lock_shared";
    jdbc.query(
        "SELECT " + function + "(('x' || substr(md5(?), 1, 16))::bit(64)::bigint)",
        (rs, n) -> true,
        tenant + ":" + key.name());
  }

  public void lockState(UUID tenant, UUID task, RoutingPoolKey key) {
    jdbc.update(
        """
        INSERT INTO flowboard.routing_task_state (tenant_id, task_id, pool_key)
        VALUES (?, ?, ?) ON CONFLICT DO NOTHING
        """,
        tenant,
        task,
        key.name());
    jdbc.queryForObject(
        """
        SELECT task_id FROM flowboard.routing_task_state
        WHERE tenant_id = ? AND task_id = ? AND pool_key = ? FOR UPDATE
        """,
        UUID.class,
        tenant,
        task,
        key.name());
  }

  public void shareState(UUID tenant, UUID task) {
    jdbc.queryForObject(
        """
        SELECT task_id FROM flowboard.routing_task_state
        WHERE tenant_id = ? AND task_id = ? FOR SHARE
        """,
        UUID.class,
        tenant,
        task);
  }

  public Optional<Pool> pool(UUID tenant, RoutingPoolKey key) {
    return jdbc
        .query(
            "SELECT id, pool_key, revision FROM flowboard.routing_pool WHERE tenant_id = ? AND pool_key = ?",
            (rs, n) ->
                new Pool(
                    rs.getObject("id", UUID.class),
                    RoutingPoolKey.valueOf(rs.getString("pool_key")),
                    rs.getLong("revision")),
            tenant,
            key.name())
        .stream()
        .findFirst();
  }

  public List<Member> members(UUID tenant, UUID poolId) {
    return jdbc.query(
        """
        SELECT user_id, active FROM flowboard.routing_pool_member
        WHERE tenant_id = ? AND pool_id = ? ORDER BY user_id
        """,
        (rs, n) -> new Member(rs.getObject("user_id", UUID.class), rs.getBoolean("active")),
        tenant,
        poolId);
  }

  public boolean isActiveMember(UUID tenant, RoutingPoolKey key, UUID userId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1 FROM flowboard.routing_pool p
              JOIN flowboard.routing_pool_member m
                ON m.tenant_id=p.tenant_id AND m.pool_id=p.id
              WHERE p.tenant_id=? AND p.pool_key=? AND m.user_id=? AND m.active)
            """,
            Boolean.class,
            tenant,
            key.name(),
            userId));
  }

  public Pool configure(UUID tenant, RoutingPoolKey key, Pool previous, Set<UUID> members) {
    RoutingPool header;
    if (previous == null) {
      header = RoutingPool.create(key);
      header.setTenantId(tenant);
      entityManager.persist(header);
    } else {
      header = entityManager.find(RoutingPool.class, previous.id());
      header.advanceRevision();
    }
    entityManager.flush();
    UUID id = header.getId();
    long revision = header.getRevision();
    jdbc.update(
        "UPDATE flowboard.routing_pool_member SET active = false, updated_at = now(), version = version + 1 WHERE tenant_id = ? AND pool_id = ? AND active",
        tenant,
        id);
    members.stream()
        .sorted()
        .forEach(
            user ->
                jdbc.update(
                    """
        INSERT INTO flowboard.routing_pool_member (tenant_id, pool_id, user_id, active)
        VALUES (?, ?, ?, true)
        ON CONFLICT (tenant_id, pool_id, user_id)
        DO UPDATE SET active = true, updated_at = now(), version = routing_pool_member.version + 1
        """,
                    tenant,
                    id,
                    user));
    return new Pool(id, key, revision);
  }

  public void evaluated(UUID tenant, UUID task, Long revision) {
    jdbc.update(
        """
        UPDATE flowboard.routing_task_state SET evaluated_pool_revision = ?, evaluated_at = now()
        WHERE tenant_id = ? AND task_id = ?
        """,
        revision,
        tenant,
        task);
  }

  private static final String FAILURES =
      """
      SELECT f.*, r.resolved_at FROM flowboard.routing_failure f
      LEFT JOIN flowboard.routing_failure_resolution r ON r.tenant_id = f.tenant_id AND r.failure_id = f.id
      """;

  public List<Failure> failures(UUID tenant, RoutingPoolKey key, Boolean open) {
    return jdbc.query(
        FAILURES
            + """
        WHERE f.tenant_id = ? AND (?::text IS NULL OR f.pool_key = ?)
          AND (?::boolean IS NULL OR (r.id IS NULL) = ?)
        ORDER BY f.occurred_at DESC, f.id
        """,
        this::failure,
        tenant,
        key == null ? null : key.name(),
        key == null ? null : key.name(),
        open,
        open);
  }

  public org.springframework.data.domain.Page<Failure> failures(
      UUID tenant,
      RoutingPoolKey key,
      Boolean open,
      org.springframework.data.domain.Pageable page) {
    String filter =
        """
        WHERE f.tenant_id = ? AND (?::text IS NULL OR f.pool_key = ?)
          AND (?::boolean IS NULL OR (r.id IS NULL) = ?)
        """;
    String name = key == null ? null : key.name();
    Long total =
        jdbc.queryForObject(
            """
        SELECT count(*) FROM flowboard.routing_failure f
        LEFT JOIN flowboard.routing_failure_resolution r ON r.tenant_id = f.tenant_id AND r.failure_id = f.id
        """
                + filter,
            Long.class,
            tenant,
            name,
            name,
            open,
            open);
    var rows =
        jdbc.query(
            FAILURES + filter + " ORDER BY f.occurred_at DESC, f.id LIMIT ? OFFSET ?",
            this::failure,
            tenant,
            name,
            name,
            open,
            open,
            page.getPageSize(),
            page.getOffset());
    return new org.springframework.data.domain.PageImpl<>(
        rows, page, Objects.requireNonNull(total));
  }

  public List<Failure> openFailures(UUID tenant, UUID task) {
    return jdbc.query(
        FAILURES + " WHERE f.tenant_id = ? AND f.task_id = ? AND r.id IS NULL ORDER BY f.id",
        this::failure,
        tenant,
        task);
  }

  public Failure failure(UUID tenant, UUID id) {
    return jdbc.queryForObject(
        FAILURES + " WHERE f.tenant_id = ? AND f.id = ?", this::failure, tenant, id);
  }

  private Failure failure(ResultSet rs, int n) throws SQLException {
    return new Failure(
        rs.getObject("id", UUID.class),
        rs.getObject("task_id", UUID.class),
        RoutingPoolKey.valueOf(rs.getString("pool_key")),
        RoutingFailureReason.valueOf(rs.getString("reason_code")),
        rs.getObject("user_id", UUID.class),
        rs.getObject("pool_revision", Long.class),
        instant(rs, "occurred_at"),
        instant(rs, "resolved_at"));
  }

  public UUID open(
      UUID tenant,
      UUID task,
      RoutingPoolKey key,
      RoutingFailureReason reason,
      UUID user,
      Long revision) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO flowboard.routing_failure (id, tenant_id, task_id, pool_key, reason_code, user_id, pool_revision)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        tenant,
        task,
        key.name(),
        reason.name(),
        user,
        revision);
    return id;
  }

  public void resolve(UUID tenant, UUID failure) {
    jdbc.update(
        """
        INSERT INTO flowboard.routing_failure_resolution (tenant_id, failure_id, reason)
        VALUES (?, ?, 'CONDITION_CLEARED') ON CONFLICT (tenant_id, failure_id) DO NOTHING
        """,
        tenant,
        failure);
    cancelResolved(tenant, failure);
  }

  public void cancelResolved(UUID tenant, UUID failure) {
    jdbc.update(
        """
        UPDATE flowboard.routing_failure_alert SET status = 'CANCELLED', cancel_reason = 'FAILURE_RESOLVED', updated_at = now()
        WHERE tenant_id = ? AND failure_id = ? AND status IN ('PENDING', 'FAILED')
        """,
        tenant,
        failure);
  }

  public List<UUID> repairTasks(UUID tenant, RoutingPoolKey key, boolean changedOnly) {
    return jdbc.query(
        """
        SELECT t.id FROM flowboard.task t
        LEFT JOIN flowboard.routing_task_state s ON s.tenant_id = t.tenant_id AND s.task_id = t.id
        LEFT JOIN flowboard.routing_pool p ON p.tenant_id = t.tenant_id AND p.pool_key = ?
        WHERE t.tenant_id = ? AND t.task_type = ? AND t.workflow_definition_id IS NOT NULL
          AND t.is_active AND t.closed_at IS NULL AND t.status NOT IN ('DONE','CANCELLED')
          AND (NOT ? OR s.task_id IS NULL OR s.evaluated_pool_revision IS DISTINCT FROM p.revision)
        ORDER BY t.id
        """,
        (rs, n) -> rs.getObject(1, UUID.class),
        key.name(),
        tenant,
        key.name(),
        changedOnly);
  }

  public List<Alert> alerts(UUID tenant, UUID failure) {
    return jdbc.query(
        "SELECT * FROM flowboard.routing_failure_alert WHERE tenant_id = ? AND failure_id = ? ORDER BY recipient_id",
        this::alert,
        tenant,
        failure);
  }

  public Alert lockAlert(UUID tenant, UUID id) {
    return jdbc.queryForObject(
        "SELECT * FROM flowboard.routing_failure_alert WHERE tenant_id = ? AND id = ? FOR UPDATE",
        this::alert,
        tenant,
        id);
  }

  private Alert alert(ResultSet rs, int n) throws SQLException {
    return new Alert(
        rs.getObject("id", UUID.class),
        rs.getObject("recipient_id", UUID.class),
        rs.getString("channel"),
        rs.getString("status"),
        rs.getString("cancel_reason"),
        rs.getInt("attempts"),
        rs.getString("last_error"),
        instant(rs, "delivered_at"));
  }

  public void nominate(UUID tenant, UUID failure, UUID recipient) {
    jdbc.update(
        """
        INSERT INTO flowboard.routing_failure_alert (tenant_id, failure_id, recipient_id)
        VALUES (?, ?, ?) ON CONFLICT (tenant_id, failure_id, recipient_id, channel)
        DO UPDATE SET status = 'PENDING', cancel_reason = NULL, updated_at = now()
        WHERE routing_failure_alert.status = 'CANCELLED'
          AND routing_failure_alert.cancel_reason = 'RECIPIENT_NOT_ELIGIBLE'
        """,
        tenant,
        failure,
        recipient);
  }

  public void cancelRecipient(UUID tenant, UUID alert) {
    jdbc.update(
        """
        UPDATE flowboard.routing_failure_alert SET status = 'CANCELLED', cancel_reason = 'RECIPIENT_NOT_ELIGIBLE', updated_at = now()
        WHERE tenant_id = ? AND id = ? AND status IN ('PENDING','FAILED')
        """,
        tenant,
        alert);
  }

  public void delivered(UUID tenant, UUID alert) {
    jdbc.update(
        """
        UPDATE flowboard.routing_failure_alert SET status = 'DELIVERED', cancel_reason = NULL,
          attempts = attempts + 1, delivered_at = now(), last_error = NULL, updated_at = now()
        WHERE tenant_id = ? AND id = ?
        """,
        tenant,
        alert);
  }

  public int failed(UUID tenant, UUID alert, int token, String error) {
    return jdbc.update(
        """
        UPDATE flowboard.routing_failure_alert SET status = 'FAILED', attempts = attempts + 1,
          last_error = ?, updated_at = now()
        WHERE tenant_id = ? AND id = ? AND status IN ('PENDING','FAILED') AND attempts = ?
        """,
        error,
        tenant,
        alert,
        token);
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    var timestamp = rs.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }
}
