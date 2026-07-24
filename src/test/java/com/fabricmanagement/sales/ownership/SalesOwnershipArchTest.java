package com.fabricmanagement.sales.ownership;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.BaseJunctionEntity;
import com.fabricmanagement.sales.ownership.domain.CustomerAccountTeamMember;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.fabricmanagement.platform..domain..")
        .check(productionClasses);
  }

  @Test
  void accountTeamMemberReusesJunctionAuditAndSoftDeleteContract() {
    assertThat(CustomerAccountTeamMember.class.getSuperclass()).isEqualTo(BaseJunctionEntity.class);
  }
}
