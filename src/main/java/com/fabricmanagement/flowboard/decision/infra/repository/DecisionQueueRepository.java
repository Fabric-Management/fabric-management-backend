package com.fabricmanagement.flowboard.decision.infra.repository;

import static com.fabricmanagement.flowboard.decision.infra.repository.DecisionFollowRepository.EFFECTIVE_FOLLOW_PREDICATE;

import com.fabricmanagement.flowboard.decision.domain.DecisionQueueBucket;
import com.fabricmanagement.sales.salesorder.domain.port.SalesOrderReadScopePort.OrderReadScope;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** One-row-per-case SQL; all visibility predicates run before count and pagination. */
@Repository
public class DecisionQueueRepository {
  private static final String FROM =
      """
      FROM (SELECT tenant_id, case_id AS id, subject_id, subject_number, order_created_by,
                   task_id, case_state, verdict_code, projected_at, is_active
              FROM flowboard.decision_subject_projection) c
      JOIN flowboard.task t ON t.tenant_id=c.tenant_id AND t.id=c.task_id
      WHERE c.tenant_id=:tenant AND c.is_active AND t.is_active
        AND t.closed_at IS NULL AND t.status NOT IN ('DONE','CANCELLED')
        AND c.case_state IN ('OPEN','PARTIALLY_SETTLED')
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final Clock clock;
  private final Duration staleAfter;

  public DecisionQueueRepository(
      @Qualifier("dataSource") DataSource dataSource,
      Clock clock,
      @Value("${flowboard.decision-queue.stale-after:30s}") Duration staleAfter) {
    this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    this.clock = clock;
    this.staleAfter = staleAfter;
  }

  public PageSlice page(
      UUID tenant,
      UUID caller,
      Set<UUID> callerDepartments,
      OrderReadScope readScope,
      DecisionQueueBucket bucket,
      int page,
      int size) {
    MapSqlParameterSource parameters = parameters(tenant, caller, callerDepartments, readScope);
    String where = readScope(readScope) + bucket(bucket, callerDepartments);
    long total =
        Objects.requireNonNull(
            jdbc.queryForObject("SELECT count(*) " + FROM + where, parameters, Long.class));
    parameters.addValue("limit", size).addValue("offset", (long) page * size);
    List<Row> rows =
        jdbc.query(
            """
            SELECT c.id, c.subject_id, c.subject_number, c.projected_at,
                   c.verdict_code, t.id AS task_id, t.version AS task_version,
                   t.priority, t.deadline, t.created_at,
                   (SELECT string_agg(a.user_id::text, ',' ORDER BY a.user_id)
                      FROM flowboard.task_assignee a
                     WHERE a.tenant_id=t.tenant_id AND a.task_id=t.id
                       AND a.is_active AND a.user_id IS NOT NULL) direct_ids,
                   (SELECT string_agg(a.department_id::text, ',' ORDER BY a.department_id)
                      FROM flowboard.task_assignee a
                     WHERE a.tenant_id=t.tenant_id AND a.task_id=t.id
                       AND a.is_active AND a.department_id IS NOT NULL) department_ids
            """
                + FROM
                + where
                + " ORDER BY CASE t.priority WHEN 'CRITICAL' THEN 4 WHEN 'HIGH' THEN 3 "
                + "WHEN 'MEDIUM' THEN 2 ELSE 1 END DESC, t.deadline ASC NULLS LAST, "
                + "t.created_at ASC, c.id ASC LIMIT :limit OFFSET :offset",
            parameters,
            this::row);
    return new PageSlice(rows, total);
  }

  public long count(
      UUID tenant,
      UUID caller,
      Set<UUID> callerDepartments,
      OrderReadScope readScope,
      DecisionQueueBucket bucket) {
    return Objects.requireNonNull(
        jdbc.queryForObject(
            "SELECT count(*) " + FROM + readScope(readScope) + bucket(bucket, callerDepartments),
            parameters(tenant, caller, callerDepartments, readScope),
            Long.class));
  }

  public boolean hasOverduePublication(UUID tenant, Set<String> relevantListeners) {
    List<String> listeners = relevantListeners.stream().sorted().toList();
    MapSqlParameterSource parameters =
        new MapSqlParameterSource()
            .addValue("tenant", tenant)
            .addValue("staleBefore", java.sql.Timestamp.from(clock.instant().minus(staleAfter)));
    String listenerClause =
        java.util.stream.IntStream.range(0, listeners.size())
            .mapToObj(
                index -> {
                  parameters.addValue("listener" + index, listeners.get(index) + ".%");
                  return "listener_id LIKE :listener" + index;
                })
            .collect(java.util.stream.Collectors.joining(" OR "));
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1 FROM public.event_publication
              WHERE completion_date IS NULL
                AND publication_date < :staleBefore
                AND serialized_event::jsonb ->> 'tenantId' = cast(:tenant as text)
                AND (
            """
                + listenerClause
                + "))",
            parameters,
            Boolean.class));
  }

  private MapSqlParameterSource parameters(
      UUID tenant, UUID caller, Set<UUID> departments, OrderReadScope scope) {
    return new MapSqlParameterSource()
        .addValue("tenant", tenant)
        .addValue("caller", caller)
        .addValue("departments", departments.isEmpty() ? Set.of(new UUID(0, 0)) : departments)
        .addValue(
            "readPrincipals",
            scope.principalIds().isEmpty() ? Set.of(new UUID(0, 0)) : scope.principalIds());
  }

  private String readScope(OrderReadScope scope) {
    return switch (scope.kind()) {
      case NONE -> " AND false";
      case ALL -> "";
      case PRINCIPALS -> " AND c.order_created_by IN (:readPrincipals)";
    };
  }

  private String bucket(DecisionQueueBucket bucket, Set<UUID> departments) {
    String mine =
        "EXISTS (SELECT 1 FROM flowboard.task_assignee a WHERE a.tenant_id=t.tenant_id "
            + "AND a.task_id=t.id "
            + "AND a.is_active AND a.user_id=:caller)";
    String department =
        departments.isEmpty()
            ? "false"
            : "EXISTS (SELECT 1 FROM flowboard.task_assignee a WHERE a.tenant_id=t.tenant_id "
                + "AND a.task_id=t.id "
                + "AND a.is_active AND a.department_id IN (:departments))";
    String any =
        "EXISTS (SELECT 1 FROM flowboard.task_assignee a WHERE a.tenant_id=t.tenant_id "
            + "AND a.task_id=t.id AND a.is_active)";
    String anotherDirectHolder =
        "EXISTS (SELECT 1 FROM flowboard.task_assignee a WHERE a.tenant_id=t.tenant_id "
            + "AND a.task_id=t.id AND a.is_active AND a.user_id IS NOT NULL "
            + "AND a.user_id<>:caller)";
    return switch (bucket) {
      case MINE -> " AND " + mine;
      case DEPARTMENT -> " AND NOT (" + mine + ") AND " + department;
      case UNASSIGNED -> " AND NOT (" + any + ")";
      case WAITING ->
          " AND NOT ("
              + mine
              + ") AND NOT ("
              + department
              + ") AND "
              + anotherDirectHolder
              + " AND ("
              + EFFECTIVE_FOLLOW_PREDICATE
              + ")";
    };
  }

  private Row row(ResultSet rs, int ignored) throws SQLException {
    return new Row(
        rs.getObject("id", UUID.class),
        rs.getObject("subject_id", UUID.class),
        rs.getString("subject_number"),
        rs.getObject("task_id", UUID.class),
        rs.getLong("task_version"),
        rs.getString("priority"),
        rs.getObject("deadline", LocalDate.class),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("projected_at").toInstant(),
        rs.getString("verdict_code"),
        ids(rs.getString("direct_ids")),
        ids(rs.getString("department_ids")));
  }

  private static List<UUID> ids(String csv) {
    if (csv == null || csv.isBlank()) return List.of();
    return Arrays.stream(csv.split(",")).map(UUID::fromString).toList();
  }

  public record PageSlice(List<Row> rows, long total) {}

  public record Row(
      UUID caseId,
      UUID orderId,
      String orderNumber,
      UUID taskId,
      long taskVersion,
      String priority,
      LocalDate dueDate,
      Instant createdAt,
      Instant projectedAt,
      String verdictCode,
      List<UUID> directAssigneeIds,
      List<UUID> departmentIds) {}
}
