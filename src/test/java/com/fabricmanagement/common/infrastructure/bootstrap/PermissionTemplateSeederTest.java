package com.fabricmanagement.common.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import com.fabricmanagement.platform.user.infra.repository.PermissionTemplateRepository;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Regression cover for PERM-SEED-1.
 *
 * <p>The seeder used to skip its entire catalogue if the template tenant held <i>any</i> row.
 * Migration {@code V20260706120000} inserted three {@code sales:approve} rows, so from 2026-07-06
 * every tenant was cloned from a 10-row template and every non-ADMIN user was denied everywhere.
 * Nothing caught it because no test ran the seeder against a non-empty table.
 */
class PermissionTemplateSeederTest {

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("seeds its catalogue even when foreign rows already occupy the template tenant")
  void seedsDespiteForeignRows() {
    // Exactly what V20260706120000 writes, before the seeder ever runs.
    List<PermissionTemplate> preExisting =
        List.of(
            template("ADMIN", null, "sales", "approve", DataScope.GLOBAL),
            template("MANAGER", null, "sales", "approve", DataScope.ORGANIZATION),
            template("SUPERVISOR", null, "sales", "approve", DataScope.ORGANIZATION));

    List<PermissionTemplate> saved = new ArrayList<>();
    PermissionTemplateSeeder seeder = seeder(preExisting, saved);

    seeder.seed();

    assertThat(saved)
        .as("the two permissions whose absence locked every non-admin user out")
        .extracting(
            PermissionTemplate::getRoleCode,
            PermissionTemplate::getDepartmentCode,
            PermissionTemplate::getResource,
            PermissionTemplate::getAction)
        .contains(
            Tuple.tuple("WORKER", "SALES", "sales", "read"),
            Tuple.tuple("WORKER", "FIBER", "fiber", "read"));
    assertThat(saved).hasSizeGreaterThan(100);
  }

  @Test
  @DisplayName("does not re-insert rows that already exist, whoever wrote them")
  void skipsRowsAlreadyPresent() {
    List<PermissionTemplate> preExisting =
        List.of(
            template("ADMIN", null, "sales", "approve", DataScope.GLOBAL),
            template("MANAGER", null, "sales", "approve", DataScope.ORGANIZATION),
            template("SUPERVISOR", null, "sales", "approve", DataScope.ORGANIZATION),
            template("WORKER", "SALES", "sales", "read", DataScope.OWN));

    List<PermissionTemplate> saved = new ArrayList<>();
    seeder(preExisting, saved).seed();

    assertThat(saved)
        .as("no duplicate of a pre-existing row — uq_permission_template_effective would reject it")
        .noneMatch(
            t ->
                "WORKER".equals(t.getRoleCode())
                    && "SALES".equals(t.getDepartmentCode())
                    && "sales".equals(t.getResource())
                    && "read".equals(t.getAction()));
  }

  @Test
  @DisplayName("running twice inserts nothing the second time")
  void isIdempotentAcrossBoots() {
    List<PermissionTemplate> firstBoot = new ArrayList<>();
    seeder(List.of(), firstBoot).seed();
    assertThat(firstBoot).isNotEmpty();

    List<PermissionTemplate> secondBoot = new ArrayList<>();
    seeder(firstBoot, secondBoot).seed();
    assertThat(secondBoot).isEmpty();
  }

  @Test
  @DisplayName("no two desired rows collide on the unique index key")
  void desiredSetHasNoConflictingDuplicates() {
    List<PermissionTemplate> saved = new ArrayList<>();

    // rejectConflictingDuplicates throws before saveAll if the catalogue declares the same
    // (role, department, resource, action) twice with different data_scope values. The unique
    // index ignores data_scope, so such a pair would blow up at boot instead of at build time.
    seeder(List.of(), saved).seed();

    Set<String> keys = new HashSet<>();
    for (PermissionTemplate t : saved) {
      String key =
          String.join(
              "|",
              t.getRoleCode(),
              t.getDepartmentCode() == null ? "__ALL__" : t.getDepartmentCode(),
              t.getResource(),
              t.getAction());
      assertThat(keys.add(key)).as("duplicate effective key: %s", key).isTrue();
    }
  }

  @Test
  @DisplayName("owns the sales:approve rows that migration V20260706120000 used to write")
  void catalogueOwnsWildcardSalesApprove() {
    List<PermissionTemplate> saved = new ArrayList<>();
    seeder(List.of(), saved).seed();

    assertThat(saved)
        .extracting(
            PermissionTemplate::getRoleCode,
            PermissionTemplate::getDepartmentCode,
            PermissionTemplate::getResource,
            PermissionTemplate::getAction,
            PermissionTemplate::getDataScope)
        .contains(
            Tuple.tuple("ADMIN", null, "sales", "approve", DataScope.GLOBAL),
            Tuple.tuple("MANAGER", null, "sales", "approve", DataScope.ORGANIZATION),
            Tuple.tuple("SUPERVISOR", null, "sales", "approve", DataScope.ORGANIZATION));
  }

