package com.fabricmanagement.flowboard.decision.infra.repository;

import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverProjectionPort.Facts;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Atomic dominance guard shared by listener, replay and rebuild. */
@Repository
@Slf4j
public class DecisionSubjectProjectionWriter {
  private final JdbcTemplate jdbc;
  private final MeterRegistry metrics;
  private final Clock clock;

  public DecisionSubjectProjectionWriter(
      @Qualifier("dataSource") DataSource dataSource, MeterRegistry metrics, Clock clock) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.metrics = metrics;
    this.clock = clock;
  }

  @Transactional
  public ApplyResult apply(Facts facts, UUID sourceEventId) {
    UUID id = UUID.randomUUID();
    var now = clock.instant();
    var rows =
        jdbc.query(
            """
            INSERT INTO flowboard.decision_subject_projection
              (id, tenant_id, uid, created_at, created_by, updated_at, updated_by,
               is_active, deleted_at, version, case_id, kind, subject_type, subject_id,
               subject_number, order_created_by, task_id, case_state, case_revision, unresolved_line_count,
               case_opened_at, case_closed_at, evidence_revision, verdict_code,
               projected_at, source_event_id)
            VALUES
              (?, ?, ?, ?, ?, ?, ?, true, null, 0, ?, 'ORDER_COVER', 'SALES_ORDER', ?, ?, ?,
               ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (tenant_id, case_id) DO UPDATE SET
              subject_id = EXCLUDED.subject_id,
              subject_number = EXCLUDED.subject_number,
              order_created_by = EXCLUDED.order_created_by,
              task_id = COALESCE(EXCLUDED.task_id, decision_subject_projection.task_id),
              case_state = EXCLUDED.case_state,
              case_revision = EXCLUDED.case_revision,
              unresolved_line_count = EXCLUDED.unresolved_line_count,
              case_opened_at = EXCLUDED.case_opened_at,
              case_closed_at = EXCLUDED.case_closed_at,
              evidence_revision = EXCLUDED.evidence_revision,
              verdict_code = EXCLUDED.verdict_code,
              projected_at = EXCLUDED.projected_at,
              source_event_id = EXCLUDED.source_event_id,
              updated_at = EXCLUDED.updated_at,
              updated_by = EXCLUDED.updated_by,
              version = decision_subject_projection.version + 1
            WHERE EXCLUDED.case_revision >= decision_subject_projection.case_revision
              AND (decision_subject_projection.evidence_revision IS NULL
                   OR (EXCLUDED.evidence_revision IS NOT NULL
                       AND EXCLUDED.evidence_revision >= decision_subject_projection.evidence_revision))
              AND (decision_subject_projection.subject_id,
                   decision_subject_projection.subject_number,
                   decision_subject_projection.order_created_by,
                   decision_subject_projection.task_id,
                   decision_subject_projection.case_state,
                   decision_subject_projection.case_revision,
                   decision_subject_projection.unresolved_line_count,
                   decision_subject_projection.case_opened_at,
                   decision_subject_projection.case_closed_at,
                   decision_subject_projection.evidence_revision,
                   decision_subject_projection.verdict_code)
                  IS DISTINCT FROM
                  (EXCLUDED.subject_id,
                   EXCLUDED.subject_number,
                   EXCLUDED.order_created_by,
                   COALESCE(EXCLUDED.task_id, decision_subject_projection.task_id),
                   EXCLUDED.case_state,
                   EXCLUDED.case_revision,
                   EXCLUDED.unresolved_line_count,
                   EXCLUDED.case_opened_at,
                   EXCLUDED.case_closed_at,
                   EXCLUDED.evidence_revision,
                   EXCLUDED.verdict_code)
            RETURNING (xmax = 0) AS inserted
            """,
            (rs, row) -> rs.getBoolean("inserted"),
            id,
            facts.tenantId(),
            "SYS-000-DSP-" + id.toString().replace("-", ""),
            Timestamp.from(now),
            SystemUser.ID,
            Timestamp.from(now),
            SystemUser.ID,
            facts.caseId(),
            facts.orderId(),
            facts.orderNumber(),
            facts.orderCreatedBy(),
            facts.taskId(),
            facts.caseState(),
            facts.caseRevision(),
            facts.unresolvedLineCount(),
            Timestamp.from(facts.openedAt()),
            facts.closedAt() == null ? null : Timestamp.from(facts.closedAt()),
            facts.evidenceRevision(),
            facts.verdictCode().name(),
            Timestamp.from(now),
            sourceEventId);
    if (!rows.isEmpty()) return rows.getFirst() ? ApplyResult.INSERTED : ApplyResult.UPDATED;
    metrics.counter("decision.projection.write.skipped").increment();
    VersionPair current =
        jdbc
            .query(
                """
            SELECT case_revision,evidence_revision
            FROM flowboard.decision_subject_projection
            WHERE tenant_id=? AND case_id=?
            """,
                (rs, row) ->
                    new VersionPair(
                        rs.getLong("case_revision"), rs.getObject("evidence_revision", Long.class)),
                facts.tenantId(),
                facts.caseId())
            .stream()
            .findFirst()
            .orElse(null);
    if (current != null && !dominates(facts, current)) {
      metrics.counter("decision.projection.write.non_dominating").increment();
      log.warn(
          "Non-dominating decision projection write skipped: tenant={}, case={}",
          facts.tenantId(),
          facts.caseId());
    } else {
      log.debug(
          "Equal decision projection write skipped: tenant={}, case={}",
          facts.tenantId(),
          facts.caseId());
    }
    return ApplyResult.SKIPPED;
  }

  private static boolean dominates(Facts incoming, VersionPair current) {
    return incoming.caseRevision() >= current.caseRevision()
        && (current.evidenceRevision() == null
            || (incoming.evidenceRevision() != null
                && incoming.evidenceRevision() >= current.evidenceRevision()));
  }

  @Transactional
  public int delete(UUID tenantId, UUID caseId) {
    return jdbc.update(
        "DELETE FROM flowboard.decision_subject_projection WHERE tenant_id=? AND case_id=?",
        tenantId,
        caseId);
  }

  @Transactional(readOnly = true)
  public List<UUID> caseIdsAfter(UUID tenantId, UUID afterCaseId, int limit) {
    return jdbc.query(
        """
        SELECT case_id FROM flowboard.decision_subject_projection
        WHERE tenant_id=? AND (?::uuid IS NULL OR case_id > ?)
        ORDER BY case_id LIMIT ?
        """,
        (rs, row) -> rs.getObject(1, UUID.class),
        tenantId,
        afterCaseId,
        afterCaseId,
        limit);
  }

  /** Captures the orphan candidates from one snapshot while still paging the table by keyset. */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<UUID> caseIdsSnapshot(UUID tenantId, int batchSize) {
    List<UUID> result = new ArrayList<>();
    UUID cursor = null;
    while (true) {
      List<UUID> page = caseIdsAfterInCurrentTransaction(tenantId, cursor, batchSize);
      if (page.isEmpty()) return List.copyOf(result);
      result.addAll(page);
      cursor = page.getLast();
      if (page.size() < batchSize) return List.copyOf(result);
    }
  }

  private List<UUID> caseIdsAfterInCurrentTransaction(UUID tenantId, UUID afterCaseId, int limit) {
    return jdbc.query(
        """
        SELECT case_id FROM flowboard.decision_subject_projection
        WHERE tenant_id=? AND (?::uuid IS NULL OR case_id > ?)
        ORDER BY case_id LIMIT ?
        """,
        (rs, row) -> rs.getObject(1, UUID.class),
        tenantId,
        afterCaseId,
        afterCaseId,
        limit);
  }

  public enum ApplyResult {
    INSERTED,
    UPDATED,
    SKIPPED
  }

  private record VersionPair(long caseRevision, Long evidenceRevision) {}
}
