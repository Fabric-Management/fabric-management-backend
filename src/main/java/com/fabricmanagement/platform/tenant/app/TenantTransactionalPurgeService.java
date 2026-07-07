package com.fabricmanagement.platform.tenant.app;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantTransactionalPurgeService {

  private static final String TENANT_DEMOMODE_CACHE = "tenant-demomode";

  private static final List<String> TRANSACTIONAL_TABLES =
      List.of(
          "flowboard.task_attachment",
          "flowboard.task_time_entry",
          "flowboard.task_dependency",
          "flowboard.task_activity_log",
          "flowboard.task_comment",
          "flowboard.task_checklist",
          "flowboard.task_label_assignment",
          "flowboard.task_assignee",
          "flowboard.task_reminder",
          "flowboard.task_relation",
          "flowboard.escalation_log",
          "flowboard.task",
          "flowboard.dashboard_widget",
          "flowboard.dashboard_config",
          "flowboard.board_view",
          "flowboard.board_group",
          "flowboard.automation_rule",
          "flowboard.recurring_task_template",
          "flowboard.task_template",
          "flowboard.task_label",
          "flowboard.user_performance_snapshot",
          "flowboard.board",
          "notification.notification_log",
          "notification.notification_queue",
          "common_communication.communication_email_outbox",
          "common_communication.common_verification_log",
          "common_communication.common_notification",
          "common_approval.approval_request",
          "common_approval.user_promotion_request",
          "common_approval.approval_policy",
          "finance.fx_revaluation",
          "finance.fx_realization",
          "finance.finance_credit_note_application",
          "finance.finance_payment_allocation",
          "finance.finance_payment",
          "finance.finance_invoice_tax_line",
          "finance.finance_invoice_line",
          "finance.finance_invoice",
          "finance.financial_period",
          "finance.document_number_counter",
          "costing.cost_calculation_line",
          "costing.cost_calculation",
          "costing.cost_history",
          "costing.volume_price_break",
          "costing.price_list_item",
          "costing.price_list",
          "costing.cost_template",
          "costing.cost_item",
          "costing.exchange_rate_snapshot",
          "costing.exchange_rate_cache",
          "logistics.logistics_shipment_line_batch",
          "logistics.logistics_shipment_line",
          "logistics.logistics_shipment",
          "iwm.rma_line",
          "iwm.rma",
          "iwm.stock_transfer",
          "iwm.stock_count_assignee",
          "iwm.stock_count_line",
          "iwm.stock_count",
          "iwm.stock_count_tolerance",
          "iwm.stock_reservation",
          "iwm.stock_adjustment_request",
          "iwm.min_stock_rule",
          "iwm.lot_end_rule",
          "iwm.return_rate_rule",
          "iwm.warehouse_location",
          "production.production_output_item",
          "production.production_output_record",
          "production.work_order_output",
          "production.work_order_consumption",
          "production.stock_unit_audit_log",
          "production.stock_unit_soft_hold",
          "production.stock_unit",
          "production.goods_receipt_item",
          "production.goods_receipt",
          "production.prod_work_order_assignee",
          "production.prod_work_order",
          "production.prod_recipe_component",
          "production.prod_recipe",
          "production.production_quality_fiber_test_result",
          "production.production_execution_batch_attribute",
          "production.production_execution_batch_certification",
          "production.production_execution_batch_reservation",
          "production.production_execution_inventory_transaction",
          "production.production_execution_inventory_balance",
          "production.production_execution_batch_lineage",
          "production.production_execution_batch",
          "production.production_fiber_request",
          "production.prod_yarn_category",
          "production.prod_yarn_attribute",
          "production.prod_yarn_certification",
          "procurement.supplier_quote_token",
          "procurement.supplier_quote_line",
          "procurement.supplier_quote",
          "procurement.supplier_rfq_recipient",
          "procurement.supplier_rfq_line",
          "procurement.supplier_rfq",
          "procurement.subcontract_order",
          "procurement.purchase_order_line",
          "procurement.purchase_order",
          "sales_ord.sales_order_line",
          "sales_ord.sales_order",
          "sales.sample_delivery",
          "sales.sample_request",
          "sales.quote_send_request",
          "sales.quote_approval_token",
          "sales.quote_line",
          "sales.quote",
          "sales.sales_product",
          "sales.discount_policy",
          "common_company.partner_contact",
          "human.human_pay_run_audit_log",
          "human.human_pay_run_payout",
          "human.human_pay_run_entry",
          "human.human_pay_run",
          "human.human_pay_period",
          "human.human_leave_accrual_log",
          "human.human_leave_balance",
          "human.human_employee_number_sequence",
          "common_user.profile_update_request",
          "common_infrastructure.document_sequence");

  static List<String> tenantScopedDeleteTables() {
    return TRANSACTIONAL_TABLES;
  }

  private final SystemTransactionExecutor systemExecutor;
  private final CacheManager cacheManager;
  private final Clock clock;

  @Value("${application.trial.base-days:90}")
  private int baseDays;

  public PurgeResult goReal(UUID tenantId) {
    if (tenantId == null) {
      throw new PlatformDomainException("Tenant not found", "TENANT_NOT_FOUND", 404);
    }
    Instant now = Instant.now(clock);
    Instant trialEndsAt = now.plus(baseDays, ChronoUnit.DAYS);

    Map<String, Integer> deletedRows =
        systemExecutor.executeInTransaction(
            jdbc -> {
              ensureTenantIsDemoMode(jdbc, tenantId);
              Map<String, Integer> rows = purgeDemoData(jdbc, tenantId);
              flipDemoModeAndStartClock(jdbc, tenantId, now, trialEndsAt);
              return rows;
            });

    evictDemoModeCache(tenantId);
    log.info(
        "Tenant go-real purge completed: tenantId={}, trialEndsAt={}, deletedRows={}",
        tenantId,
        trialEndsAt,
        deletedRows);
    return new PurgeResult(tenantId, deletedRows, now, trialEndsAt);
  }

  public PurgeDemoDataResult purgeDemoData(UUID tenantId) {
    if (tenantId == null) {
      throw new PlatformDomainException("Tenant not found", "TENANT_NOT_FOUND", 404);
    }

    Map<String, Integer> deletedRows =
        systemExecutor.executeInTransaction(
            jdbc -> {
              ensureTenantIsDemoMode(jdbc, tenantId);
              return purgeDemoData(jdbc, tenantId);
            });

    log.info(
        "Tenant demo data purge completed: tenantId={}, deletedRows={}", tenantId, deletedRows);
    return new PurgeDemoDataResult(tenantId, deletedRows);
  }

  private Map<String, Integer> purgeDemoData(JdbcTemplate jdbc, UUID tenantId) {
    Map<String, Integer> rows = new LinkedHashMap<>();
    deleteTransactionalRows(jdbc, tenantId, rows);
    deleteSeedUsers(jdbc, tenantId, rows);
    deleteTradingPartnerRows(jdbc, tenantId, rows);
    deleteProductReferenceRows(jdbc, tenantId, rows);
    return rows;
  }

  private void ensureTenantIsDemoMode(JdbcTemplate jdbc, UUID tenantId) {
    Boolean demoMode;
    try {
      demoMode =
          jdbc.queryForObject(
              """
              SELECT demo_mode
              FROM common_tenant.common_tenant
              WHERE id = ?
              FOR UPDATE
              """,
              Boolean.class,
              tenantId);
    } catch (EmptyResultDataAccessException ex) {
      throw new PlatformDomainException("Tenant not found", "TENANT_NOT_FOUND", 404);
    }
    if (!Boolean.TRUE.equals(demoMode)) {
      throw new PlatformDomainException("Tenant is already real", "TENANT_ALREADY_REAL", 409);
    }
  }

  private void deleteTransactionalRows(
      JdbcTemplate jdbc, UUID tenantId, Map<String, Integer> rows) {
    deleteChildRowsWithoutTenantId(jdbc, tenantId, rows);
    for (String table : TRANSACTIONAL_TABLES) {
      delete(jdbc, rows, table, "DELETE FROM " + table + " WHERE tenant_id = ?", tenantId);
    }
  }

  private void deleteChildRowsWithoutTenantId(
      JdbcTemplate jdbc, UUID tenantId, Map<String, Integer> rows) {
    delete(
        jdbc,
        rows,
        "production.production_execution_batch_override_log",
        """
        DELETE FROM production.production_execution_batch_override_log log
        USING production.production_execution_batch batch
        WHERE log.batch_id = batch.id
          AND batch.tenant_id = ?
        """,
        tenantId);
    delete(
        jdbc,
        rows,
        "sales_ord.sales_order_line_processed_shipments",
        """
        DELETE FROM sales_ord.sales_order_line_processed_shipments processed
        USING sales_ord.sales_order_line line
        WHERE processed.sales_order_line_id = line.id
          AND line.tenant_id = ?
        """,
        tenantId);
  }

  private void deleteTradingPartnerRows(
      JdbcTemplate jdbc, UUID tenantId, Map<String, Integer> rows) {
    delete(
        jdbc,
        rows,
        "common_communication.common_contact(external-partner-orgs)",
        """
        WITH partner_orgs AS (
          SELECT id
          FROM common_company.common_organization
          WHERE tenant_id = ? AND organization_type = 'EXTERNAL_PARTNER'
        ),
        partner_contacts AS (
          SELECT oc.contact_id
          FROM common_company.common_organization_contact oc
          JOIN partner_orgs po ON po.id = oc.organization_id
        )
        DELETE FROM common_communication.common_contact c
        USING partner_contacts pc
        WHERE c.id = pc.contact_id
          AND c.tenant_id = ?
          AND NOT EXISTS (
            SELECT 1
            FROM common_user.common_user_contact uc
            WHERE uc.contact_id = c.id
          )
          AND NOT EXISTS (
            SELECT 1
            FROM common_company.common_organization_contact oc2
            JOIN common_company.common_organization o2 ON o2.id = oc2.organization_id
            WHERE oc2.contact_id = c.id
              AND o2.tenant_id = ?
              AND o2.organization_type <> 'EXTERNAL_PARTNER'
          )
        """,
        tenantId,
        tenantId,
        tenantId);
    delete(
        jdbc,
        rows,
        "common_communication.common_address(external-partner-orgs)",
        """
        WITH partner_orgs AS (
          SELECT id
          FROM common_company.common_organization
          WHERE tenant_id = ? AND organization_type = 'EXTERNAL_PARTNER'
        ),
        partner_addresses AS (
          SELECT oa.address_id
          FROM common_company.common_organization_address oa
          JOIN partner_orgs po ON po.id = oa.organization_id
        )
        DELETE FROM common_communication.common_address a
        USING partner_addresses pa
        WHERE a.id = pa.address_id
          AND a.tenant_id = ?
          AND NOT EXISTS (
            SELECT 1
            FROM common_user.common_user_address ua
            WHERE ua.address_id = a.id
          )
          AND NOT EXISTS (
            SELECT 1
            FROM common_company.common_organization_address oa2
            JOIN common_company.common_organization o2 ON o2.id = oa2.organization_id
            WHERE oa2.address_id = a.id
              AND o2.tenant_id = ?
              AND o2.organization_type <> 'EXTERNAL_PARTNER'
          )
        """,
        tenantId,
        tenantId,
        tenantId);
    delete(
        jdbc,
        rows,
        "common_company.common_organization_contact(external-partner-orgs)",
        """
        DELETE FROM common_company.common_organization_contact oc
        USING common_company.common_organization o
        WHERE oc.organization_id = o.id
          AND o.tenant_id = ?
          AND o.organization_type = 'EXTERNAL_PARTNER'
        """,
        tenantId);
    delete(
        jdbc,
        rows,
        "common_company.common_organization_address(external-partner-orgs)",
        """
        DELETE FROM common_company.common_organization_address oa
        USING common_company.common_organization o
        WHERE oa.organization_id = o.id
          AND o.tenant_id = ?
          AND o.organization_type = 'EXTERNAL_PARTNER'
        """,
        tenantId);
    delete(
        jdbc,
        rows,
        "common_company.common_organization(external-partners)",
        """
        DELETE FROM common_company.common_organization
        WHERE tenant_id = ? AND organization_type = 'EXTERNAL_PARTNER'
        """,
        tenantId);
    delete(
        jdbc,
        rows,
        "common_company.partner_trading_partner_certification",
        "DELETE FROM common_company.partner_trading_partner_certification WHERE tenant_id = ?",
        tenantId);
    delete(
        jdbc,
        rows,
        "common_company.organization_certification",
        "DELETE FROM common_company.organization_certification WHERE tenant_id = ?",
        tenantId);
    delete(
        jdbc,
        rows,
        "common_company.common_trading_partner+unreferenced_registry",
        """
        WITH deleted_partners AS (
          DELETE FROM common_company.common_trading_partner
          WHERE tenant_id = ?
          RETURNING registry_id
        )
        DELETE FROM common_company.trading_partner_registry r
        WHERE r.id IN (SELECT registry_id FROM deleted_partners)
          AND NOT EXISTS (
            SELECT 1
            FROM common_company.common_trading_partner remaining
            WHERE remaining.registry_id = r.id
              AND remaining.tenant_id <> ?
          )
        """,
        tenantId,
        tenantId);
  }

  private void deleteProductReferenceRows(
      JdbcTemplate jdbc, UUID tenantId, Map<String, Integer> rows) {
    delete(
        jdbc,
        rows,
        "production.prod_product_attribute",
        "DELETE FROM production.prod_product_attribute WHERE tenant_id = ?",
        tenantId);
    delete(
        jdbc,
        rows,
        "production.quality_grade",
        "DELETE FROM production.quality_grade WHERE tenant_id = ?",
        tenantId);
    delete(
        jdbc,
        rows,
        "production.color",
        "DELETE FROM production.color WHERE tenant_id = ?",
        tenantId);
    delete(
        jdbc,
        rows,
        "production.prod_fiber_certification",
        "DELETE FROM production.prod_fiber_certification WHERE tenant_id = ?",
        tenantId);
    delete(
        jdbc,
        rows,
        "production.prod_fiber_quality_standard",
        "DELETE FROM production.prod_fiber_quality_standard WHERE tenant_id = ?",
        tenantId);
    delete(
        jdbc,
        rows,
        "production.prod_fiber",
        "DELETE FROM production.prod_fiber WHERE tenant_id = ?",
        tenantId);
    delete(
        jdbc,
        rows,
        "production.prod_product",
        "DELETE FROM production.prod_product WHERE tenant_id = ?",
        tenantId);
    delete(
        jdbc,
        rows,
        "production.prod_fiber_iso_code",
        "DELETE FROM production.prod_fiber_iso_code WHERE tenant_id = ?",
        tenantId);
    delete(
        jdbc,
        rows,
        "production.prod_fiber_category",
        "DELETE FROM production.prod_fiber_category WHERE tenant_id = ?",
        tenantId);
    delete(
        jdbc,
        rows,
        "production.inheritance_rule_schema",
        "DELETE FROM production.inheritance_rule_schema WHERE tenant_id = ?",
        tenantId);
  }

  private void deleteSeedUsers(JdbcTemplate jdbc, UUID tenantId, Map<String, Integer> rows) {
    delete(
        jdbc,
        rows,
        "common_communication.common_contact(seed-demo-users)",
        """
        WITH seed_contacts AS (
          SELECT uc.contact_id
          FROM common_user.common_user_contact uc
          JOIN common_user.common_user u ON u.id = uc.user_id
          WHERE u.tenant_id = ? AND u.demo_seed = true
        )
        DELETE FROM common_communication.common_contact c
        USING seed_contacts sc
        WHERE c.id = sc.contact_id
          AND c.tenant_id = ?
          AND NOT EXISTS (
            SELECT 1
            FROM common_user.common_user_contact uc2
            JOIN common_user.common_user u2 ON u2.id = uc2.user_id
            WHERE uc2.contact_id = c.id
              AND u2.tenant_id = ?
              AND u2.demo_seed = false
          )
          AND NOT EXISTS (
            SELECT 1
            FROM common_company.common_organization_contact oc
            WHERE oc.contact_id = c.id
          )
        """,
        tenantId,
        tenantId,
        tenantId);
    delete(
        jdbc,
        rows,
        "common_communication.common_address(seed-demo-users)",
        """
        WITH seed_addresses AS (
          SELECT ua.address_id
          FROM common_user.common_user_address ua
          JOIN common_user.common_user u ON u.id = ua.user_id
          WHERE u.tenant_id = ? AND u.demo_seed = true
        )
        DELETE FROM common_communication.common_address a
        USING seed_addresses sa
        WHERE a.id = sa.address_id
          AND a.tenant_id = ?
          AND NOT EXISTS (
            SELECT 1
            FROM common_user.common_user_address ua2
            JOIN common_user.common_user u2 ON u2.id = ua2.user_id
            WHERE ua2.address_id = a.id
              AND u2.tenant_id = ?
              AND u2.demo_seed = false
          )
          AND NOT EXISTS (
            SELECT 1
            FROM common_company.common_organization_address oa
            WHERE oa.address_id = a.id
          )
        """,
        tenantId,
        tenantId,
        tenantId);
    deleteSeedUserOwnedRows(jdbc, tenantId, rows, "common_auth.common_refresh_token", "user_id");
    deleteSeedUserOwnedRows(jdbc, tenantId, rows, "common_auth.common_trusted_device", "user_id");
    deleteSeedUserOwnedRows(jdbc, tenantId, rows, "common_auth.common_auth_user", "user_id");
    deleteSeedUserOwnedRows(jdbc, tenantId, rows, "common_user.user_nav_preferences", "user_id");
    deleteSeedUserOwnedRows(jdbc, tenantId, rows, "i18n.user_locale_config", "user_id");
    deleteSeedUserOwnedRows(
        jdbc, tenantId, rows, "notification.user_notification_preference", "user_id");
    deleteSeedUserOwnedRows(jdbc, tenantId, rows, "human.human_employee", "user_id");
    deleteSeedUserOwnedRows(
        jdbc, tenantId, rows, "common_user.common_user_work_location", "user_id");
    deleteSeedUserOwnedRows(jdbc, tenantId, rows, "common_user.common_user_address", "user_id");
    deleteSeedUserOwnedRows(jdbc, tenantId, rows, "common_user.common_user_contact", "user_id");
    deleteSeedUserOwnedRows(jdbc, tenantId, rows, "common_user.common_user_department", "user_id");
    delete(
        jdbc,
        rows,
        "common_user.common_user(seed-demo-users)",
        "DELETE FROM common_user.common_user WHERE tenant_id = ? AND demo_seed = true",
        tenantId);
  }

  private void deleteSeedUserOwnedRows(
      JdbcTemplate jdbc,
      UUID tenantId,
      Map<String, Integer> rows,
      String table,
      String userColumn) {
    delete(
        jdbc,
        rows,
        table + "(seed-demo-users)",
        "DELETE FROM "
            + table
            + " WHERE tenant_id = ? AND "
            + userColumn
            + " IN (SELECT id FROM common_user.common_user WHERE tenant_id = ? AND demo_seed = true)",
        tenantId,
        tenantId);
  }

  private void flipDemoModeAndStartClock(
      JdbcTemplate jdbc, UUID tenantId, Instant now, Instant trialEndsAt) {
    int affected =
        jdbc.update(
            """
            UPDATE common_tenant.common_tenant
            SET demo_mode = false,
                trial_started_at = ?,
                last_activity_at = ?,
                trial_ends_at = ?,
                updated_at = now(),
                version = version + 1
            WHERE id = ?
              AND demo_mode = true
            """,
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(trialEndsAt),
            tenantId);
    if (affected != 1) {
      throw new PlatformDomainException("Tenant is already real", "TENANT_ALREADY_REAL", 409);
    }
  }

  private void delete(
      JdbcTemplate jdbc, Map<String, Integer> rows, String label, String sql, Object... args) {
    int deleted = jdbc.update(sql, args);
    rows.put(label, deleted);
    log.debug("Tenant go-real purge table deleted: table={}, rows={}", label, deleted);
  }

  private void evictDemoModeCache(UUID tenantId) {
    var cache = cacheManager.getCache(TENANT_DEMOMODE_CACHE);
    if (cache != null) {
      cache.evict(tenantId.toString());
    }
  }

  public record PurgeResult(
      UUID tenantId,
      Map<String, Integer> deletedRows,
      Instant trialStartedAt,
      Instant trialEndsAt) {}

  public record PurgeDemoDataResult(UUID tenantId, Map<String, Integer> deletedRows) {}
}
