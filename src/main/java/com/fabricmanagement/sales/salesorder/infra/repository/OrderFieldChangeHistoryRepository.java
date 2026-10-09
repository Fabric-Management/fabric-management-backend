package com.fabricmanagement.sales.salesorder.infra.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bounded, stable pages of an order's field history (CEDIT-09 §3.2): newest order version first,
 * then id, both descending, read by a seek on the last {@code (orderVersion, id)} pair and never by
 * an offset. Index: {@code idx_order_field_change_order_version}.
 *
 * <p>The stored values are read as their JSON text ({@code jsonb::text}), not through the entity's
 * {@code JsonNode} mapping: the shared mapper reads decimals as doubles, and a history value must
 * keep every digit (CEDIT-09 §3.3; backend canon §5, field history read). The query is tenant-bound
 * explicitly and runs in the caller's transaction, so RLS applies as well.
 */
@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class OrderFieldChangeHistoryRepository {

  private static final String COLUMNS =
      """
      SELECT id, operation_id, order_version, changed_at, actor_id, line_id, edit_key,
             change_kind, old_value::text AS old_value, new_value::text AS new_value,
             resolution, resolution_scope
      FROM sales_ord.order_field_change
      WHERE tenant_id = :tenantId
        AND sales_order_id = :orderId
      """;

  // Rows are append-only and never deactivated, so no soft-delete filter applies.
  private static final String NEWEST =
      COLUMNS
          + """
            AND order_version <= :snapshotVersion
          ORDER BY order_version DESC, id DESC
          LIMIT :rows
          """;

  private static final String OLDER =
      COLUMNS
          + """
            AND order_version <= :lastVersion
            AND (order_version < :lastVersion OR id < :lastId)
          ORDER BY order_version DESC, id DESC
          LIMIT :rows
          """;

  private final NamedParameterJdbcTemplate jdbc;

  /** A stored history row with both values as JSON text (null for SQL NULL). */
  public record Row(
      UUID id,
      UUID operationId,
      long orderVersion,
      Instant changedAt,
      UUID actorId,
      UUID lineId,
      String editKey,
      String changeKind,
      String oldValue,
      String newValue,
      String resolution,
      String resolutionScope) {}

  /** The first page of a chain: the newest rows up to the chain's snapshot version. */
  public List<Row> newest(UUID tenantId, UUID orderId, long snapshotVersion, int rows) {
    return jdbc.query(
        NEWEST,
        new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("orderId", orderId)
            .addValue("snapshotVersion", snapshotVersion)
            .addValue("rows", rows),
        OrderFieldChangeHistoryRepository::row);
  }

  /**
   * The rows strictly older than {@code (lastVersion, lastId)}. The last pair of a page is never
   * above the chain's snapshot version, so nothing newer than the snapshot can follow it.
   */
  public List<Row> olderThan(UUID tenantId, UUID orderId, long lastVersion, UUID lastId, int rows) {
    return jdbc.query(
        OLDER,
        new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("orderId", orderId)
            .addValue("lastVersion", lastVersion)
            .addValue("lastId", lastId)
            .addValue("rows", rows),
        OrderFieldChangeHistoryRepository::row);
  }

  private static Row row(ResultSet rs, int rowNum) throws SQLException {
    return new Row(
        rs.getObject("id", UUID.class),
        rs.getObject("operation_id", UUID.class),
        rs.getLong("order_version"),
        rs.getObject("changed_at", OffsetDateTime.class).toInstant(),
        rs.getObject("actor_id", UUID.class),
        rs.getObject("line_id", UUID.class),
        rs.getString("edit_key"),
        rs.getString("change_kind"),
        rs.getString("old_value"),
        rs.getString("new_value"),
        rs.getString("resolution"),
        rs.getString("resolution_scope"));
  }
}