  @Test
  @DisplayName("COLOR-RBAC-1: colours matrix is ORGANIZATION-scoped with the right grants/absences")
  void seedsColourMatrix() {
    List<PermissionTemplate> saved = new ArrayList<>();
    seeder(List.of(), saved).seed();

    List<PermissionTemplate> colours =
        saved.stream().filter(t -> "colors".equals(t.getResource())).toList();

    assertThat(colours)
        .as("colour cards are tenant-wide master data")
        .isNotEmpty()
        .allSatisfy(t -> assertThat(t.getDataScope()).isEqualTo(DataScope.ORGANIZATION));

    List<Tuple> grants =
        colours.stream()
            .map(t -> Tuple.tuple(t.getRoleCode(), t.getDepartmentCode(), t.getAction()))
            .toList();

    // Quality: worker read; supervisor read+write; manager full.
    assertThat(grants)
        .contains(
            Tuple.tuple("WORKER", "QUALITY", "read"),
            Tuple.tuple("SUPERVISOR", "QUALITY", "read"),
            Tuple.tuple("SUPERVISOR", "QUALITY", "write"),
            Tuple.tuple("MANAGER", "QUALITY", "read"),
            Tuple.tuple("MANAGER", "QUALITY", "write"),
            Tuple.tuple("MANAGER", "QUALITY", "approve"),
            Tuple.tuple("MANAGER", "QUALITY", "manage"))
        .doesNotContain(
            Tuple.tuple("WORKER", "QUALITY", "write"),
            Tuple.tuple("SUPERVISOR", "QUALITY", "approve"),
            Tuple.tuple("SUPERVISOR", "QUALITY", "manage"));

    // Dyeing supervisor/manager write; dyeing worker read only.
    assertThat(grants)
        .contains(
            Tuple.tuple("WORKER", "DYEING", "read"),
            Tuple.tuple("SUPERVISOR", "DYEING", "write"),
            Tuple.tuple("MANAGER", "DYEING", "write"))
        .doesNotContain(Tuple.tuple("WORKER", "DYEING", "write"));

    // Non-dyeing production departments read only (e.g. WEAVING).
    assertThat(grants)
        .contains(Tuple.tuple("SUPERVISOR", "WEAVING", "read"))
        .doesNotContain(
            Tuple.tuple("SUPERVISOR", "WEAVING", "write"),
            Tuple.tuple("MANAGER", "WEAVING", "write"));

    // Sales, Warehouse & Procurement read only.
    assertThat(grants)
        .contains(
            Tuple.tuple("WORKER", "SALES", "read"),
            Tuple.tuple("MANAGER", "WAREHOUSE", "read"),
            Tuple.tuple("MANAGER", "PROCUREMENT", "read"))
        .doesNotContain(
            Tuple.tuple("MANAGER", "SALES", "write"),
            Tuple.tuple("MANAGER", "WAREHOUSE", "write"),
            Tuple.tuple("MANAGER", "PROCUREMENT", "write"));

    // Finance, HR and partner roles receive no colours rows at all.
    assertThat(colours)
        .noneMatch(
            t ->
                "FINANCE".equals(t.getDepartmentCode())
                    || "HR".equals(t.getDepartmentCode())
                    || t.getRoleCode().startsWith("PARTNER_"));
  }

  private static PermissionTemplate template(
      String roleCode, String departmentCode, String resource, String action, DataScope scope) {
    PermissionTemplate template =
        PermissionTemplate.builder()
            .roleCode(roleCode)
            .departmentCode(departmentCode)
            .resource(resource)
            .action(action)
            .dataScope(scope)
            .build();
    template.setTenantId(TenantContext.TEMPLATE_TENANT_ID);
    template.setIsActive(true);
    return template;
  }

  @SuppressWarnings("unchecked")
  private PermissionTemplateSeeder seeder(
      List<PermissionTemplate> existing, List<PermissionTemplate> savedSink) {
    PermissionTemplateRepository repository =
        (PermissionTemplateRepository)
            Proxy.newProxyInstance(
                PermissionTemplateRepository.class.getClassLoader(),
                new Class<?>[] {PermissionTemplateRepository.class},
                (proxy, method, args) ->
                    switch (method.getName()) {
                      case "findByTenantId" -> List.copyOf(existing);
                      case "existsByTenantId" -> !existing.isEmpty();
                      case "saveAll" -> {
                        List<PermissionTemplate> batch = (List<PermissionTemplate>) args[0];
                        savedSink.addAll(batch);
                        yield batch;
                      }
                      default -> null;
                    });
    return new PermissionTemplateSeeder(repository, transactionTemplate());
  }

  private TransactionTemplate transactionTemplate() {
    return new TransactionTemplate(
        new PlatformTransactionManager() {
          @Override
          public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
          }

          @Override
          public void commit(TransactionStatus status) {}

          @Override
          public void rollback(TransactionStatus status) {}
        });
  }
}
