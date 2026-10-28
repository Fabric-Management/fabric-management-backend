package com.fabricmanagement.finance.collection.infra.repository;

import com.fabricmanagement.finance.collection.domain.CollectionReportFacts.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CollectionReportRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public List<Row> rows(MapSqlParameterSource params) {
    return jdbc.query(
        CollectionReportSql.FACTS
            + "SELECT * FROM filtered ORDER BY expected_date NULLS LAST,id LIMIT :limit OFFSET"
            + " :offset",
        params,
        (r, n) ->
            new Row(
                uuid(r, "id"),
                uuid(r, "order_id"),
                uuid(r, "partner_id"),
                null,
                uuid(r, "responsible_user_id"),
                null,
                r.getString("currency"),
                r.getString("method"),
                r.getObject("expected_date", java.time.LocalDate.class),
                r.getObject("cheque_payment_date", java.time.LocalDate.class),
                r.getBigDecimal("planned").toPlainString(),
                money(r, "cash"),
                money(r, "outstanding"),
                money(r, "received"),
                money(r, "missing"),
                money(r, "refund_pending"),
                r.getBoolean("cancelled"),
                r.getBoolean("source_stale"),
                r.getBoolean("source_missing"),
                instant(r, "source_observed_at"),
                Arrays.asList((String[]) r.getArray("buckets").getArray()),
                warnings(r)));
  }

  public List<Currency> totals(MapSqlParameterSource params) {
    return jdbc.query(
        CollectionReportSql.FACTS
            + """
            SELECT currency,count(*) AS rows,count(*) FILTER(WHERE unknown) AS unknown_rows,
              sum(planned) AS planned,
              sum(received) AS received,
              CASE WHEN bool_or(unknown) THEN NULL ELSE sum(cash) END AS cash,
              CASE WHEN bool_or(unknown) THEN NULL ELSE sum(outstanding) END AS outstanding,
              CASE WHEN bool_or(unknown) THEN NULL ELSE sum(missing) END AS missing,
              sum(refund_pending) AS refund_pending
            FROM filtered GROUP BY currency ORDER BY currency
            """,
        params,
        (r, n) ->
            new Currency(
                r.getString("currency"),
                r.getLong("rows"),
                r.getLong("unknown_rows"),
                money(r, "planned"),
                money(r, "cash"),
                money(r, "outstanding"),
                money(r, "received"),
                money(r, "missing"),
                money(r, "refund_pending")));
  }

  public Instant oldestSourceCheck(MapSqlParameterSource params) {
    return jdbc.queryForObject(
        CollectionReportSql.FACTS
            + """
            SELECT CASE WHEN bool_or(p.checked_at IS NULL OR p.checked_at>:cutoff) THEN NULL
              ELSE min(p.checked_at) END AS checked_at
            FROM filtered f LEFT JOIN finance.collection_projection p
              ON p.tenant_id=:tenant AND p.order_id=f.order_id
            """,
        params,
        (r, n) -> instant(r, "checked_at"));
  }

  private static List<String> warnings(ResultSet r) throws SQLException {
    var result = new java.util.ArrayList<String>();
    if (r.getBoolean("unknown")) result.add("VOID_EFFECTIVE_DATE_UNKNOWN");
    if (r.getBoolean("source_stale")) result.add("SOURCE_STALE");
    if (r.getBoolean("source_missing")) result.add("SOURCE_MISSING");
    if (r.getBoolean("awaits_confirmation_date")) result.add("CONFIRMATION_DATE_UNRESOLVED");
    return List.copyOf(result);
  }

  private static UUID uuid(ResultSet r, String column) throws SQLException {
    return r.getObject(column, UUID.class);
  }

  private static String money(ResultSet r, String column) throws SQLException {
    var value = r.getBigDecimal(column);
    return value == null ? null : value.toPlainString();
  }

  private static Instant instant(ResultSet r, String column) throws SQLException {
    var value = r.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }
}
