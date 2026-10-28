package com.fabricmanagement.finance.collection.infra.repository;

/** Bitemporal, finance-owned evidence only. Never join current Payment status into a past cut. */
final class CollectionReportSql {
  private CollectionReportSql() {}

  static final String FACTS =
      """
      WITH scoped AS (
        SELECT o.* FROM finance.collection_obligation o
        WHERE o.tenant_id=:tenant AND o.created_at<=:cutoff
          AND (:allScope OR o.created_by IN (:principals) OR o.responsible_user_id IN (:principals))
          AND (CAST(:partner AS uuid) IS NULL OR o.partner_id=:partner)
          AND (CAST(:owner AS uuid) IS NULL OR o.responsible_user_id=:owner)
          AND (CAST(:orderId AS uuid) IS NULL OR o.order_id=:orderId)
          AND (CAST(:currency AS text) IS NULL OR o.currency=:currency)
      ), source AS (
        SELECT DISTINCT ON (s.order_id) s.* FROM finance.collection_source_observation s
        WHERE s.tenant_id=:tenant AND s.created_at<=:cutoff AND s.observed_at<=:cutoff
          AND s.effective_at<:endAt
        ORDER BY s.order_id,s.effective_at DESC,s.source_version DESC,s.created_at DESC,s.id DESC
      ), health AS (
        SELECT DISTINCT ON (s.order_id) s.* FROM finance.collection_source_observation s
        WHERE s.tenant_id=:tenant AND s.created_at<=:cutoff AND s.observed_at<=:cutoff
        ORDER BY s.order_id,s.observed_at DESC,s.source_version DESC,s.id DESC
      ), expected AS (
        SELECT DISTINCT ON (o.id) o.id,o.order_id,o.partner_id,o.responsible_user_id,o.currency,
          e.amount,e.method,e.expected_date,e.cheque_payment_date,e.awaits_confirmation_date,
          (s.cancelled OR NOT (s.active_logical_ids @> jsonb_build_array(o.logical_id::text))) AS cancelled,
          COALESCE(h.stale,true) AS source_stale,COALESCE(h.source_missing,true) AS source_missing,
          h.observed_at AS source_observed_at
        FROM scoped o JOIN source s ON s.order_id=o.order_id
        JOIN finance.collection_expectation e ON e.tenant_id=o.tenant_id AND e.obligation_id=o.id
          AND e.created_at<=:cutoff AND e.activated_at<:endAt
          AND (e.revision_id=s.revision_id OR s.cancelled
            OR NOT (s.active_logical_ids @> jsonb_build_array(o.logical_id::text)))
        LEFT JOIN health h ON h.order_id=o.order_id
        ORDER BY o.id,e.activated_at DESC,e.created_at DESC,e.id DESC
      ), match_cut AS (
        SELECT m.* FROM finance.collection_payment_match m JOIN scoped o ON o.id=m.obligation_id
        WHERE m.tenant_id=:tenant AND m.created_at<=:cutoff AND m.effective_date<=:asOf
          AND NOT (m.reversed_at IS NOT NULL AND m.reversed_at<=:cutoff AND m.reversed_on<=:asOf)
      ), cash AS (
        SELECT obligation_id,sum(amount) AS cash,
          sum(CASE WHEN cheque_id IS NULL THEN amount ELSE 0 END) AS non_cheque_cash,
          bool_or(invalidation_recorded_at IS NOT NULL AND invalidation_recorded_at<=:cutoff) AS unknown
        FROM match_cut GROUP BY obligation_id
      ), refund_cut AS (
        -- A refund is owed from when it opens until a settlement or cancellation reaches the cut.
        SELECT r.obligation_id,r.amount,
          COALESCE((SELECT ev.action FROM finance.collection_refund_event ev
            WHERE ev.tenant_id=r.tenant_id AND ev.refund_id=r.id AND ev.created_at<=:cutoff
              AND ev.effective_date<=:asOf
            ORDER BY ev.step DESC LIMIT 1),'OPEN') AS last_action
        FROM finance.collection_refund r JOIN scoped o ON o.id=r.obligation_id
        WHERE r.tenant_id=:tenant AND r.created_at<=:cutoff AND r.effective_date<=:asOf
      ), refunds AS (
        SELECT obligation_id,sum(amount) AS pending FROM refund_cut
        WHERE last_action IN ('OPEN','REOPEN') GROUP BY obligation_id
      ), cheque_cut AS (
        SELECT DISTINCT ON (v.cheque_id) v.*
          FROM finance.collection_cheque_event v
        WHERE v.tenant_id=:tenant AND v.created_at<=:cutoff AND v.effective_date<=:asOf
        ORDER BY v.cheque_id,v.effective_date DESC,v.evidence_revision DESC,v.created_at DESC,v.id DESC
      ), cheque_held AS (
        -- What each cleared cheque's matches still hold at the cut: refunding part of the cleared
        -- money slices a match, but the application it came from never changes.
        SELECT cheque_id,obligation_id,sum(amount) AS held FROM match_cut
        WHERE cheque_id IS NOT NULL GROUP BY cheque_id,obligation_id
      ), instruments AS (
        SELECT a.obligation_id,
          sum(CASE WHEN v.state IN ('RECEIVED','PRESENTED') THEN a.amount
            WHEN v.state='CLEARED' THEN least(a.amount,COALESCE(h.held,0)) ELSE 0 END) AS received,
          bool_or(v.state IN ('RECEIVED','PRESENTED') AND v.due_on<:asOf) AS overdue_cheque,
          bool_or(v.state IN ('RECEIVED','PRESENTED') AND v.due_on=:asOf) AS cheque_due_today,
          bool_or(v.state='DISHONOURED') AS dishonoured
        FROM finance.collection_cheque_application a JOIN scoped o ON o.id=a.obligation_id
        JOIN cheque_cut v ON v.cheque_id=a.cheque_id
        LEFT JOIN cheque_held h ON h.cheque_id=a.cheque_id AND h.obligation_id=a.obligation_id
        WHERE a.tenant_id=:tenant AND a.created_at<=:cutoff GROUP BY a.obligation_id
      ), balances AS (
        SELECT e.*,COALESCE(c.unknown,false) AS unknown,
          CASE WHEN e.cancelled THEN 0 ELSE e.amount END AS planned,
          CASE WHEN c.unknown THEN NULL ELSE COALESCE(c.cash,0) END AS cash,
          CASE WHEN c.unknown THEN NULL WHEN e.cancelled THEN 0
            ELSE greatest(e.amount-COALESCE(c.cash,0),0) END AS outstanding,
          COALESCE(i.received,0) AS received,
          CASE WHEN c.unknown THEN NULL WHEN e.cancelled OR e.method<>'CHEQUE' THEN 0
            ELSE greatest(e.amount-COALESCE(i.received,0)-COALESCE(c.non_cheque_cash,0),0) END AS missing,
          COALESCE(i.overdue_cheque,false) AS overdue_cheque,
          COALESCE(i.cheque_due_today,false) AS cheque_due_today,
          COALESCE(i.dishonoured,false) AS dishonoured,
          COALESCE(rf.pending,0) AS refund_pending
        FROM expected e LEFT JOIN cash c ON c.obligation_id=e.id
        LEFT JOIN instruments i ON i.obligation_id=e.id
        LEFT JOIN refunds rf ON rf.obligation_id=e.id
      ), labelled AS (
        SELECT b.*,array_remove(ARRAY[
          CASE WHEN unknown THEN 'UNKNOWN_BALANCE' END,
          CASE WHEN cancelled AND (COALESCE(cash,0)>0 OR received>0 OR unknown) THEN 'CANCELLED_REVIEW' END,
          CASE WHEN NOT cancelled AND cash>planned THEN 'OVER_SETTLED_REVIEW' END,
          CASE WHEN refund_pending>0 THEN 'REFUND_PENDING' END,
          CASE WHEN NOT cancelled AND outstanding=0 THEN 'SETTLED' END,
          CASE WHEN NOT cancelled AND outstanding>0 AND method<>'CHEQUE' AND expected_date<:asOf THEN 'CASH_OVERDUE' END,
          CASE WHEN NOT cancelled AND missing>0 AND expected_date<:asOf THEN 'CHEQUE_DELIVERY_OVERDUE' END,
          CASE WHEN NOT cancelled AND outstanding>0 AND overdue_cheque THEN 'CHEQUE_CASH_OVERDUE' END,
          CASE WHEN NOT cancelled AND method='CHEQUE' AND outstanding>0 AND missing=0 THEN 'CHEQUE_AWAITING_CLEARING' END,
          CASE WHEN NOT cancelled AND outstanding>0 AND (expected_date=:asOf OR cheque_due_today) THEN 'DUE_TODAY' END,
          CASE WHEN NOT cancelled AND outstanding>0 AND expected_date>:asOf THEN 'FUTURE' END,
          CASE WHEN NOT cancelled AND expected_date IS NULL THEN 'UNDATED' END,
          CASE WHEN dishonoured THEN 'DISHONOURED' END],NULL) AS buckets
        FROM balances b
      ), filtered AS (
        SELECT * FROM labelled WHERE (CAST(:bucket AS text) IS NULL OR :bucket=ANY(buckets))
          AND (CAST(:fromDate AS date) IS NULL OR expected_date>=:fromDate)
          AND (CAST(:toDate AS date) IS NULL OR expected_date<=:toDate)
      )
      """;
}
