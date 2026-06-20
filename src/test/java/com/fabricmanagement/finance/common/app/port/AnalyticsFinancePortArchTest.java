package com.fabricmanagement.finance.common.app.port;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalyticsFinancePortArchTest {

  private static JavaClasses allClasses;

  @BeforeAll
  static void importClasses() {
    allClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.fabricmanagement");
  }

  @Test
  @DisplayName("Finance ports should not access sales or analytics domains")
  void financePortsShouldNotReachIntoSalesOrAnalytics() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("com.fabricmanagement.finance.common.app.port.impl..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.fabricmanagement.sales..", "com.fabricmanagement.analytics..")
            .as(
                "AnalyticsFinancePortImpl must only use finance internal models, not external domains.");

    rule.check(allClasses);
  }
}
