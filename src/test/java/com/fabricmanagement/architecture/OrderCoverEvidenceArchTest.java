package com.fabricmanagement.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class OrderCoverEvidenceArchTest {
  @Test
  void evidenceBoundaryUsesOnlySalesOwnedContracts() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(
                "com.fabricmanagement.sales.salesorder",
                "com.fabricmanagement.production.core.batch");
    noClasses()
        .that()
        .resideInAPackage("com.fabricmanagement.sales.salesorder..")
        .and()
        .haveSimpleNameStartingWith("OrderCover")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.fabricmanagement.production..")
        .check(classes);
    noClasses()
        .that()
        .haveSimpleName("OrderCoverEvidenceAdapter")
        .should()
        .dependOnClassesThat(
            resideInAPackage("com.fabricmanagement.sales..")
                .and(resideOutsideOfPackage("com.fabricmanagement.sales.salesorder.domain.port.."))
                .and(resideOutsideOfPackage("com.fabricmanagement.sales.salesorder.dto..")))
        .check(classes);
  }
}
