package com.fabricmanagement.platform.dbprivilege;

import java.util.LinkedHashMap;
import java.util.Map;

final class TablePrivilegeClassification {
  private static final Map<String, TablePrivilegeClass> ENTRIES = entries();

  private TablePrivilegeClassification() {}

  static Map<String, TablePrivilegeClass> declaredEntries() {
    return ENTRIES;
  }

  static TablePrivilegeClass classFor(String relation) {
    return ENTRIES.getOrDefault(relation, TablePrivilegeClass.MUTABLE);
  }

  private static Map<String, TablePrivilegeClass> entries() {
    Map<String, TablePrivilegeClass> entries = new LinkedHashMap<>();
    entries.put("flowboard.routing_pool", TablePrivilegeClass.MUTABLE);
    entries.put("flowboard.routing_pool_member", TablePrivilegeClass.MUTABLE);
    entries.put("flowboard.routing_task_state", TablePrivilegeClass.MUTABLE_SYSTEM_PURGE);
    entries.put("flowboard.routing_failure", TablePrivilegeClass.APPEND_ONLY_LEDGER);
    entries.put("flowboard.routing_failure_resolution", TablePrivilegeClass.APPEND_ONLY_LEDGER);
    entries.put("flowboard.routing_failure_alert", TablePrivilegeClass.MUTABLE_SYSTEM_PURGE);
    entries.put("flowboard.decision_follow", TablePrivilegeClass.APPEND_ONLY_LEDGER);
    entries.put("flowboard.decision_follow_suppression", TablePrivilegeClass.MUTABLE);
    entries.put("production.quality_decision", TablePrivilegeClass.APPEND_ONLY_LEDGER);
    entries.put("production.quality_decision_unit", TablePrivilegeClass.APPEND_ONLY_LEDGER);
    entries.put("sales_ord.requirement_profile_version", TablePrivilegeClass.APPEND_ONLY_LEDGER);
    entries.put("sales_ord.order_cover_evidence", TablePrivilegeClass.APPEND_ONLY_LEDGER);
    entries.put("sales_ord.order_cover_evidence_stream", TablePrivilegeClass.MUTABLE_SYSTEM_PURGE);
    entries.put("sales_ord.order_cover_activation", TablePrivilegeClass.APPEND_ONLY_LEDGER);
    entries.put("sales_ord.order_cover_case", TablePrivilegeClass.MUTABLE_SYSTEM_PURGE);
    entries.put("sales_ord.order_cover_case_line", TablePrivilegeClass.MUTABLE_SYSTEM_PURGE);
    entries.put("sales_ord.order_cover_result", TablePrivilegeClass.APPEND_ONLY_LEDGER);
    entries.put("sales_ord.order_cover_line_result", TablePrivilegeClass.APPEND_ONLY_LEDGER);
    entries.put("sales.customer_commercial_assignment", TablePrivilegeClass.CLOSE_ONCE_LEDGER);
    entries.put(
        "production.production_execution_batch_color_archive",
        TablePrivilegeClass.READ_ONLY_ARCHIVE);
    entries.put("common_tenant.flyway_schema_history", TablePrivilegeClass.NO_RUNTIME_ACCESS);
    entries.put("public.jobrunr_migrations", TablePrivilegeClass.APP_ONLY_MUTABLE);
    entries.put("public.jobrunr_jobs", TablePrivilegeClass.APP_ONLY_MUTABLE);
    entries.put("public.jobrunr_recurring_jobs", TablePrivilegeClass.APP_ONLY_MUTABLE);
    entries.put("public.jobrunr_backgroundjobservers", TablePrivilegeClass.APP_ONLY_MUTABLE);
    entries.put("public.jobrunr_metadata", TablePrivilegeClass.APP_ONLY_MUTABLE);
    entries.put("public.jobrunr_jobs_stats", TablePrivilegeClass.APP_ONLY_READ_ONLY);
    return Map.copyOf(entries);
  }
}
