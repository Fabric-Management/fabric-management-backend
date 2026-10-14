package com.fabricmanagement.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The lot-health rules (Fatih, 2026-10-14) are pure: the proposal, the warehouse, shipping and
 * reservations call them and get the same answer, so they depend on nothing but the JDK.
 */
@AnalyzeClasses(
    packages = "com.fabricmanagement.inventory.lothealth",
    importOptions = {ImportOption.DoNotIncludeTests.class})
public class LotHealthPurityArchTest {

  @ArchTest
  static final ArchRule lotHealthDomainDependsOnlyOnTheJdk =
      classes()
          .that()
          .resideInAPackage("com.fabricmanagement.inventory.lothealth.domain..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("java..", "com.fabricmanagement.inventory.lothealth.domain..")
          .as("Lot-health rules depend only on the JDK and themselves");
}
