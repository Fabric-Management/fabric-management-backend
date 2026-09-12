package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.platform.organization.domain.SystemDepartment;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import com.fabricmanagement.platform.user.infra.repository.PermissionTemplateRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Owns the contents of the template tenant's permission_template rows.
 *
 * <p>Every tenant is created by cloning these rows, so this class defines what a non-admin user is
 * allowed to do anywhere in the product. It is the single writer: no migration and no other runner
 * may insert rows for {@link TenantContext#TEMPLATE_TENANT_ID}.
 *
 * <p><b>Differential by design.</b> {@link #seed()} inserts only the rows that are missing and is
 * safe to run on every boot. It deliberately has no "already seeded?" short-circuit: the previous
 * one asked whether the table held <i>any</i> row, so when migration {@code
 * V20260706120000__quote_send_requests.sql} inserted three {@code sales:approve} rows the seeder
 * concluded its work was done and skipped the entire catalogue. Every tenant created after that
 * migration received 10 templates instead of ~150, and every non-ADMIN user was denied everywhere.
 * See {@code docs/platform/tickets/PERM-SEED-1-permission-template-seed-poisoning.md}.
 *
 * <p>Invoked by {@link PermissionTemplateBackfillRunner} in all profiles — not through {@code
 * DevSeedDataRunner}, which is confined to local/dev/docker and would leave production's template
 * tenant unseeded.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionTemplateSeeder {

  private final PermissionTemplateRepository permissionTemplateRepository;
  private final TransactionTemplate transactionTemplate;

  /** Effective identity of a template row, mirroring {@code uq_permission_template_effective}. */
  private record TemplateKey(
      String roleCode, String departmentCode, String resource, String action) {

    static final String ALL_DEPARTMENTS = "__ALL__";

    static TemplateKey of(PermissionTemplate template) {
      return new TemplateKey(
          template.getRoleCode(),
          template.getDepartmentCode() == null ? ALL_DEPARTMENTS : template.getDepartmentCode(),
          template.getResource(),
          template.getAction());
    }
  }

  /**
   * Inserts the templates that the template tenant is missing. Rows written by anyone else are left
   * untouched — they cannot be trusted as evidence that this seeder has run.
   */
  public void seed() {
    TenantContext.executeInTenantContext(
        TenantContext.TEMPLATE_TENANT_ID,
        () -> {
          transactionTemplate.executeWithoutResult(
              status -> {
                List<PermissionTemplate> desired = buildDesiredTemplates();
                rejectConflictingDuplicates(desired);

                Set<TemplateKey> existing = new HashSet<>();
                for (PermissionTemplate stored :
                    permissionTemplateRepository.findByTenantId(TenantContext.TEMPLATE_TENANT_ID)) {
                  existing.add(TemplateKey.of(stored));
                }

                List<PermissionTemplate> missing =
                    desired.stream().filter(t -> !existing.contains(TemplateKey.of(t))).toList();

                if (missing.isEmpty()) {
                  log.info(
                      "Permission templates complete: {} desired, all present in template tenant.",
                      desired.size());
                  return;
                }

                permissionTemplateRepository.saveAll(missing);
                log.info(
                    "Seeded {} missing permission templates ({} desired, {} already present).",
                    missing.size(),
                    desired.size(),
                    existing.size());
              });
          return null;
        });
  }

  /**
   * Two desired rows that differ only in {@code dataScope} collide on the unique index, which does
   * not include that column. Such a pair is a bug in the catalogue below, and the ambiguity ("which
   * scope wins?") must be resolved by a human rather than by insertion order. Fail loudly at boot.
   */
  private void rejectConflictingDuplicates(List<PermissionTemplate> desired) {
    Map<TemplateKey, DataScope> seen = new HashMap<>();
    for (PermissionTemplate template : desired) {
      TemplateKey key = TemplateKey.of(template);
      DataScope previous = seen.putIfAbsent(key, template.getDataScope());
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate permission template %s: declared with both %s and %s. The unique index"
                    .formatted(key, previous, template.getDataScope())
                + " uq_permission_template_effective ignores data_scope, so only one may be"
                + " declared. Fix PermissionTemplateSeeder.");
      }
    }
  }

  public List<PermissionTemplate> buildDesiredTemplates() {
    List<PermissionTemplate> templates = new ArrayList<>();

    // 1. Wildcards (All departments)
    seedDepartment(
        templates,
        null,
        List.of(
            new GrantRule("MANAGER", PermissionKey.FINANCE_READ, DataScope.ORGANIZATION),
            new GrantRule("WORKER", PermissionKey.MEMBERS_READ, DataScope.OWN),
            new GrantRule("WORKER", PermissionKey.SETTINGS_READ, DataScope.OWN),
            new GrantRule("WORKER", PermissionKey.FLOWBOARD_READ, DataScope.OWN),
            new GrantRule("WORKER", PermissionKey.FLOWBOARD_WRITE, DataScope.OWN),
            new GrantRule("SUPERVISOR", PermissionKey.MEMBERS_READ, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.SETTINGS_READ, DataScope.OWN),
            new GrantRule("SUPERVISOR", PermissionKey.FLOWBOARD_READ, DataScope.OWN),
            new GrantRule("SUPERVISOR", PermissionKey.FLOWBOARD_WRITE, DataScope.OWN),
            new GrantRule("MANAGER", PermissionKey.MEMBERS_READ, DataScope.DEPARTMENT),
            new GrantRule("MANAGER", PermissionKey.SETTINGS_READ, DataScope.OWN),
            new GrantRule("MANAGER", PermissionKey.FLOWBOARD_READ, DataScope.OWN),
            new GrantRule("MANAGER", PermissionKey.FLOWBOARD_WRITE, DataScope.OWN),
            // Department-agnostic quote approval. Previously inserted by migration
            // V20260706120000 (APPROVAL-1); ownership moved here so the seeder alone
            // writes the template tenant. ADMIN is listed for parity with that migration
            // even though PermissionEvaluator short-circuits ADMIN before consulting rows.
            new GrantRule("ADMIN", PermissionKey.SALES_APPROVE, DataScope.GLOBAL),
            new GrantRule("ADMIN", PermissionKey.SALES_ASSIGN_OWNER, DataScope.GLOBAL),
            new GrantRule("MANAGER", PermissionKey.SALES_APPROVE, DataScope.ORGANIZATION),
            new GrantRule("SUPERVISOR", PermissionKey.SALES_APPROVE, DataScope.ORGANIZATION),
            new GrantRule("ADMIN", PermissionKey.QUALITY_READ, DataScope.GLOBAL),
            new GrantRule("ADMIN", PermissionKey.QUALITY_WRITE, DataScope.GLOBAL),
            new GrantRule("ADMIN", PermissionKey.QUALITY_APPROVE, DataScope.GLOBAL),
            new GrantRule("ADMIN", PermissionKey.QUALITY_MANAGE, DataScope.GLOBAL),
            new GrantRule("PLATFORM_ADMIN", PermissionKey.QUALITY_READ, DataScope.GLOBAL),
            new GrantRule("PLATFORM_ADMIN", PermissionKey.QUALITY_WRITE, DataScope.GLOBAL),
            new GrantRule("PLATFORM_ADMIN", PermissionKey.QUALITY_APPROVE, DataScope.GLOBAL),
            new GrantRule("PLATFORM_ADMIN", PermissionKey.QUALITY_MANAGE, DataScope.GLOBAL)));

    // 2. SALES
    seedDepartment(
        templates,
        SystemDepartment.SALES.code(),
        List.of(
            new GrantRule("WORKER", PermissionKey.SALES_READ, DataScope.OWN),
            new GrantRule("WORKER", PermissionKey.SALES_WRITE, DataScope.OWN),
            new GrantRule("SUPERVISOR", PermissionKey.SALES_READ, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.SALES_WRITE, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.SALES_CONFIRM, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.SALES_SHIP, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.SALES_DELETE, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.SALES_APPROVE, DataScope.DEPARTMENT),
            new GrantRule("MANAGER", PermissionKey.SALES_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.SALES_WRITE, DataScope.DEPARTMENT),
            new GrantRule("MANAGER", PermissionKey.SALES_DELETE, DataScope.DEPARTMENT),
            new GrantRule("MANAGER", PermissionKey.SALES_CONFIRM, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.SALES_SHIP, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.SALES_CANCEL, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.SALES_APPROVE, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.SALES_ASSIGN_OWNER, DataScope.ORGANIZATION),
            new GrantRule("WORKER", PermissionKey.FINANCE_READ, DataScope.OWN),
            new GrantRule("SUPERVISOR", PermissionKey.FINANCE_READ, DataScope.DEPARTMENT),
            new GrantRule("WORKER", PermissionKey.COLORS_READ, DataScope.ORGANIZATION),
            new GrantRule("SUPERVISOR", PermissionKey.COLORS_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.COLORS_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.FINANCE_READ, DataScope.ORGANIZATION)));

    // 3. Production sub-departments (FIBER, YARN, WEAVING, KNITTING, DYEING, GARMENT)
    //    Each sub-dept gets the same base production permissions.
    List<GrantRule> productionRules =
        List.of(
            new GrantRule("WORKER", PermissionKey.FIBER_READ, DataScope.OWN),
            new GrantRule("WORKER", PermissionKey.PRODUCTS_READ, DataScope.OWN),
            new GrantRule("SUPERVISOR", PermissionKey.FIBER_READ, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.FIBER_WRITE, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.PRODUCTS_READ, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.PRODUCTS_WRITE, DataScope.DEPARTMENT),
            new GrantRule("MANAGER", PermissionKey.FIBER_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.FIBER_WRITE, DataScope.DEPARTMENT),
            new GrantRule("MANAGER", PermissionKey.PRODUCTS_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.PRODUCTS_WRITE, DataScope.DEPARTMENT),
            // COLOR-RBAC-1: every production department reads tenant colour cards.
            new GrantRule("WORKER", PermissionKey.COLORS_READ, DataScope.ORGANIZATION),
            new GrantRule("SUPERVISOR", PermissionKey.COLORS_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.COLORS_READ, DataScope.ORGANIZATION));
    List<String> productionDepartments =
        List.of(
            SystemDepartment.FIBER.code(),
            SystemDepartment.YARN.code(),
            SystemDepartment.WEAVING.code(),
            SystemDepartment.KNITTING.code(),
            SystemDepartment.DYEING.code(),
            SystemDepartment.GARMENT.code());
    for (String deptCode : productionDepartments) {
      seedDepartment(templates, deptCode, productionRules);
    }

    // COLOR-RBAC-1: only the Dyeing department gets colour write by template; every other
    // production department stays read-only, so this cannot live in the shared productionRules.
    seedDepartment(
        templates,
        SystemDepartment.DYEING.code(),
        List.of(
            new GrantRule("SUPERVISOR", PermissionKey.COLORS_WRITE, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.COLORS_WRITE, DataScope.ORGANIZATION)));

    // 4. QUALITY
    seedDepartment(
        templates,
        SystemDepartment.QUALITY.code(),
        List.of(
            new GrantRule("WORKER", PermissionKey.FIBER_READ, DataScope.OWN),
            new GrantRule("WORKER", PermissionKey.PRODUCTS_READ, DataScope.OWN),
            new GrantRule("WORKER", PermissionKey.QUALITY_READ, DataScope.ORGANIZATION),
            new GrantRule("WORKER", PermissionKey.QUALITY_WRITE, DataScope.ORGANIZATION),
            new GrantRule("SUPERVISOR", PermissionKey.FIBER_READ, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.PRODUCTS_READ, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.PRODUCTS_WRITE, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.QUALITY_READ, DataScope.ORGANIZATION),
            new GrantRule("SUPERVISOR", PermissionKey.QUALITY_WRITE, DataScope.ORGANIZATION),
            new GrantRule("SUPERVISOR", PermissionKey.QUALITY_APPROVE, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.FIBER_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.PRODUCTS_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.PRODUCTS_WRITE, DataScope.DEPARTMENT),
            new GrantRule("MANAGER", PermissionKey.QUALITY_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.QUALITY_WRITE, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.QUALITY_APPROVE, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.QUALITY_MANAGE, DataScope.ORGANIZATION),
            new GrantRule("WORKER", PermissionKey.COLORS_READ, DataScope.ORGANIZATION),
            new GrantRule("SUPERVISOR", PermissionKey.COLORS_READ, DataScope.ORGANIZATION),
            new GrantRule("SUPERVISOR", PermissionKey.COLORS_WRITE, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.COLORS_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.COLORS_WRITE, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.COLORS_APPROVE, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.COLORS_MANAGE, DataScope.ORGANIZATION)));

    // 5. WAREHOUSE
    seedDepartment(
        templates,
        SystemDepartment.WAREHOUSE.code(),
        List.of(
            new GrantRule("WORKER", PermissionKey.PRODUCTS_READ, DataScope.OWN),
            new GrantRule("WORKER", PermissionKey.PRODUCTS_WRITE, DataScope.OWN),
            new GrantRule("WORKER", PermissionKey.LOGISTICS_READ, DataScope.OWN),
            new GrantRule("WORKER", PermissionKey.LOGISTICS_WRITE, DataScope.OWN),
            new GrantRule("WORKER", PermissionKey.LOGISTICS_PREPARE, DataScope.OWN),
            new GrantRule("SUPERVISOR", PermissionKey.PRODUCTS_READ, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.PRODUCTS_WRITE, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.FIBER_READ, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.LOGISTICS_READ, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.LOGISTICS_WRITE, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.LOGISTICS_PREPARE, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.LOGISTICS_SHIP, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.LOGISTICS_DELIVER, DataScope.DEPARTMENT),
            new GrantRule("MANAGER", PermissionKey.PRODUCTS_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.PRODUCTS_WRITE, DataScope.DEPARTMENT),
            new GrantRule("MANAGER", PermissionKey.FIBER_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.LOGISTICS_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.LOGISTICS_WRITE, DataScope.DEPARTMENT),
            new GrantRule("MANAGER", PermissionKey.LOGISTICS_PREPARE, DataScope.DEPARTMENT),
            new GrantRule("MANAGER", PermissionKey.LOGISTICS_SHIP, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.LOGISTICS_DELIVER, DataScope.ORGANIZATION),
            new GrantRule("WORKER", PermissionKey.COLORS_READ, DataScope.ORGANIZATION),
            new GrantRule("SUPERVISOR", PermissionKey.COLORS_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.COLORS_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.LOGISTICS_CANCEL, DataScope.ORGANIZATION)));

    // 6. FINANCE
    seedDepartment(
        templates,
        SystemDepartment.FINANCE.code(),
        List.of(
            new GrantRule("WORKER", PermissionKey.SALES_READ, DataScope.ORGANIZATION),
            new GrantRule("SUPERVISOR", PermissionKey.SALES_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.SALES_READ, DataScope.ORGANIZATION),
            new GrantRule("WORKER", PermissionKey.MEMBERS_READ, DataScope.ORGANIZATION),
            new GrantRule("SUPERVISOR", PermissionKey.MEMBERS_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.MEMBERS_READ, DataScope.ORGANIZATION),
            new GrantRule("WORKER", PermissionKey.FINANCE_READ, DataScope.OWN),
            new GrantRule("WORKER", PermissionKey.FINANCE_WRITE, DataScope.OWN),
            new GrantRule("SUPERVISOR", PermissionKey.FINANCE_READ, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.FINANCE_WRITE, DataScope.DEPARTMENT),
            new GrantRule("MANAGER", PermissionKey.FINANCE_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.FINANCE_WRITE, DataScope.DEPARTMENT),
            new GrantRule("MANAGER", PermissionKey.FINANCE_MANAGE, DataScope.ORGANIZATION)));

    // 7. HR
    seedDepartment(
        templates,
        SystemDepartment.HR.code(),
        List.of(
            new GrantRule("WORKER", PermissionKey.MEMBERS_READ, DataScope.ORGANIZATION),
            new GrantRule("SUPERVISOR", PermissionKey.MEMBERS_READ, DataScope.ORGANIZATION),
            new GrantRule("SUPERVISOR", PermissionKey.MEMBERS_WRITE, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.MEMBERS_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.MEMBERS_WRITE, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.MEMBERS_MANAGE, DataScope.ORGANIZATION)));

    // 8. PROCUREMENT
    seedDepartment(
        templates,
        SystemDepartment.PROCUREMENT.code(),
        List.of(
            new GrantRule("WORKER", PermissionKey.PROCUREMENT_READ, DataScope.OWN),
            new GrantRule("WORKER", PermissionKey.PROCUREMENT_WRITE, DataScope.OWN),
            new GrantRule("WORKER", PermissionKey.PRODUCTS_READ, DataScope.OWN),
            new GrantRule("SUPERVISOR", PermissionKey.PROCUREMENT_READ, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.PROCUREMENT_WRITE, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.PRODUCTS_READ, DataScope.DEPARTMENT),
            new GrantRule("SUPERVISOR", PermissionKey.PRODUCTS_WRITE, DataScope.DEPARTMENT),
            new GrantRule("MANAGER", PermissionKey.PROCUREMENT_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.PROCUREMENT_WRITE, DataScope.DEPARTMENT),
            new GrantRule("MANAGER", PermissionKey.PRODUCTS_READ, DataScope.ORGANIZATION),
            new GrantRule("WORKER", PermissionKey.COLORS_READ, DataScope.ORGANIZATION),
            new GrantRule("SUPERVISOR", PermissionKey.COLORS_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.COLORS_READ, DataScope.ORGANIZATION),
            new GrantRule("MANAGER", PermissionKey.PRODUCTS_WRITE, DataScope.DEPARTMENT)));

    // 9. PARTNER roles — visible only in partner invitation UIs
    // Partners do not have departments, so wildcard (null) is used here.
    // This is safe because these templates only match specific partner role codes.
    seedDepartment(
        templates,
        null,
        List.of(
            new GrantRule("PARTNER_OWNER", PermissionKey.SALES_READ, DataScope.ORGANIZATION),
            new GrantRule("PARTNER_ACCOUNTANT", PermissionKey.SALES_READ, DataScope.ORGANIZATION),
            new GrantRule("PARTNER_BUYER", PermissionKey.SALES_READ, DataScope.OWN),
            new GrantRule("PARTNER_BUYER", PermissionKey.SALES_WRITE, DataScope.OWN)));

    // Note: MANAGEMENT department removed — ADMIN role provides cross-org access globally.

    // Mirror named read/write pairs only; future fiber actions require an explicit grant decision.
    mirrorResourceActions(
        templates,
        Map.of(
            PermissionKey.FIBER_READ, PermissionKey.YARN_READ,
            PermissionKey.FIBER_WRITE, PermissionKey.YARN_WRITE),
        template -> true);

    // PERM-CAT-1: preserve every existing row and derive only the six approved missing pairs.
    mirrorResourceActions(
        templates,
        Map.of(
            PermissionKey.FIBER_READ, PermissionKey.PRODUCTION_READ,
            PermissionKey.FIBER_WRITE, PermissionKey.PRODUCTION_WRITE),
        template ->
            template.getDepartmentCode() != null
                && productionDepartments.contains(template.getDepartmentCode()));
    mirrorResourceActions(
        templates,
        Map.of(
            PermissionKey.FINANCE_READ, PermissionKey.COSTING_READ,
            PermissionKey.FINANCE_WRITE, PermissionKey.COSTING_WRITE,
            PermissionKey.FINANCE_MANAGE, PermissionKey.COSTING_MANAGE),
        template -> SystemDepartment.FINANCE.code().equals(template.getDepartmentCode()));
    mirrorResourceActions(
        templates,
        Map.of(PermissionKey.LOGISTICS_CANCEL, PermissionKey.LOGISTICS_DELETE),
        template -> SystemDepartment.WAREHOUSE.code().equals(template.getDepartmentCode()));

    return templates;
  }

  /** Derives approved GrantRules from existing rows without inventing a second policy matrix. */
  private void mirrorResourceActions(
      List<PermissionTemplate> templates,
      Map<PermissionKey, PermissionKey> pairs,
      Predicate<PermissionTemplate> departmentFilter) {
    List<PermissionTemplate> derived =
        templates.stream()
            .filter(departmentFilter)
            .filter(
                source ->
                    pairs.containsKey(
                        PermissionKey.of(source.getResource(), source.getAction()).orElseThrow()))
            .map(
                source -> {
                  PermissionKey targetKey =
                      pairs.get(
                          PermissionKey.of(source.getResource(), source.getAction()).orElseThrow());
                  GrantRule target =
                      new GrantRule(source.getRoleCode(), targetKey, source.getDataScope());
                  return toTemplate(source.getDepartmentCode(), target);
                })
            .toList();
    templates.addAll(derived);
  }

  private void seedDepartment(
      List<PermissionTemplate> templatesToSave, String departmentCode, List<GrantRule> rules) {
    rules.stream().map(rule -> toTemplate(departmentCode, rule)).forEach(templatesToSave::add);
  }

  private PermissionTemplate toTemplate(String departmentCode, GrantRule rule) {
    PermissionTemplate template =
        PermissionTemplate.builder()
            .roleCode(rule.roleCode())
            .departmentCode(departmentCode)
            .resource(rule.key().resource())
            .action(rule.key().action())
            .dataScope(rule.scope())
            .build();
    template.setTenantId(TenantContext.TEMPLATE_TENANT_ID);
    template.setIsActive(true);
    return template;
  }
}
