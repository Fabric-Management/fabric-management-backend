package com.fabricmanagement.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.platform.tenant.domain.port.TenantCatalogueProvisioningPort;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * TASK-TEMPLATE-TENANCY-1 §7 / §8.10: FlowBoard owns the task-template catalogue; platform reaches
 * it only through {@link TenantCatalogueProvisioningPort}; BYPASSRLS stays in the distribution
 * component.
 */
class TaskTemplateCatalogueArchTest {

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.fabricmanagement");
  }

  @Test
  void platformDoesNotDependOnFlowboard() {
    noClasses()
        .that()
        .resideInAPackage("com.fabricmanagement.platform..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.fabricmanagement.flowboard..")
        .because("platform reaches the task-template catalogue only through its own port")
        .check(classes);
  }

  @Test
  void tenantQueryContractStaysGeneric() {
    // Deliberately narrow: other common packages have pre-existing dependencies (out of scope).
    noClasses()
        .that()
        .resideInAPackage("com.fabricmanagement.common.infrastructure.tenant..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("com.fabricmanagement.flowboard..", "com.fabricmanagement.platform..")
        .check(classes);
  }

  @Test
  void provisioningPortIsImplementedOnlyByFlowboardAdapters() {
    classes()
        .that()
        .implement(TenantCatalogueProvisioningPort.class)
        .should()
        .resideInAPackage("com.fabricmanagement.flowboard..app.adapter..")
        .check(classes);
  }

  @Test
  void onlyTheDistributionComponentUsesBypassRlsInTheGenerator() {
    noClasses()
        .that()
        .resideInAPackage("com.fabricmanagement.flowboard.generator..")
        .and()
        .doNotHaveFullyQualifiedName(
            "com.fabricmanagement.flowboard.generator.app.catalogue.CatalogueSourceReader")
        .and()
        .doNotHaveFullyQualifiedName(
            "com.fabricmanagement.flowboard.generator.app.catalogue.PlaygroundCatalogueWriter")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName(
            "com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor")
        .check(classes);
  }

  /**
   * Pre-existing and allowed: tenant purge deletes every table of a purged tenant, including this
   * one. It is tenant lifecycle, not catalogue distribution.
   */
  private static final Set<String> PURGE_ALLOWLIST = Set.of("TenantTransactionalPurgeService.java");

  private static final Pattern TASK_TEMPLATE_TABLE =
      Pattern.compile("(?<![a-z_])(flowboard\\.)?task_template(?![a-z_])");

  @Test
  void platformSourceNeverMentionsTheTaskTemplateTable() throws IOException {
    Path root = Path.of("src/main/java/com/fabricmanagement/platform");
    var violations = new TreeSet<String>();
    try (var files = Files.walk(root)) {
      files
          .filter(path -> path.toString().endsWith(".java"))
          .filter(path -> !PURGE_ALLOWLIST.contains(path.getFileName().toString()))
          .forEach(
              path -> {
                try {
                  if (TASK_TEMPLATE_TABLE.matcher(Files.readString(path)).find()) {
                    violations.add(path.toString());
                  }
                } catch (IOException failure) {
                  throw new UncheckedIOException(failure);
                }
              });
    }
    assertThat(violations)
        .as("platform must not read or write flowboard.task_template (TASK-TEMPLATE-TENANCY-1)")
        .isEmpty();
  }
}
