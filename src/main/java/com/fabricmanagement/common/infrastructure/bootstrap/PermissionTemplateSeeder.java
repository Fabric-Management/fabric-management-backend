package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import com.fabricmanagement.platform.user.infra.repository.PermissionTemplateRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds the predefined System Default Permission Templates into the database. This ensures that
 * immediately upon instantiation, standard functional mapping matrices are accessible for
 * authorization evaluation globally.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionTemplateSeeder implements DataSeeder {

  private final PermissionTemplateRepository permissionTemplateRepository;
  private final TransactionTemplate transactionTemplate;

  @Override
  public boolean isSeeded() {
    // Idempotency: verify if ANY system default templates (tenant_id IS NULL) are already seeded
    return permissionTemplateRepository.existsByTenantIdIsNull();
  }

  @Override
  public void seed() {
    transactionTemplate.executeWithoutResult(
        status -> {
          log.info("Seeding Permission Templates (System Defaults)...");
          List<PermissionTemplate> templatesToSave = new ArrayList<>();

          // 1. Wildcards (All departments)
          seedDepartment(
              templatesToSave,
              null,
              List.of(
                  new String[] {"WORKER", "dashboard", "view", "ORGANIZATION"},
                  new String[] {"WORKER", "notifications", "view", "ORGANIZATION"},
                  new String[] {"SUPERVISOR", "dashboard", "view", "ORGANIZATION"},
                  new String[] {"SUPERVISOR", "notifications", "view", "ORGANIZATION"},
                  new String[] {"MANAGER", "dashboard", "view", "ORGANIZATION"},
                  new String[] {"MANAGER", "notifications", "view", "ORGANIZATION"},
                  new String[] {"MANAGER", "reports", "view", "ORGANIZATION"},
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
                  new String[] {"MANAGER", "flowboard", "write", "OWN"}));

          // 2. SALES_MARKETING
          seedDepartment(
              templatesToSave,
              "SALES_MARKETING",
              List.of(
                  new String[] {"WORKER", "sales", "read", "OWN"},
                  new String[] {"WORKER", "sales", "write", "OWN"},
                  new String[] {"WORKER", "partners", "read", "DEPARTMENT"},
                  new String[] {"WORKER", "flowboard", "view", "DEPARTMENT"},
                  new String[] {"SUPERVISOR", "sales", "read", "DEPARTMENT"},
                  new String[] {"SUPERVISOR", "sales", "write", "DEPARTMENT"},
                  new String[] {"SUPERVISOR", "partners", "read", "DEPARTMENT"},
                  new String[] {"SUPERVISOR", "partners", "write", "DEPARTMENT"},
                  new String[] {"SUPERVISOR", "flowboard", "view", "DEPARTMENT"},
                  new String[] {"SUPERVISOR", "flowboard", "edit", "DEPARTMENT"},
                  new String[] {"MANAGER", "sales", "read", "ORGANIZATION"},
                  new String[] {"MANAGER", "sales", "write", "DEPARTMENT"},
                  new String[] {"MANAGER", "sales", "delete", "DEPARTMENT"},
                  new String[] {"MANAGER", "partners", "read", "ORGANIZATION"},
                  new String[] {"MANAGER", "partners", "write", "DEPARTMENT"},
                  new String[] {"MANAGER", "flowboard", "view", "ORGANIZATION"},
                  new String[] {"MANAGER", "flowboard", "edit", "DEPARTMENT"},
                  new String[] {"MANAGER", "reports", "export", "DEPARTMENT"}));

          // 3. PRODUCTION
          seedDepartment(
              templatesToSave,
              "PRODUCTION",
              List.of(
                  new String[] {"WORKER", "fiber", "read", "OWN"},
                  new String[] {"WORKER", "materials", "read", "OWN"},
                  new String[] {"WORKER", "projects", "read", "OWN"},
                  new String[] {"SUPERVISOR", "fiber", "read", "DEPARTMENT"},
                  new String[] {"SUPERVISOR", "fiber", "write", "DEPARTMENT"},
                  new String[] {"SUPERVISOR", "materials", "read", "DEPARTMENT"},
                  new String[] {"SUPERVISOR", "materials", "write", "DEPARTMENT"},
                  new String[] {"SUPERVISOR", "projects", "read", "DEPARTMENT"},
                  new String[] {"MANAGER", "fiber", "read", "ORGANIZATION"},
                  new String[] {"MANAGER", "fiber", "write", "DEPARTMENT"},
                  new String[] {"MANAGER", "fiber", "approve", "DEPARTMENT"},
                  new String[] {"MANAGER", "materials", "read", "ORGANIZATION"},
                  new String[] {"MANAGER", "materials", "write", "DEPARTMENT"},
                  new String[] {"MANAGER", "projects", "read", "ORGANIZATION"},
                  new String[] {"MANAGER", "projects", "write", "DEPARTMENT"},
                  new String[] {"MANAGER", "projects", "manage", "DEPARTMENT"}));

          // 4. QUALITY_CONTROL
          seedDepartment(
              templatesToSave,
              "QUALITY_CONTROL",
              List.of(
                  new String[] {"WORKER", "fiber", "read", "OWN"},
                  new String[] {"WORKER", "materials", "read", "OWN"},
                  new String[] {"SUPERVISOR", "fiber", "read", "DEPARTMENT"},
                  new String[] {"SUPERVISOR", "materials", "read", "DEPARTMENT"},
                  new String[] {"SUPERVISOR", "materials", "write", "DEPARTMENT"},
                  new String[] {"MANAGER", "fiber", "read", "ORGANIZATION"},
                  new String[] {"MANAGER", "materials", "read", "ORGANIZATION"},
                  new String[] {"MANAGER", "materials", "write", "DEPARTMENT"},
                  new String[] {"MANAGER", "reports", "export", "DEPARTMENT"}));

          // 5. WAREHOUSE_LOGISTICS
          seedDepartment(
              templatesToSave,
              "WAREHOUSE_LOGISTICS",
              List.of(
                  new String[] {"WORKER", "materials", "read", "OWN"},
                  new String[] {"WORKER", "materials", "write", "OWN"},
                  new String[] {"SUPERVISOR", "materials", "read", "DEPARTMENT"},
                  new String[] {"SUPERVISOR", "materials", "write", "DEPARTMENT"},
                  new String[] {"SUPERVISOR", "fiber", "read", "DEPARTMENT"},
                  new String[] {"MANAGER", "materials", "read", "ORGANIZATION"},
                  new String[] {"MANAGER", "materials", "write", "DEPARTMENT"},
                  new String[] {"MANAGER", "fiber", "read", "ORGANIZATION"}));

          // 6. FINANCE_ACCOUNTING
          seedDepartment(
              templatesToSave,
              "FINANCE_ACCOUNTING",
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
                  new String[] {"MANAGER", "members", "read", "ORGANIZATION"}));

          // 7. HUMAN_RESOURCES
          seedDepartment(
              templatesToSave,
              "HUMAN_RESOURCES",
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
              templatesToSave,
              "PROCUREMENT",
              List.of(
                  new String[] {"WORKER", "materials", "read", "OWN"},
                  new String[] {"WORKER", "materials", "write", "OWN"},
                  new String[] {"WORKER", "partners", "read", "DEPARTMENT"},
                  new String[] {"SUPERVISOR", "materials", "read", "DEPARTMENT"},
                  new String[] {"SUPERVISOR", "materials", "write", "DEPARTMENT"},
                  new String[] {"SUPERVISOR", "partners", "read", "DEPARTMENT"},
                  new String[] {"SUPERVISOR", "partners", "write", "DEPARTMENT"},
                  new String[] {"MANAGER", "materials", "read", "ORGANIZATION"},
                  new String[] {"MANAGER", "materials", "write", "DEPARTMENT"},
                  new String[] {"MANAGER", "partners", "read", "ORGANIZATION"},
                  new String[] {"MANAGER", "partners", "write", "DEPARTMENT"}));

          // 9. MANAGEMENT
          seedDepartment(
              templatesToSave,
              "MANAGEMENT",
              List.of(
                  new String[] {"WORKER", "reports", "view", "OWN"},
                  new String[] {"WORKER", "flowboard", "view", "DEPARTMENT"},
                  new String[] {"SUPERVISOR", "reports", "view", "DEPARTMENT"},
                  new String[] {"SUPERVISOR", "flowboard", "view", "ORGANIZATION"},
                  new String[] {"SUPERVISOR", "sales", "read", "ORGANIZATION"},
                  new String[] {"MANAGER", "reports", "view", "ORGANIZATION"},
                  new String[] {"MANAGER", "reports", "export", "ORGANIZATION"},
                  new String[] {"MANAGER", "flowboard", "view", "ORGANIZATION"},
                  new String[] {"MANAGER", "flowboard", "edit", "ORGANIZATION"},
                  new String[] {"MANAGER", "sales", "read", "ORGANIZATION"},
                  new String[] {"MANAGER", "sales", "write", "DEPARTMENT"},
                  new String[] {"MANAGER", "projects", "read", "ORGANIZATION"},
                  new String[] {"MANAGER", "projects", "write", "DEPARTMENT"},
                  new String[] {"MANAGER", "settings", "view", "ORGANIZATION"},
                  new String[] {"WORKER", "members", "read", "ORGANIZATION"},
                  new String[] {"SUPERVISOR", "members", "read", "ORGANIZATION"},
                  new String[] {"SUPERVISOR", "members", "write", "ORGANIZATION"},
                  new String[] {"MANAGER", "members", "read", "ORGANIZATION"},
                  new String[] {"MANAGER", "members", "write", "ORGANIZATION"},
                  new String[] {"MANAGER", "members", "manage", "ORGANIZATION"}));

          permissionTemplateRepository.saveAll(templatesToSave);
          log.info("Successfully seeded {} Permission Templates", templatesToSave.size());
        });
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

      // Explicitly setting TenantID to null to establish them as System Default templates
      template.setTenantId(null);
      template.setIsActive(true);

      templatesToSave.add(template);
    }
  }

  /**
   * Executes critically late in the seeding lifecycle (after Tenant, Role, Organization, and
   * Depts).
   */
  @Override
  public int getOrder() {
    return 50;
  }
}
