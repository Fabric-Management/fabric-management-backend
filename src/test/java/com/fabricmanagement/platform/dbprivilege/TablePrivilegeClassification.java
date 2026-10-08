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
    entries.put("flowboard.decision_subject_projection", TablePrivilegeClass.MUTABLE);
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
    // SOI D1-D8 order intake
    entries.put(
        "production.batch_finished_width_measurement", TablePrivilegeClass.APPEND_ONLY_LEDGER);
    entries.put("sales_ord.customer_tone_acceptance", TablePrivilegeClass.APPEND_ONLY_LEDGER);
    entries.put("sales_ord.quantity_proposal", TablePrivilegeClass.APPEND_ONLY_LEDGER);
    entries.put("sales_ord.customer_request_evaluation", TablePrivilegeClass.APPEND_ONLY_LEDGER);
    entries.put("sales_ord.customer_request_decision", TablePrivilegeClass.APPEND_ONLY_LEDGER);
    entries.put("sales_ord.line_product_correction", TablePrivilegeClass.APPEND_ONLY_LEDGER);
    entries.put("sales_ord.delivery_commitment", TablePrivilegeClass.APPEND_ONLY_LEDGER);
    entries.put("sales_ord.delivery_proposal", TablePrivilegeClass.APPEND_ONLY_LEDGER);
    entries.put("sales_ord.order_flow_event", TablePrivilegeClass.APPEND_ONLY_LEDGER);
    entries.put("sales_ord.order_work_assignment_event", TablePrivilegeClass.APPEND_ONLY_LEDGER);
    entries.put("sales_ord.order_version", TablePrivilegeClass.APPEND_ONLY_LEDGER);
    entries.put("production.prod_product_finished_width", TablePrivilegeClass.MUTABLE_SYSTEM_PURGE);
    entries.put("production.prod_product_sales_unit", TablePrivilegeClass.MUTABLE_SYSTEM_PURGE);
    entries.put("production.stock_unit_cut", TablePrivilegeClass.MUTABLE_SYSTEM_PURGE);
    entries.put(
        "production.lot_compatibility_confirmation", TablePrivilegeClass.MUTABLE_SYSTEM_PURGE);
    entries.put("production.lot_compatibility_request", TablePrivilegeClass.MUTABLE_SYSTEM_PURGE);
    entries.put("production.stock_unit_allocation", TablePrivilegeClass.MUTABLE_SYSTEM_PURGE);
    entries.put("production.work_order_hold", TablePrivilegeClass.MUTABLE_SYSTEM_PURGE);
    entries.put("sales_ord.quantity_acceptance", TablePrivilegeClass.MUTABLE_SYSTEM_PURGE);
    entries.put("sales_ord.customer_product_request", TablePrivilegeClass.MUTABLE_SYSTEM_PURGE);
    entries.put("sales_ord.customer_request_revision", TablePrivilegeClass.MUTABLE_SYSTEM_PURGE);
    entries.put("sales_ord.intake_attachment", TablePrivilegeClass.MUTABLE_SYSTEM_PURGE);
    entries.put("sales_ord.line_greige_cover", TablePrivilegeClass.MUTABLE_SYSTEM_PURGE);
    entries.put("sales_ord.line_portion_readiness", TablePrivilegeClass.MUTABLE_SYSTEM_PURGE);
    entries.put("sales_ord.order_arrival_estimate", TablePrivilegeClass.MUTABLE_SYSTEM_PURGE);
    entries.put("sales_ord.order_work_assignment", TablePrivilegeClass.MUTABLE_SYSTEM_PURGE);
    entries.put("sales_ord.customer_approval", TablePrivilegeClass.MUTABLE_SYSTEM_PURGE);
    entries.put("sales_ord.customer_approval_authority", TablePrivilegeClass.CLOSE_ONCE_LEDGER);
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
