package com.fabricmanagement.analytics;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalyticsArchTest {

  private static JavaClasses allClasses;

  @BeforeAll
  static void importClasses() {
    allClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.fabricmanagement");
  }

  @Test
  @DisplayName(
      "analytics must not access sales or costing infra directly, only through app-layer ports")
  void analyticsShouldNotReachIntoInfra() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("com.fabricmanagement.analytics..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.fabricmanagement.sales..infra..",
                "com.fabricmanagement.sales..domain..",
                "com.fabricmanagement.costing..infra..",
                "com.fabricmanagement.costing..domain..")
            .as(
                "Analytics must only use ports from sales and costing, never their infra or domain entities directly.");

    rule.check(allClasses);
  }
}
