package com.fabricmanagement.sales.ownership;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.BaseJunctionEntity;
import com.fabricmanagement.sales.ownership.domain.CustomerAccountTeamMember;
import com.fabricmanagement.sales.ownership.infra.repository.CustomerCommercialAssignmentRepository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.CrudRepository;

class SalesOwnershipArchTest {

  private static JavaClasses productionClasses;

  @BeforeAll
  static void importClasses() {
    productionClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.fabricmanagement");
  }

  @Test
  void ownershipModuleDoesNotImportPlatformDomainClasses() {
    noClasses()
        .that()
        .resideInAPackage("com.fabricmanagement.sales.ownership..")
        .and()
        .haveSimpleNameNotEndingWith("Listener")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.fabricmanagement.platform..domain..")
        .check(productionClasses);
  }

  @Test
  void accountTeamMemberReusesJunctionAuditAndSoftDeleteContract() {
    assertThat(CustomerAccountTeamMember.class.getSuperclass()).isEqualTo(BaseJunctionEntity.class);
  }

  @Test
  void commercialAssignmentRepositoryExposesNoRowDeleteSurface() {
    assertThat(CrudRepository.class.isAssignableFrom(CustomerCommercialAssignmentRepository.class))
        .isFalse();
    assertThat(CustomerCommercialAssignmentRepository.class.getMethods())
        .noneMatch(
            method ->
                method.getName().startsWith("delete") || method.getName().startsWith("remove"));
  }

  @Test
  void salesPublishesEventsWithoutDependingOnNotificationInternals() {
    noClasses()
        .that()
        .resideInAPackage("com.fabricmanagement.sales..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.fabricmanagement.notification..")
        .check(productionClasses);
  }

  @Test
  void salesUsesOnlyThePublicCommunicationFacade() {
    noClasses()
        .that()
        .resideInAPackage("com.fabricmanagement.sales..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.fabricmanagement.platform.communication.app..")
        .check(productionClasses);
  }

  @Test
  void triageCaseLogMigrationHasPhysicalDedupAndRls() throws IOException {
    String migration =
        Files.readString(
            Path.of(
                "src/main/resources/db/migration/"
                    + "V20260728130000__create_ownership_triage_case_log.sql"));

    assertThat(migration)
        .contains("UNIQUE (tenant_id, customer_id, gap_started_at)")
        .contains("ENABLE ROW LEVEL SECURITY")
        .contains("FORCE ROW LEVEL SECURITY")
        .doesNotContain("FOREIGN KEY");
  }
}
