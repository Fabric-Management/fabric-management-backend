package com.fabricmanagement.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.fabricmanagement.platform.tenant.domain.port.PlaygroundFixtureProvisioningPort;
import com.fabricmanagement.product.fiber.app.FiberPlaygroundFixtureService;
import com.fabricmanagement.production.playground.app.PlaygroundFiberFixtureService;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FIBER-CATALOG-1 §9: playground fibre fixtures are installed only by the two trusted creation
 * origins. Reset, login, startup, backfill and the generic demo seeders never reach them.
 */
class PlaygroundFixtureOriginArchTest {

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.fabricmanagement");
  }

  @Test
  @DisplayName("Only legacy playground creation and register-first onboarding call the port")
  void onlyTrustedOriginsUseTheProvisioningPort() {
    noClasses()
        .that()
        .doNotHaveFullyQualifiedName("com.fabricmanagement.platform.tenant.app.TenantClonerService")
        .and()
        .doNotHaveFullyQualifiedName(
            "com.fabricmanagement.platform.auth.app.onboarding.SeedRegisteredTenantDemoStep")
        .and()
        .resideOutsideOfPackage("com.fabricmanagement.production.playground..")
        .and()
        .doNotBelongToAnyOf(PlaygroundFixtureProvisioningPort.class)
        .should()
        .dependOnClassesThat()
        .belongToAnyOf(PlaygroundFixtureProvisioningPort.class)
        .as("Playground fixtures are provisioned only by the two trusted creation origins")
        .check(classes);
  }

  @Test
  @DisplayName("Fixture installers are reachable only through the playground module")
  void fixtureInstallersAreNotReachableFromResetOrDemoSeeders() {
    noClasses()
        .that()
        .resideOutsideOfPackage("com.fabricmanagement.production.playground..")
        .and()
        .doNotBelongToAnyOf(FiberPlaygroundFixtureService.class)
        .should()
        .dependOnClassesThat()
        .belongToAnyOf(PlaygroundFiberFixtureService.class, FiberPlaygroundFixtureService.class)
        .as("Reset, demo seeders and other flows must never install playground fixtures")
        .check(classes);
  }
}
