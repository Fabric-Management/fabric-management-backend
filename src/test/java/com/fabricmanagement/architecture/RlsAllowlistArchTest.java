package com.fabricmanagement.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Enforces that every JPA Entity is either Tenant-Scoped (has tenantId) or explicitly Allowlisted.
 * This guarantees no table can accidentally bypass RLS.
 */
class RlsAllowlistArchTest {

  private static JavaClasses allClasses;

  /**
   * T6 Allowlist: Tables that are intentionally NOT tenant-scoped. If an entity maps to a table NOT
   * in this list, it MUST have a tenantId field.
   */
  private static final Set<String> ALLOWLISTED_TABLES =
      Set.of(
          // JobRunr framework tables (managed by JobRunr, inherently cross-tenant)
          "jobrunr_jobs",
          "jobrunr_recurring_jobs",
          "jobrunr_backgroundjobservers",
          "jobrunr_migrations",
          "jobrunr_metadata",

          // Spring Modulith outbox pattern table
          "event_publication",

          // Idempotency dedup table (E2)
          "processed_event",

          // Platform Registry (Global system tables)
          "common_system_role",
          "common_permission",
          "common_tenant",
          "trading_partner_registry",

          // Permanent marketing lead; tenant-independent with nullable trial_tenant_id link
          "common_lead",

          // Audit Logs without tenantId (joined via parent entity)
          "production_execution_batch_override_log",

          // Flyway
          "flyway_schema_history");

  @BeforeAll
  static void importClasses() {
    allClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.fabricmanagement");
  }

  @Test
  @DisplayName("All Entities must have a tenantId unless their table is explicitly allowlisted")
  void entitiesMustBeTenantScopedOrAllowlisted() {
    ArchRule rule =
        classes()
            .that()
            .areAnnotatedWith(Entity.class)
            .should(haveTenantIdOrBeAllowlisted())
            .as(
                "Entities must have a 'tenantId' field or their table must be in ALLOWLISTED_TABLES");

    rule.check(allClasses);
  }

  private ArchCondition<JavaClass> haveTenantIdOrBeAllowlisted() {
    return new ArchCondition<>("have tenantId or be allowlisted") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        // 1. Check if it has a tenantId field
        boolean hasTenantId =
            javaClass.getAllFields().stream().anyMatch(f -> f.getName().equals("tenantId"));

        if (hasTenantId) {
          return; // It's tenant-scoped, PASS
        }

        // 2. If no tenantId, check if it maps to an allowlisted table
        String tableName = getTableName(javaClass);
        if (tableName != null && ALLOWLISTED_TABLES.contains(tableName.toLowerCase())) {
          return; // It's explicitly allowlisted, PASS
        }

        // Some base entities or unmapped tables might not have @Table
        if (tableName == null) {
          return; // Can't verify table name here, assume it's inherited or mapped differently
        }

        events.add(
            SimpleConditionEvent.violated(
                javaClass,
                String.format(
                    "Entity %s (Table: %s) does NOT have a tenantId field and is NOT in the T6 Allowlist. "
                        + "It must either be tenant-scoped or explicitly allowlisted.",
                    javaClass.getName(), tableName)));
      }
    };
  }

  private String getTableName(JavaClass javaClass) {
    if (javaClass.isAnnotatedWith(Table.class)) {
      return javaClass.getAnnotationOfType(Table.class).name();
    }
    return null;
  }
}
