package com.fabricmanagement.finance.collection.infra.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CollectionCustomerSummaryRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public record Plan(
      UUID id,
      UUID orderId,
      UUID creator,
      UUID owner,
      String currency,
      String method,
      LocalDate expectedDate,
      BigDecimal agreed,
      BigDecimal outstanding,
      BigDecimal missing,
      boolean unavailable) {}

  public record Held(
      UUID id,
      UUID creator,
      String currency,
      String reference,
      LocalDate dueOn,
      BigDecimal amount,
      BigDecimal applied,
      boolean presented,
      boolean visible,
      CollectionChequeOfferRepository.Evidence offer) {}

  public List<Plan> plans(MapSqlParameterSource params) {
    return jdbc.query(
        CollectionReportSql.FACTS
            + """
            SELECT o.id,o.order_id,o.created_by,o.responsible_user_id,o.currency,
              COALESCE(b.method,o.method) AS method,
              CASE WHEN b.id IS NULL THEN o.expected_date ELSE b.expected_date END AS expected_date,
              COALESCE(b.amount,o.amount) AS agreed,b.outstanding,b.missing,
              (b.id IS NULL OR b.source_missing OR b.source_stale OR b.unknown) AS unavailable
            FROM scoped o LEFT JOIN balances b ON b.id=o.id
            WHERE o.is_active AND NOT COALESCE(b.cancelled,o.cancelled)
              AND (b.id IS NULL OR b.source_missing OR b.source_stale OR b.unknown OR b.outstanding>0)
            ORDER BY expected_date NULLS LAST,o.id
            """,
        params,
        (r, n) ->
            new Plan(
                r.getObject("id", UUID.class),
                r.getObject("order_id", UUID.class),
                r.getObject("created_by", UUID.class),
                r.getObject("responsible_user_id", UUID.class),
                r.getString("currency"),
                r.getString("method"),
                r.getObject("expected_date", LocalDate.class),
                r.getBigDecimal("agreed"),
                r.getBigDecimal("outstanding"),
                r.getBigDecimal("missing"),
                r.getBoolean("unavailable")));
  }

  /** Cheque detail follows the intersection of the scopes of all its active applications. */
  public List<Held> held(MapSqlParameterSource params) {
    return jdbc.query(
        CollectionReportSql.FACTS
            + """
            SELECT c.id,c.created_by,c.currency,v.instrument_reference,v.due_on,v.amount,
              a.applied,v.state='PRESENTED' AS presented,
              CASE WHEN a.applied=0 THEN (:allScope OR COALESCE(c.created_by IN (:principals),false))
                ELSE a.all_targets_visible END AS visible,
              offer.cheque_id,offer.obligation_id,offer.actor_id,offer.recorded_at
            FROM finance.collection_cheque c
            JOIN cheque_cut v ON v.cheque_id=c.id
            CROSS JOIN LATERAL (
              SELECT COALESCE(sum(a.amount),0) AS applied,
                bool_and(s.id IS NOT NULL) AS all_targets_visible
              FROM finance.collection_cheque_application a LEFT JOIN scoped s ON s.id=a.obligation_id
              WHERE a.tenant_id=c.tenant_id AND a.cheque_id=c.id AND a.is_active
                AND a.created_at<=:cutoff AND a.effective_date<=:asOf
                AND NOT (a.reversed_at IS NOT NULL AND a.reversed_at<=:cutoff AND a.reversed_on<=:asOf)
            ) a
            LEFT JOIN finance.collection_cheque_offer offer ON offer.tenant_id=c.tenant_id
              AND offer.cheque_id=c.id AND offer.recorded_at<=:cutoff
            WHERE c.tenant_id=:tenant AND c.partner_id=:partner AND c.is_active
              AND v.state IN ('RECEIVED','PRESENTED')
            ORDER BY v.due_on,c.id
            """,
        params,
        (r, n) ->
            new Held(
                r.getObject("id", UUID.class),
                r.getObject("created_by", UUID.class),
                r.getString("currency"),
                r.getString("instrument_reference"),
                r.getObject("due_on", LocalDate.class),
                r.getBigDecimal("amount"),
                r.getBigDecimal("applied"),
                r.getBoolean("presented"),
                r.getBoolean("visible"),
                r.getObject("cheque_id", UUID.class) == null
                    ? null
                    : CollectionChequeOfferRepository.evidence(r)));
  }

  public List<UUID> paymentPage(MapSqlParameterSource params, UUID after, int size) {
    return jdbc.query(
        """
        SELECT p.id FROM finance.finance_payment p
        WHERE p.tenant_id=:tenant AND p.trading_partner_id=:partner AND p.direction='INBOUND'
          AND p.is_active AND p.payment_date<=:asOf
          AND (:allScope OR p.created_by IN (:principals))
          AND (CAST(:after AS uuid) IS NULL OR p.id>:after)
        ORDER BY p.id LIMIT :size
        """,
        params.addValue("after", after).addValue("size", size),
        (r, n) -> r.getObject("id", UUID.class));
  }
}
