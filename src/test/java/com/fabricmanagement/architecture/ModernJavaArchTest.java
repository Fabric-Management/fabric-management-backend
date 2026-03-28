package com.fabricmanagement.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit tests enforcing DDD, Clean Code, and API consistency rules.
 *
 * <p>Complements {@link ConstitutionArchTest} which covers structural/layer rules. This class
 * focuses on code quality patterns aligned with AGENTS.md §4, §5, §12.
 *
 * <h2>Rule Groups</h2>
 *
 * <ul>
 *   <li>DDD — Domain layer purity (no @Service, no @Repository, no Spring Web in domain)
 *   <li>Clean Code — No @Transactional on controllers, no entity leak to API layer
 *   <li>API Consistency — Controllers in correct packages with correct annotations
 * </ul>
 *
 * @see ConstitutionArchTest
 */
class ModernJavaArchTest {

  private static JavaClasses allClasses;

  @BeforeAll
  static void importClasses() {
    allClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.fabricmanagement");
  }

  // ═══════════════════════════════════════════════════════════════════
  // Group 1: Domain-Driven Design — Domain Layer Purity
  // ═══════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("DDD — Domain Layer Purity")
  class DomainLayerPurityTests {

    @Test
    @DisplayName("DDD-1: Domain layer must not contain @Service classes")
    void domainShouldNotContainServiceAnnotation() {
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("..domain..")
              .should()
              .beAnnotatedWith(org.springframework.stereotype.Service.class)
              .as(
                  "DDD-1: @Service is forbidden in domain layer."
                      + " Business logic belongs IN the entity (Rich Domain Model),"
                      + " orchestration belongs in the app/ layer.");

      rule.check(allClasses);
    }

    @Test
    @DisplayName("DDD-2: Domain layer must not contain @Repository classes")
    void domainShouldNotContainRepositoryAnnotation() {
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("..domain..")
              .should()
              .beAnnotatedWith(org.springframework.stereotype.Repository.class)
              .as(
                  "DDD-2: @Repository is forbidden in domain layer."
                      + " Data access belongs in the infra/repository/ layer.");

      rule.check(allClasses);
    }

    @Test
    @DisplayName("DDD-3: Domain layer must not depend on Spring Web/MVC")
    void domainShouldNotDependOnSpringWeb() {
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("..domain..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage(
                  "org.springframework.web..",
                  "org.springframework.http..",
                  "org.springframework.web.bind.annotation..")
              .as(
                  "DDD-3: Domain layer must not depend on Spring Web/MVC."
                      + " HTTP concerns belong in the api/controller/ layer.");

      rule.check(allClasses);
    }

    @Test
    @DisplayName("DDD-4: Domain layer must not depend on infrastructure/repository classes")
    void domainShouldNotDependOnInfraRepository() {
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("..domain..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("..infra.repository..")
              .as(
                  "DDD-4: Domain must not depend on infra/repository."
                      + " Dependency direction: infra → domain, never the reverse.");

      rule.check(allClasses);
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  // Group 2: Clean Code — Layer Discipline
  // ═══════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Clean Code — Layer Discipline")
  class CleanCodeTests {

    @Test
    @DisplayName("CC-1: Controllers must not have @Transactional")
    void controllersShouldNotHaveTransactional() {
      ArchRule rule =
          noClasses()
              .that()
              .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
              .should()
              .beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
              .as(
                  "CC-1: @Transactional on controllers is forbidden."
                      + " Transaction management belongs in the Service (app/) layer.");

      rule.check(allClasses);
    }

    @Test
    @DisplayName("CC-2: Controllers must not directly depend on @Entity classes")
    void controllersShouldNotDependOnEntities() {
      // SYSTEMIC ISSUE: tracking violations across many modules.
      // Controllers return entities directly instead of DTOs.
      //
      // This is a known anti-pattern causing:
      //   - Circular reference risks (Jackson serialization)
      //   - LazyInitializationException outside transaction
      //   - Sensitive data leakage (tenantId, version, etc.)
      //
      // When a module is refactored to use DTOs, enable this test
      // for that module's package specifically.

      ArchRule rule =
          noClasses()
              .that()
              .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
              .and()
              .resideOutsideOfPackage("com.fabricmanagement.platform.dev..")
              .should()
              .dependOnClassesThat()
              .areAnnotatedWith(jakarta.persistence.Entity.class)
              .as(
                  "CC-2: Controllers must not depend on @Entity classes."
                      + " Always use DTOs for API input/output.");

      rule.check(allClasses);
    }

    @Test
    @DisplayName("CC-3: Domain layer must not use @Component (use app/ for Spring beans)")
    void domainShouldNotUseComponent() {
      // Existing violations (3 counts):
      //   - platform/communication/domain/strategy/EmailStrategy
      //   - platform/communication/domain/strategy/SmsStrategy
      //   - platform/communication/domain/strategy/WhatsAppStrategy
      // These strategy classes should be moved to app/ or infra/ layer.

      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("..domain..")
              .and()
              .resideOutsideOfPackage(
                  "com.fabricmanagement.platform.communication.domain.strategy..")
              .should()
              .beAnnotatedWith(org.springframework.stereotype.Component.class)
              .as(
                  "CC-3: @Component is forbidden in domain layer."
                      + " Domain objects should be framework-agnostic."
                      + " Use app/ layer for Spring-managed beans."
                      + " (3 existing violations frozen:"
                      + " platform/communication/domain/strategy)");

      rule.check(allClasses);
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  // Group 3: API Consistency
  // ═══════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("API Consistency")
  class ApiConsistencyTests {

    @Test
    @DisplayName("API-1: @RestController classes must reside under api/ package")
    void controllersMustResideInApiPackage() {
      // Existing violations (2 counts):
      //   - common/infrastructure/web/HealthController (infra-level health check)
      //   - platform/dev/DevelopmentToolsController (dev-only tools)
      // These are infrastructure controllers, not domain API controllers.

      ArchRule rule =
          classes()
              .that()
              .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
              .and()
              .doNotHaveSimpleName("HealthController")
              .and()
              .doNotHaveSimpleName("DevelopmentToolsController")
              .should()
              .resideInAPackage("..api..")
              .as(
                  "API-1: All @RestController classes must reside under the api/ package."
                      + " Controllers outside api/ violate the layered architecture."
                      + " (2 infra controllers frozen: HealthController,"
                      + " DevelopmentToolsController)");

      rule.check(allClasses);
    }
  }
}
