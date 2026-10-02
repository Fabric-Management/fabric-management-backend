package com.fabricmanagement.flowboard.generator.app.catalogue;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueSourceRow;
import com.fabricmanagement.flowboard.generator.domain.catalogue.TaskTemplateCatalogue;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads the keyed task-template rows of a catalogue source tenant (golden-template or the
 * playground source). The source is another tenant, so the read runs on {@code fabric_system}
 * (BYPASSRLS); every statement filters by {@code tenant_id} explicitly. Read-only.
 *
 * <p>BYPASSRLS use in FlowBoard is confined to this class and {@link PlaygroundCatalogueWriter}
 * (TASK-TEMPLATE-TENANCY-1 §7; enforced by {@code TaskTemplateCatalogueArchTest}).
 */
@Component
@RequiredArgsConstructor
public class CatalogueSourceReader {

  static final String SELECT_KEYED =
      """
      SELECT catalog_key, name, description, event_type, title_template, task_type, module_type,
             default_priority, default_assignee_role, estimated_hours, checklist_template,
             auto_labels, is_active, deleted_at
        FROM flowboard.task_template
       WHERE tenant_id = ? AND catalog_key IS NOT NULL
       ORDER BY catalog_key
      """;

  private final SystemTransactionExecutor systemExecutor;

  /** Keyed rows of {@code sourceTenantId}, in every state. */
  public List<CatalogueSourceRow> readKeyedRows(UUID sourceTenantId) {
    return systemExecutor.executeQuery(SELECT_KEYED, CatalogueSourceReader::map, sourceTenantId);
  }

  /** Same read on a caller-supplied template, to stay inside the caller's transaction. */
  static List<CatalogueSourceRow> readKeyedRows(JdbcTemplate jdbc, UUID sourceTenantId) {
    return jdbc.query(SELECT_KEYED, CatalogueSourceReader::map, sourceTenantId);
  }

  /**
   * golden-template's catalogue, validated: exactly one row per {@link TaskTemplateCatalogue}
   * entry, active, not deleted, with the pinned content fingerprint. Anything else means the
   * catalogue migration did not run or golden drifted, which is a technical failure.
   */
  public List<CatalogueSourceRow> readCompleteCatalogue(UUID goldenTenantId) {
    List<CatalogueSourceRow> rows = readKeyedRows(goldenTenantId);
    Map<String, CatalogueSourceRow> byKey = new HashMap<>();
    for (CatalogueSourceRow row : rows) {
      if (byKey.put(row.catalogKey(), row) != null) {
        throw new IllegalStateException(
            "Task-template catalogue: duplicate key " + row.catalogKey() + " in golden");
      }
    }
    for (TaskTemplateCatalogue entry : TaskTemplateCatalogue.values()) {
      CatalogueSourceRow row = byKey.get(entry.key());
      if (row == null) {
        throw new IllegalStateException(
            "Task-template catalogue: golden " + goldenTenantId + " lacks " + entry.key());
      }
      if (!row.active() || row.deletedAt() != null) {
        throw new IllegalStateException(
            "Task-template catalogue: golden row " + entry.key() + " is not active");
      }
      if (!entry.eventType().equals(row.eventType())
          || !entry.taskType().name().equals(row.taskType())
          || !entry.seedFingerprint().equals(row.fingerprint())) {
        throw new IllegalStateException(
            "Task-template catalogue: golden row "
                + entry.key()
                + " drifted from its pinned content");
      }
    }
    if (byKey.size() != TaskTemplateCatalogue.values().length) {
      throw new IllegalStateException(
          "Task-template catalogue: golden holds unknown keys " + byKey.keySet());
    }
    return rows;
  }

  private static CatalogueSourceRow map(ResultSet rs, int rowNum) throws SQLException {
    OffsetDateTime deletedAt = rs.getObject("deleted_at", OffsetDateTime.class);
    return new CatalogueSourceRow(
        rs.getString("catalog_key"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getString("event_type"),
        rs.getString("title_template"),
        rs.getString("task_type"),
        rs.getString("module_type"),
        rs.getString("default_priority"),
        rs.getString("default_assignee_role"),
        rs.getBigDecimal("estimated_hours"),
        rs.getString("checklist_template"),
        rs.getString("auto_labels"),
        rs.getBoolean("is_active"),
        deletedAt == null ? null : deletedAt.toInstant());
  }
}
