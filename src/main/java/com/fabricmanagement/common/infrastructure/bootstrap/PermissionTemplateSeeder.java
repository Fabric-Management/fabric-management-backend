package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
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

  private List<PermissionTemplate> buildDesiredTemplates() {
    List<PermissionTemplate> templates = new ArrayList<>();

    // 1. Wildcards (All departments)
    seedDepartment(
        templates,
        null,
        List.of(
            new String[] {"WORKER", "dashboard", "view", "ORGANIZATION"},
            new String[] {"WORKER", "notifications", "view", "ORGANIZATION"},
            new String[] {"SUPERVISOR", "dashboard", "view", "ORGANIZATION"},
            new String[] {"SUPERVISOR", "notifications", "view", "ORGANIZATION"},
            new String[] {"MANAGER", "dashboard", "view", "ORGANIZATION"},
            new String[] {"MANAGER", "notifications", "view", "ORGANIZATION"},
            new String[] {"MANAGER", "reports", "view", "ORGANIZATION"},
            new String[] {"MANAGER", "finance", "read", "ORGANIZATION"},
            new String[] {"VIEWER", "dashboard", "view", "ORGANIZATION"},
            new String[] {"VIEWER", "notifications", "view", "ORGANIZATION"},
            new String[] {"WORKER", "members", "read", "OWN"},
            new String[] {"WORKER", "settings", "read", "OWN"},
            new String[] {"WORKER", "settings", "write", "OWN"},
            new String[] {"WORKER", "flowboard", "read", "OWN"},
            new String[] {"WORKER", "flowboard", "write", "OWN"},
            new String[] {"SUPERVISOR", "members", "read", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "settings", "read", "OWN"},
            new String[] {"SUPERVISOR", "settings", "write", "OWN"},
            new String[] {"SUPERVISOR", "flowboard", "read", "OWN"},
            new String[] {"SUPERVISOR", "flowboard", "write", "OWN"},
            new String[] {"MANAGER", "members", "read", "DEPARTMENT"},
            new String[] {"MANAGER", "settings", "read", "OWN"},
            new String[] {"MANAGER", "settings", "write", "OWN"},
            new String[] {"MANAGER", "flowboard", "read", "OWN"},
            new String[] {"MANAGER", "flowboard", "write", "OWN"},
            // Department-agnostic quote approval. Previously inserted by migration
            // V20260706120000 (APPROVAL-1); ownership moved here so the seeder alone
            // writes the template tenant. ADMIN is listed for parity with that migration
            // even though PermissionEvaluator short-circuits ADMIN before consulting rows.
            new String[] {"ADMIN", "sales", "approve", "GLOBAL"},
            new String[] {"MANAGER", "sales", "approve", "ORGANIZATION"},
            new String[] {"SUPERVISOR", "sales", "approve", "ORGANIZATION"},
            new String[] {"ADMIN", "quality", "read", "GLOBAL"},
            new String[] {"ADMIN", "quality", "write", "GLOBAL"},
            new String[] {"ADMIN", "quality", "approve", "GLOBAL"},
            new String[] {"ADMIN", "quality", "manage", "GLOBAL"},
            new String[] {"PLATFORM_ADMIN", "quality", "read", "GLOBAL"},
            new String[] {"PLATFORM_ADMIN", "quality", "write", "GLOBAL"},
            new String[] {"PLATFORM_ADMIN", "quality", "approve", "GLOBAL"},
            new String[] {"PLATFORM_ADMIN", "quality", "manage", "GLOBAL"}));

    // 2. SALES
    seedDepartment(
        templates,
        SystemDepartment.SALES.code(),
        List.of(
            new String[] {"WORKER", "sales", "read", "OWN"},
            new String[] {"WORKER", "sales", "write", "OWN"},
            new String[] {"WORKER", "partners", "read", "DEPARTMENT"},
            new String[] {"WORKER", "flowboard", "view", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "sales", "read", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "sales", "write", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "sales", "confirm", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "sales", "ship", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "sales", "delete", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "sales", "approve", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "partners", "read", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "partners", "write", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "flowboard", "view", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "flowboard", "edit", "DEPARTMENT"},
            new String[] {"MANAGER", "sales", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "sales", "write", "DEPARTMENT"},
            new String[] {"MANAGER", "sales", "delete", "DEPARTMENT"},
            new String[] {"MANAGER", "sales", "confirm", "ORGANIZATION"},
            new String[] {"MANAGER", "sales", "ship", "ORGANIZATION"},
            new String[] {"MANAGER", "sales", "cancel", "ORGANIZATION"},
            new String[] {"MANAGER", "sales", "approve", "ORGANIZATION"},
            new String[] {"MANAGER", "partners", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "partners", "write", "DEPARTMENT"},
            new String[] {"MANAGER", "flowboard", "view", "ORGANIZATION"},
            new String[] {"MANAGER", "flowboard", "edit", "DEPARTMENT"},
            new String[] {"MANAGER", "reports", "export", "DEPARTMENT"},
            new String[] {"WORKER", "finance", "read", "OWN"},
            new String[] {"SUPERVISOR", "finance", "read", "DEPARTMENT"},
            new String[] {"WORKER", "colors", "read", "ORGANIZATION"},
            new String[] {"SUPERVISOR", "colors", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "colors", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "finance", "read", "ORGANIZATION"}));

    // 3. Production sub-departments (FIBER, YARN, WEAVING, KNITTING, DYEING, GARMENT)
    //    Each sub-dept gets the same base production permissions.
    List<String[]> productionRules =
        List.of(
            new String[] {"WORKER", "fiber", "read", "OWN"},
            new String[] {"WORKER", "products", "read", "OWN"},
            new String[] {"WORKER", "projects", "read", "OWN"},
            new String[] {"SUPERVISOR", "fiber", "read", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "fiber", "write", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "products", "read", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "products", "write", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "projects", "read", "DEPARTMENT"},
            new String[] {"MANAGER", "fiber", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "fiber", "write", "DEPARTMENT"},
            new String[] {"MANAGER", "fiber", "approve", "DEPARTMENT"},
            new String[] {"MANAGER", "products", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "products", "write", "DEPARTMENT"},
            new String[] {"MANAGER", "projects", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "projects", "write", "DEPARTMENT"},
            new String[] {"MANAGER", "projects", "manage", "DEPARTMENT"},
            // COLOR-RBAC-1: every production department reads tenant colour cards.
            new String[] {"WORKER", "colors", "read", "ORGANIZATION"},
            new String[] {"SUPERVISOR", "colors", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "colors", "read", "ORGANIZATION"});
    for (String deptCode :
        List.of(
            SystemDepartment.FIBER.code(),
            SystemDepartment.YARN.code(),
            SystemDepartment.WEAVING.code(),
            SystemDepartment.KNITTING.code(),
            SystemDepartment.DYEING.code(),
            SystemDepartment.GARMENT.code())) {
      seedDepartment(templates, deptCode, productionRules);
    }

    // COLOR-RBAC-1: only the Dyeing department gets colour write by template; every other
    // production department stays read-only, so this cannot live in the shared productionRules.
    seedDepartment(
        templates,
        SystemDepartment.DYEING.code(),
        List.of(
            new String[] {"SUPERVISOR", "colors", "write", "ORGANIZATION"},
            new String[] {"MANAGER", "colors", "write", "ORGANIZATION"}));

    // 4. QUALITY
    seedDepartment(
        templates,
        SystemDepartment.QUALITY.code(),
        List.of(
            new String[] {"WORKER", "fiber", "read", "OWN"},
            new String[] {"WORKER", "products", "read", "OWN"},
            new String[] {"WORKER", "quality", "read", "ORGANIZATION"},
            new String[] {"WORKER", "quality", "write", "ORGANIZATION"},
            new String[] {"SUPERVISOR", "fiber", "read", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "products", "read", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "products", "write", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "quality", "read", "ORGANIZATION"},
            new String[] {"SUPERVISOR", "quality", "write", "ORGANIZATION"},
            new String[] {"SUPERVISOR", "quality", "approve", "ORGANIZATION"},
            new String[] {"MANAGER", "fiber", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "products", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "products", "write", "DEPARTMENT"},
            new String[] {"MANAGER", "quality", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "quality", "write", "ORGANIZATION"},
            new String[] {"MANAGER", "quality", "approve", "ORGANIZATION"},
            new String[] {"MANAGER", "quality", "manage", "ORGANIZATION"},
            new String[] {"WORKER", "colors", "read", "ORGANIZATION"},
            new String[] {"SUPERVISOR", "colors", "read", "ORGANIZATION"},
            new String[] {"SUPERVISOR", "colors", "write", "ORGANIZATION"},
            new String[] {"MANAGER", "colors", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "colors", "write", "ORGANIZATION"},
            new String[] {"MANAGER", "colors", "approve", "ORGANIZATION"},
            new String[] {"MANAGER", "colors", "manage", "ORGANIZATION"},
            new String[] {"MANAGER", "reports", "export", "DEPARTMENT"}));

    // 5. WAREHOUSE
    seedDepartment(
        templates,
        SystemDepartment.WAREHOUSE.code(),
        List.of(
            new String[] {"WORKER", "products", "read", "OWN"},
            new String[] {"WORKER", "products", "write", "OWN"},
            new String[] {"WORKER", "logistics", "read", "OWN"},
            new String[] {"WORKER", "logistics", "write", "OWN"},
            new String[] {"WORKER", "logistics", "prepare", "OWN"},
            new String[] {"SUPERVISOR", "products", "read", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "products", "write", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "fiber", "read", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "logistics", "read", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "logistics", "write", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "logistics", "prepare", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "logistics", "ship", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "logistics", "deliver", "DEPARTMENT"},
            new String[] {"MANAGER", "products", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "products", "write", "DEPARTMENT"},
            new String[] {"MANAGER", "fiber", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "logistics", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "logistics", "write", "DEPARTMENT"},
            new String[] {"MANAGER", "logistics", "prepare", "DEPARTMENT"},
            new String[] {"MANAGER", "logistics", "ship", "ORGANIZATION"},
            new String[] {"MANAGER", "logistics", "deliver", "ORGANIZATION"},
            new String[] {"WORKER", "colors", "read", "ORGANIZATION"},
            new String[] {"SUPERVISOR", "colors", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "colors", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "logistics", "cancel", "ORGANIZATION"}));

    // 6. FINANCE
    seedDepartment(
        templates,
        SystemDepartment.FINANCE.code(),
        List.of(
            new String[] {"WORKER", "sales", "read", "ORGANIZATION"},
            new String[] {"WORKER", "reports", "view", "OWN"},
            new String[] {"SUPERVISOR", "sales", "read", "ORGANIZATION"},
            new String[] {"SUPERVISOR", "partners", "read", "ORGANIZATION"},
            new String[] {"SUPERVISOR", "reports", "view", "DEPARTMENT"},
            new String[] {"MANAGER", "sales", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "partners", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "partners", "write", "DEPARTMENT"},
            new String[] {"MANAGER", "reports", "view", "ORGANIZATION"},
            new String[] {"MANAGER", "reports", "export", "ORGANIZATION"},
            new String[] {"WORKER", "members", "read", "ORGANIZATION"},
            new String[] {"SUPERVISOR", "members", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "members", "read", "ORGANIZATION"},
            new String[] {"WORKER", "finance", "read", "OWN"},
            new String[] {"WORKER", "finance", "write", "OWN"},
            new String[] {"SUPERVISOR", "finance", "read", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "finance", "write", "DEPARTMENT"},
            new String[] {"MANAGER", "finance", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "finance", "write", "DEPARTMENT"},
            new String[] {"MANAGER", "finance", "manage", "ORGANIZATION"}));

    // 7. HR
    seedDepartment(
        templates,
        SystemDepartment.HR.code(),
        List.of(
            new String[] {"WORKER", "settings", "view", "OWN"},
            new String[] {"SUPERVISOR", "settings", "view", "DEPARTMENT"},
            new String[] {"MANAGER", "settings", "view", "ORGANIZATION"},
            new String[] {"MANAGER", "settings", "manage", "DEPARTMENT"},
            new String[] {"WORKER", "members", "read", "ORGANIZATION"},
            new String[] {"SUPERVISOR", "members", "read", "ORGANIZATION"},
            new String[] {"SUPERVISOR", "members", "write", "ORGANIZATION"},
            new String[] {"MANAGER", "members", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "members", "write", "ORGANIZATION"},
            new String[] {"MANAGER", "members", "manage", "ORGANIZATION"}));

    // 8. PROCUREMENT
    seedDepartment(
        templates,
        SystemDepartment.PROCUREMENT.code(),
        List.of(
            new String[] {"WORKER", "procurement", "read", "OWN"},
            new String[] {"WORKER", "procurement", "write", "OWN"},
            new String[] {"WORKER", "partners", "read", "DEPARTMENT"},
            new String[] {"WORKER", "products", "read", "OWN"},
            new String[] {"SUPERVISOR", "procurement", "read", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "procurement", "write", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "partners", "read", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "partners", "write", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "products", "read", "DEPARTMENT"},
            new String[] {"SUPERVISOR", "products", "write", "DEPARTMENT"},
            new String[] {"MANAGER", "procurement", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "procurement", "write", "DEPARTMENT"},
            new String[] {"MANAGER", "partners", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "partners", "write", "DEPARTMENT"},
            new String[] {"MANAGER", "products", "read", "ORGANIZATION"},
            new String[] {"WORKER", "colors", "read", "ORGANIZATION"},
            new String[] {"SUPERVISOR", "colors", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "colors", "read", "ORGANIZATION"},
            new String[] {"MANAGER", "products", "write", "DEPARTMENT"}));

    // 9. PARTNER roles — visible only in partner invitation UIs
    // Partners do not have departments, so wildcard (null) is used here.
    // This is safe because these templates only match specific partner role codes.
    seedDepartment(
        templates,
        null,
        List.of(
            new String[] {"PARTNER_OWNER", "dashboard", "view", "ORGANIZATION"},
            new String[] {"PARTNER_OWNER", "sales", "read", "ORGANIZATION"},
            new String[] {"PARTNER_OWNER", "partners", "read", "OWN"},
            new String[] {"PARTNER_ACCOUNTANT", "dashboard", "view", "OWN"},
            new String[] {"PARTNER_ACCOUNTANT", "sales", "read", "ORGANIZATION"},
            new String[] {"PARTNER_BUYER", "sales", "read", "OWN"},
            new String[] {"PARTNER_BUYER", "sales", "write", "OWN"},
            new String[] {"PARTNER_VIEWER", "dashboard", "view", "OWN"}));

    // Note: MANAGEMENT department removed — ADMIN role provides cross-org access globally.

    return templates;
  }

  private void seedDepartment(
      List<PermissionTemplate> templatesToSave, String departmentCode, List<String[]> rules) {
    for (String[] rule : rules) {
      PermissionTemplate template =
          PermissionTemplate.builder()
              .roleCode(rule[0])
              .departmentCode(departmentCode)
              .resource(rule[1])
              .action(rule[2])
              .dataScope(DataScope.valueOf(rule[3]))
              .build();

      // Explicitly setting System Tenant ID to establish them as System Default templates
      template.setTenantId(TenantContext.TEMPLATE_TENANT_ID);
      template.setIsActive(true);

      templatesToSave.add(template);
    }
  }
}
