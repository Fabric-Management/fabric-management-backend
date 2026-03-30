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
 * ArchUnit tests enforcing the Backend Architecture Constitution v1.0.
 *
 * <p>Each test maps to a specific "Rule" (Kural) in the Constitution document. Tests are organized
 * by Constitution article (Madde) number.
 *
 * <h2>Strategy: "Freeze + Enforce"</h2>
 *
 * <ul>
 *   <li>Rules that the codebase currently PASSES → enabled, will catch regressions
 *   <li>Rules that have KNOWN violations → documented with violation count, act as tracker
 * </ul>
 *
 * @see <a href="agent/skills/fabric management backend /BACKEND_ARCHITECTURE_CONSTITUTION.md">
 *     Constitution v1.0</a>
 */
class ConstitutionArchTest {

  private static JavaClasses allClasses;

  @BeforeAll
  static void importClasses() {
    allClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.fabricmanagement");
  }

  // ═══════════════════════════════════════════════════════════════════
  // Article 2: Layered Architecture
  // ═══════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Article 2 — Layered Architecture")
  class LayerArchitectureTests {

    @Test
    @DisplayName("Rule 2.1: 'application' package is forbidden, use 'app'")
    void applicationPackageShouldNotExist() {
      // ✅ CLEAN: All known violations fixed (2026-03-23):
      //   - flowboard/automation/application/ → domain/port/out/
      //   - flowboard/generator/application/ → domain/port/out/
      //   - human/core/employee/application/ → app/
      //   - production/execution/lineage/application/ → app/

      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("com.fabricmanagement..")
              .should()
              .resideInAPackage("..application..")
              .as("Rule 2.1: 'application' package string is forbidden — use 'app' instead");

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Rule 2.2: domain layer must not depend on api or app layers")
    void domainShouldNotDependOnApiOrApp() {
      // Existing violation (1 count):
      //   - human/compliance/domain/EmployeeCompliancePolicy →
      // compliance/app/EmployeeComplianceContext

      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("..domain..")
              .and()
              .resideOutsideOfPackage("com.fabricmanagement.human.compliance.domain..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage("..api..", "..app..", "..application..")
              .as(
                  "Rule 2.2: domain layer must not depend on external layers"
                      + " (imports to api, app, application are forbidden)."
                      + " (1 existing violation frozen: human/compliance/domain)");

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Rule 2.2: infra layer must not depend on api or app layers")
    void infraShouldNotDependOnApiOrApp() {
      // Existing violations (12 counts, 2 classes):
      //   - notification/hub/infra/email/EmailNotificationSender →
      // communication/app/EmailOutboxService
      //   - notification/hub/infra/websocket/WebSocketAuthInterceptor → auth/app/JwtService

      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("..infra..")
              .and()
              .resideOutsideOfPackage("com.fabricmanagement.notification.hub.infra..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage("..api..", "..app..", "..application..")
              .as(
                  "Rule 2.2: infra layer can only depend on domain layer."
                      + " (2 classes frozen: notification/hub/infra)");

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Rule 2.3: DTOs must reside in the module root dto/ package, not under api/")
    void dtosShouldNotResideUnderApi() {
      // ✅ CLEAN: All known violations fixed (2026-03-23):
      //   - costing/api/dto/ → dto/
      //   - flowboard/dashboard/api/dto/ (and subpackages) → dto/
      //   - human/payroll/api/dto/ → dto/
      //   - sales/quote/api/dto/ → dto/
      //   - sales/sample/api/dto/ → dto/

      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("com.fabricmanagement..")
              .should()
              .resideInAPackage("..api.dto..")
              .as(
                  "Rule 2.3: DTOs must be in the module's root dto/ package, api/dto/ is forbidden.");

      rule.check(allClasses);
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  // Article 3: Cross-Module Communication
  // ═══════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Article 3 — Cross-Module Communication")
  class CrossModuleCommunicationTests {

    @Test
    @DisplayName("Rule 3.2: Event classes must reside only under domain/event/")
    void eventsShouldResideInDomainEventPackage() {
      // ✅ CLEAN: All known violations fixed (2026-03-23):
      //   - flowboard/task/app/event/ → domain/event/
      //   - iwm/rules/app/event/ → domain/event/

      ArchRule rule =
          noClasses()
              .that()
              .areAssignableTo(com.fabricmanagement.common.infrastructure.events.DomainEvent.class)
              .should()
              .resideInAPackage("..app.event..")
              .as(
                  "Rule 3.2: DomainEvent subclasses must reside under domain/event/,"
                      + " app/event/ is forbidden");

      rule.check(allClasses);
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  // Article 4: Domain Model Rules
  // ═══════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Article 4 — Domain Model")
  class DomainModelTests {

    @Test
    @DisplayName("Rule 4.3: All JPA Entities must extend BaseEntity or BaseJunctionEntity")
    void allEntitiesShouldExtendBaseEntity() {
      // Existing violations (7 counts):
      //   - Tenant, TradingPartnerRegistry, UserDepartment
      //   - TaskDependency, TaskLabelAssignment
      //   - EmployeeNumberSequence
      //   - BatchOverrideLog
      // These classes must be refactored to extend BaseEntity/BaseJunctionEntity.

      ArchRule rule =
          classes()
              .that()
              .areAnnotatedWith(jakarta.persistence.Entity.class)
              .and()
              .haveSimpleNameNotContaining("Tenant")
              .and()
              .doNotHaveSimpleName("TradingPartnerRegistry")
              .and()
              .doNotHaveSimpleName("UserDepartment")
              .and()
              .doNotHaveSimpleName("TaskDependency")
              .and()
              .doNotHaveSimpleName("TaskLabelAssignment")
              .and()
              .doNotHaveSimpleName("EmployeeNumberSequence")
              .and()
              .doNotHaveSimpleName("BatchOverrideLog")
              .should()
              .beAssignableTo(
                  com.fabricmanagement.common.infrastructure.persistence.BaseEntity.class)
              .orShould()
              .beAssignableTo(
                  com.fabricmanagement.common.infrastructure.persistence.BaseJunctionEntity.class)
              .as(
                  "Rule 4.3: All @Entity classes must extend BaseEntity or"
                      + " BaseJunctionEntity."
                      + " (7 existing violations frozen)");

      rule.check(allClasses);
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  // Article 5: Security and Multi-Tenancy
  // ═══════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Article 5 — Security")
  class SecurityTests {

    @Test
    @DisplayName("Rule 5.3: JWT parsing should only be done in the platform/auth module")
    void jwtParsingOnlyInAuthModule() {
      ArchRule rule =
          noClasses()
              .that()
              .resideOutsideOfPackages(
                  "com.fabricmanagement.platform.auth..",
                  "com.fabricmanagement.common.infrastructure.security..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("io.jsonwebtoken..")
              .as(
                  "Rule 5.3: JWT library should only be accessible from platform/auth and"
                      + " common/infrastructure/security modules");

      rule.check(allClasses);
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  // Article 6: Naming Conventions
  // ═══════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Article 6 — Naming Conventions")
  class NamingConventionTests {

    @Test
    @DisplayName("Rule 6.2: Controller classes must end with 'Controller' suffix")
    void controllersShouldEndWithController() {
      ArchRule rule =
          classes()
              .that()
              .resideInAPackage("..api.controller..")
              .or()
              .resideInAPackage("..api..")
              .and()
              .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
              .should()
              .haveSimpleNameEndingWith("Controller")
              .as("Rule 6.2: @RestController classes must end with 'Controller'");

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Rule 6.2: Service classes must end with an appropriate suffix")
    void servicesShouldEndWithService() {
      // Accepted suffixes: Service, Engine, Calculator, Processor,
      // Orchestrator, Facade, Listener, Job, Manager, Dispatcher, Resolver,
      // Evaluator, Impl (interface impl pattern)

      ArchRule rule =
          classes()
              .that()
              .resideInAPackage("..app..")
              .and()
              .areAnnotatedWith(org.springframework.stereotype.Service.class)
              .and()
              .haveSimpleNameNotEndingWith("Listener")
              .and()
              .haveSimpleNameNotEndingWith("Job")
              .and()
              .haveSimpleNameNotEndingWith("Engine")
              .and()
              .haveSimpleNameNotEndingWith("EngineImpl")
              .and()
              .haveSimpleNameNotEndingWith("Calculator")
              .and()
              .haveSimpleNameNotEndingWith("Processor")
              .and()
              .haveSimpleNameNotEndingWith("Orchestrator")
              .and()
              .haveSimpleNameNotEndingWith("Facade")
              .and()
              .haveSimpleNameNotEndingWith("Manager")
              .and()
              .haveSimpleNameNotEndingWith("Dispatcher")
              .and()
              .haveSimpleNameNotEndingWith("Resolver")
              .and()
              .haveSimpleNameNotEndingWith("Evaluator")
              .and()
              .haveSimpleNameNotEndingWith("Impl")
              .should()
              .haveSimpleNameEndingWith("Service")
              .as(
                  "Rule 6.2: @Service classes under app/ must end with"
                      + " an appropriate suffix (Service, Engine, Manager, etc.)");

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Rule 6.2: Repository classes must end with 'Repository' suffix")
    void repositoriesShouldEndWithRepository() {
      ArchRule rule =
          classes()
              .that()
              .resideInAPackage("..infra.repository..")
              .or()
              .resideInAPackage("..infra..")
              .and()
              .areAssignableTo(org.springframework.data.repository.Repository.class)
              .should()
              .haveSimpleNameEndingWith("Repository")
              .as("Rule 6.2: Repository interfaces must end with 'Repository'");

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Rule 6.2: Domain Events must end with 'Event' suffix")
    void domainEventsShouldEndWithEvent() {
      ArchRule rule =
          classes()
              .that()
              .areAssignableTo(com.fabricmanagement.common.infrastructure.events.DomainEvent.class)
              .and()
              .areNotAssignableFrom(
                  com.fabricmanagement.common.infrastructure.events.DomainEvent.class)
              .should()
              .haveSimpleNameEndingWith("Event")
              .as("Rule 6.2 + 8.2: DomainEvent subclasses must end with 'Event' suffix");

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Rule 6.3: 'infrastructure' package is only allowed under common/")
    void infrastructurePackageOnlyUnderCommon() {
      // ✅ CLEAN: All known violations fixed (2026-03-23):
      //   - platform/communication/infrastructure/ → infra/

      ArchRule rule =
          noClasses()
              .that()
              .resideOutsideOfPackage("com.fabricmanagement.common.infrastructure..")
              .should()
              .resideInAPackage("..infrastructure..")
              .as(
                  "Rule 6.3: 'infrastructure' package name is strictly reserved for common/infrastructure/."
                      + " Other modules must use 'infra'.");

      rule.check(allClasses);
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  // Article 8: Domain Event Standards
  // ═══════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Article 8 — Domain Event Standards")
  class DomainEventTests {

    @Test
    @DisplayName("Rule 8.1: All domain events must extend DomainEvent base class")
    void allEventsInEventPackageShouldExtendDomainEvent() {
      // Existing violations (7 record/POJO events, they do not extend DomainEvent):
      //   - CostVarianceDetectedEvent
      //   - GoodsReceiptConfirmedEvent
      //   - InventoryTransactionCreatedEvent
      //   - BatchLineageCreatedEvent
      //   - BatchLineageDeletedEvent
      //   - MinStockAlertEvent (moved from app/event, still a POJO)
      //   - ReturnRateExceededEvent (moved from app/event, still a POJO)

      ArchRule rule =
          classes()
              .that()
              .resideInAPackage("..domain.event..")
              .and()
              .haveSimpleNameEndingWith("Event")
              .and()
              .doNotHaveSimpleName("CostVarianceDetectedEvent")
              .and()
              .doNotHaveSimpleName("GoodsReceiptConfirmedEvent")
              .and()
              .doNotHaveSimpleName("InventoryTransactionCreatedEvent")
              .and()
              .doNotHaveSimpleName("BatchLineageCreatedEvent")
              .and()
              .doNotHaveSimpleName("BatchLineageDeletedEvent")
              .and()
              .doNotHaveSimpleName("MinStockAlertEvent")
              .and()
              .doNotHaveSimpleName("ReturnRateExceededEvent")
              .should()
              .beAssignableTo(com.fabricmanagement.common.infrastructure.events.DomainEvent.class)
              .as(
                  "Rule 8.1: Event classes under domain/event/ must extend"
                      + " the DomainEvent base class."
                      + " (7 existing violations frozen)");

      rule.check(allClasses);
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  // Article 11: Platform Module Special Rules
  // ═══════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Article 11 — Platform Module")
  class PlatformModuleTests {

    @Test
    @DisplayName("Rule 11.2: Domain modules can depend on platform, not vice versa")
    void platformShouldNotDependOnDomainModules() {
      // Existing violations (reduced 2026-03-30: platform user/admin/auth use employee ports):
      //   - platform/user -> human (only Employee*Event listeners + UserCacheInvalidationService;
      //     see Rule 11.3)
      //   - platform/ai -> production/masterdata/fiber + material
      //   - platform/tradingpartner -> production/masterdata/fiber
      //   - platform/organization -> production/masterdata/fiber

      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("com.fabricmanagement.platform..")
              .and()
              .resideOutsideOfPackage("com.fabricmanagement.platform.user..")
              .and()
              .resideOutsideOfPackage("com.fabricmanagement.platform.admin..")
              .and()
              .resideOutsideOfPackage("com.fabricmanagement.platform.ai..")
              .and()
              .resideOutsideOfPackage("com.fabricmanagement.platform.tradingpartner..")
              .and()
              .resideOutsideOfPackage("com.fabricmanagement.platform.organization..")
              .and()
              .resideOutsideOfPackage("com.fabricmanagement.platform.auth..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage(
                  "com.fabricmanagement.production..",
                  "com.fabricmanagement.sales..",
                  "com.fabricmanagement.procurement..",
                  "com.fabricmanagement.flowboard..",
                  "com.fabricmanagement.human..",
                  "com.fabricmanagement.iwm..",
                  "com.fabricmanagement.costing..",
                  "com.fabricmanagement.finance..",
                  "com.fabricmanagement.logistics..",
                  "com.fabricmanagement.notification..",
                  "com.fabricmanagement.offline..")
              .as(
                  "Rule 11.2: Platform modules must not depend on domain modules"
                      + " (one-way dependency only)."
                      + " (6 platform sub-modules frozen)");

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Rule 11.3: platform/user must not depend on human except event listeners")
    void platformUserShouldNotImportHumanDirectly() {
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("com.fabricmanagement.platform.user..")
              .and()
              .haveSimpleNameNotEndingWith("EventListener")
              .and()
              .doNotHaveSimpleName("UserCacheInvalidationService")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("com.fabricmanagement.human..")
              .as(
                  "Rule 11.3: platform/user uses ports/adapters for employee data;"
                      + " human imports are limited to Employee*Event listeners and cache invalidation");

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Rule 11.4: platform/ai must not access production infrastructure directly")
    void aiModuleShouldNotAccessProductionInfrastructure() {
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("com.fabricmanagement.platform.ai..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("com.fabricmanagement.production..infra..")
              .as(
                  "Rule 11.4: platform/ai must not access production infrastructure directly (repositories, entities);"
                      + " it must use facades or AIToolRegistry.");

      rule.check(allClasses);
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  // Article 1: Module Boundaries — common/infrastructure scope check
  // ═══════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Article 1 — Module Boundaries")
  class ModuleBoundaryTests {

    @Test
    @DisplayName("Rule 1.3: common/infrastructure should not contain business logic")
    void commonInfrastructureShouldNotContainBusinessLogic() {
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("com.fabricmanagement.common.infrastructure..")
              .should()
              .beAnnotatedWith(org.springframework.stereotype.Service.class)
              .as(
                  "Rule 1.3: common/infrastructure/ only contains framework-level infrastructure,"
                      + " and must not house any @Service classes");

      rule.check(allClasses);
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  // Article 12: WorkOrder Bounded Context Isolation
  // ═══════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Article 12 — WorkOrder Bounded Context Isolation")
  class WorkOrderBoundedContextTests {

    @Test
    @DisplayName("Rule 12.1: sales must not depend on production app or infra layers")
    void salesShouldNotDependOnProductionImplementation() {
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("com.fabricmanagement.sales..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage(
                  "com.fabricmanagement.production..app..",
                  "com.fabricmanagement.production..infra..")
              .as(
                  "Rule 12.1: sales must use ProductionOrderPort — direct WorkOrderService/repository import forbidden");

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Rule 12.2: production must not depend on approval app layer directly")
    void productionShouldNotDependOnApprovalAppLayer() {
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("com.fabricmanagement.production..")
              .and()
              .haveSimpleNameNotEndingWith("EventListener")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("com.fabricmanagement.approval.app..")
              .as(
                  "Rule 12.2: production must use ApprovalPort from common — direct ApprovalGuardService import forbidden (event listeners exempt)");

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Rule 12.3: approval must not access platform.user infrastructure directly")
    void approvalShouldNotAccessPlatformUserInfrastructure() {
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("com.fabricmanagement.approval..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("com.fabricmanagement.platform.user.infra..")
              .as(
                  "Rule 12.3: approval must use UserTrustLevelPort — direct UserRepository import forbidden");

      rule.check(allClasses);
    }
  }
}
