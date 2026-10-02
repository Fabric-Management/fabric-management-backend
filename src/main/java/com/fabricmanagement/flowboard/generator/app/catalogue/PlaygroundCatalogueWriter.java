package com.fabricmanagement.flowboard.generator.app.catalogue;

import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueCandidate;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueDecision;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueFinding;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueReconciler;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueReconciliationResult;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueSourceRow;
import com.fabricmanagement.flowboard.generator.domain.catalogue.TaskTemplateCatalogue;
import com.fabricmanagement.platform.user.domain.SystemUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Copies the playground source's catalogue rows into a new playground tenant, inside the clone's
 * {@code SystemTransactionExecutor} transaction (ticket §7, plan §3).
 *
 * <p>Every catalogue key must be present in the source (in any state); a missing key fails the
 * clone, which rolls the playground back. A copy keeps its source's {@code catalog_key}, {@code
 * is_active} and {@code deleted_at}: a row the source removed arrives removed and keyed, so the
 * next backfill resolves it to R0 instead of inserting an active copy from golden. Each copy gets a
 * fresh uid ({@code <tenantUid>-TMPL-xxxx}, the {@code BaseEntity} format) and {@code created_by =
 * SYSTEM_USER_ID}, so "{@code created_by IS NULL}" keeps meaning "written by an SQL seed".
 */
@Component
@Slf4j
public class PlaygroundCatalogueWriter {

  static final String SELECT_TARGET =
      """
      SELECT id, catalog_key, event_type, task_type, name, description, uid, created_by,
             updated_by, version, deleted_at, title_template, module_type, default_priority,
             default_assignee_role, estimated_hours, auto_labels, checklist_template
        FROM flowboard.task_template
       WHERE tenant_id = ?
      """;

  static final String INSERT_COPY =
      """
      INSERT INTO flowboard.task_template (
          id, tenant_id, uid, name, description, event_type, title_template, task_type,
          module_type, default_priority, default_assignee_role, estimated_hours,
          checklist_template, auto_labels, is_active, deleted_at, catalog_key,
          created_at, created_by, updated_at, updated_by, version)
      VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
              now(), ?, now(), ?, 0)
      """;

  static final String ADOPT =
      "UPDATE flowboard.task_template SET catalog_key = ? WHERE id = ? AND tenant_id = ?"
          + " AND catalog_key IS NULL";

  private final JdbcTemplate jdbc;
  private final DataSource systemDataSource;

  public PlaygroundCatalogueWriter(
      @Qualifier("systemJdbcTemplate") JdbcTemplate jdbc,
      @Qualifier("systemDataSource") DataSource systemDataSource) {
    this.jdbc = jdbc;
    this.systemDataSource = systemDataSource;
  }

  public CatalogueReconciliationResult copyFromSource(
      UUID sourceTenantId, UUID targetTenantId, String targetTenantUid) {
    requireSystemTransaction();
    Map<String, CatalogueSourceRow> sourceByKey = new HashMap<>();
    CatalogueSourceReader.readKeyedRows(jdbc, sourceTenantId)
        .forEach(row -> sourceByKey.put(row.catalogKey(), row));
    List<CatalogueCandidate> targetRows =
        jdbc.query(SELECT_TARGET, PlaygroundCatalogueWriter::candidate, targetTenantId);

    int inserted = 0;
    int adopted = 0;
    int unchanged = 0;
    List<CatalogueFinding> findings = new ArrayList<>();
    for (TaskTemplateCatalogue entry : TaskTemplateCatalogue.values()) {
      CatalogueSourceRow sourceRow = sourceByKey.get(entry.key());
      if (sourceRow == null) {
        // Fail the clone: the backfill runs only at startup, so a playground created now with a
        // missing entry would stay without that task template until the next restart. A key that
        // is present but soft-deleted in the source is not missing; it is copied as deleted.
        throw new IllegalStateException(
            "Playground catalogue: source "
                + sourceTenantId
                + " lacks "
                + entry.key()
                + "; playground "
                + targetTenantId
                + " not created");
      }
      CatalogueDecision decision = CatalogueReconciler.decide(entry, targetRows);
      switch (decision.kind()) {
        case R1_INSERT -> {
          insertCopy(sourceRow, targetTenantId, targetTenantUid);
          inserted++;
        }
        case R2_ADOPT -> {
          jdbc.update(ADOPT, entry.key(), decision.adoptId(), targetTenantId);
          adopted++;
        }
        case R0_KEYED, R3_NO_WRITE -> unchanged++;
      }
      findings.addAll(decision.findings());
    }
    findings.forEach(
        finding ->
            log.warn(
                "Task-template catalogue finding: tenant={} key={} reason={} candidates={}",
                targetTenantId,
                finding.catalogKey(),
                finding.reason(),
                finding.candidateIds()));
    return new CatalogueReconciliationResult(inserted, adopted, unchanged, findings);
  }

  private void insertCopy(CatalogueSourceRow source, UUID targetTenantId, String targetTenantUid) {
    jdbc.update(
        INSERT_COPY,
        targetTenantId,
        freshUid(targetTenantUid),
        source.name(),
        source.description(),
        source.eventType(),
        source.titleTemplate(),
        source.taskType(),
        source.moduleType(),
        source.defaultPriority(),
        source.defaultAssigneeRole(),
        source.estimatedHours(),
        source.checklistTemplate(),
        source.autoLabels(),
        source.active(),
        source.deletedAt() == null ? null : Timestamp.from(source.deletedAt()),
        source.catalogKey(),
        SystemUser.ID,
        SystemUser.ID);
  }

  private void requireSystemTransaction() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || TransactionSynchronizationManager.getResource(systemDataSource) == null) {
      throw new IllegalStateException(
          "Playground catalogue copy must run inside the clone's system transaction");
    }
  }

  static String freshUid(String tenantUid) {
    String suffix =
        UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    return (tenantUid == null || tenantUid.isBlank() ? "SYS-000" : tenantUid) + "-TMPL-" + suffix;
  }

  private static CatalogueCandidate candidate(ResultSet rs, int rowNum) throws SQLException {
    OffsetDateTime deletedAt = rs.getObject("deleted_at", OffsetDateTime.class);
    String taskType = rs.getString("task_type");
    return new CatalogueCandidate(
        rs.getObject("id", UUID.class),
        rs.getString("catalog_key"),
        rs.getString("event_type"),
        taskType,
        rs.getString("name"),
        rs.getString("description"),
        rs.getString("uid"),
        rs.getObject("created_by", UUID.class),
        rs.getObject("updated_by", UUID.class),
        rs.getLong("version"),
        deletedAt == null ? null : deletedAt.toInstant(),
        TaskTemplateCatalogue.fingerprint(
            rs.getString("title_template"),
            taskType,
            rs.getString("module_type"),
            rs.getString("default_priority"),
            rs.getString("default_assignee_role"),
            rs.getBigDecimal("estimated_hours"),
            rs.getString("auto_labels"),
            rs.getString("checklist_template")));
  }
}
