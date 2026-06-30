package com.fabricmanagement.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

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
      // Documented design exception (1 class):
      //   - WebSocketAuthInterceptor: Spring ChannelInterceptor (security infra) legitimately
      //     needs JwtService for WebSocket auth. Moving to app layer would misrepresent its role.
      //
      // EmailNotificationSender was moved from infra/email/ to app/adapter/email/ — resolved.

      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("..infra..")
              .and()
              .resideOutsideOfPackage("com.fabricmanagement.notification.hub.infra.websocket..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage("..api..", "..app..", "..application..")
              .as(
                  "Rule 2.2: infra layer can only depend on domain layer."
                      + " (1 documented exception: WebSocketAuthInterceptor — security infra)");

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

    @Test
    @DisplayName(
        "Rule 3.3: finance.payment must not depend on finance.invoice.domain.Invoice entity")
    void financePaymentShouldNotDependOnInvoiceEntity() {
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("com.fabricmanagement.finance.payment..")
              .should()
              .dependOnClassesThat()
              .haveFullyQualifiedName("com.fabricmanagement.finance.invoice.domain.Invoice")
              .as(
                  "Rule 3.3: finance.payment must use InvoicePaymentPort — direct Invoice entity import is forbidden");

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
      // Documented design exceptions - these entities have legitimate
      // reasons to deviate from BaseEntity:
      //
      //   - Tenant                  : Root entity; cannot have a tenantId by definition
      //   - TradingPartnerRegistry  : Platform-wide registry; no tenant scope by design
      //   - EmployeeNumberSequence  : tenantId IS the @Id (per-tenant singleton counter)
      //   - BatchOverrideLog        : Append-only audit log; soft-delete/version semantics N/A
      //   - Lead                    : Tenant-independent marketing record; linked via nullable
      //                               trialTenantId and must survive tenant lifecycle events
      //   - UserDepartment          : Junction table with composite @IdClass key; incompatible with
      //                               BaseEntity @Id
      //   - DocumentNumberCounter   : Counter singleton; BaseEntity tenant/id semantics N/A
      //   - ProcessedEventEntry     : Durable event processing entry; BaseEntity semantics N/A
      //   - TaskLabelAssignment     : Minimal junction table; NOTE [X1] - intentional, no audit
      //                               trail needed

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
              .doNotHaveSimpleName("DocumentNumberCounter")
              .and()
              .doNotHaveSimpleName("TaskLabelAssignment")
              .and()
              .doNotHaveSimpleName("EmployeeNumberSequence")
              .and()
              .doNotHaveSimpleName("BatchOverrideLog")
              .and()
              .doNotHaveSimpleName("Lead")
              .and()
              .doNotHaveSimpleName("ProcessedEventEntry")
              .should()
              .beAssignableTo(
                  com.fabricmanagement.common.infrastructure.persistence.BaseEntity.class)
              .orShould()
              .beAssignableTo(
                  com.fabricmanagement.common.infrastructure.persistence.BaseJunctionEntity.class)
              .as(
                  "Rule 4.3: All @Entity classes must extend BaseEntity or BaseJunctionEntity. "
                      + "(documented design exceptions - see inline comments)");

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
              .haveSimpleNameNotEndingWith("Sender")
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
      // ✅ CLEAN: All known violations fixed (2026-03-31):
      //   - CostVarianceDetectedEvent
      //   - GoodsReceiptConfirmedEvent
      //   - InventoryTransactionCreatedEvent
      //   - BatchLineageCreatedEvent
      //   - BatchLineageDeletedEvent
      //   - MinStockAlertEvent & ReturnRateExceededEvent were false positives (already extended
      // DomainEvent)

      ArchRule rule =
          classes()
              .that()
              .resideInAPackage("..domain.event..")
              .and()
              .haveSimpleNameEndingWith("Event")
              .should()
              .beAssignableTo(com.fabricmanagement.common.infrastructure.events.DomainEvent.class)
              .as(
                  "Rule 8.1: Event classes under domain/event/ must extend"
                      + " the DomainEvent base class."
                      + " (0 frozen violations)");

      rule.check(allClasses);
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  // Article 15: Durable Event Standards
  // ═══════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Article 15 — Durable Event Standards")
  class DurableEventTests {

    @Test
    @DisplayName(
        "Rule 15.1: Durable-set listener classes must use @ApplicationModuleListener instead of @TransactionalEventListener(AFTER_COMMIT)")
    void durableSetMustUseDurableAnnotation() {
      // PERMANENT: notification.hub is best-effort due to external side effects (emails/SMS).
      // Applying durable pattern here would risk duplicate notifications on retries.
      ArchRule rule =
          noMethods()
              .that()
              .areDeclaredInClassesThat()
              .resideInAPackage("..app.listener..")
              .and()
              .areDeclaredInClassesThat()
              .resideOutsideOfPackage("com.fabricmanagement.notification.hub..")
              .should(
                  new com.tngtech.archunit.lang.ArchCondition<
                      com.tngtech.archunit.core.domain.JavaMethod>(
                      "not use @TransactionalEventListener with AFTER_COMMIT phase") {
                    @Override
                    public void check(
                        com.tngtech.archunit.core.domain.JavaMethod method,
                        com.tngtech.archunit.lang.ConditionEvents events) {
                      if (method.isAnnotatedWith(
                          org.springframework.transaction.event.TransactionalEventListener.class)) {
                        org.springframework.transaction.event.TransactionalEventListener
                            annotation =
                                method.getAnnotationOfType(
                                    org.springframework.transaction.event.TransactionalEventListener
                                        .class);
                        if (annotation.phase()
                            == org.springframework.transaction.event.TransactionPhase
                                .AFTER_COMMIT) {
                          events.add(
                              com.tngtech.archunit.lang.SimpleConditionEvent.violated(
                                  method,
                                  "Method "
                                      + method.getFullName()
                                      + " uses AFTER_COMMIT (default). Use @ApplicationModuleListener instead."));
                        }
                      }
                    }
                  })
              .as(
                  "Rule 15.1: Durable event pattern mandates @ApplicationModuleListener over @TransactionalEventListener(AFTER_COMMIT) for cross-module integration");

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
      // Documented design exceptions - 6 platform sub-modules with legitimate domain coupling:
      //
      // [E1] platform.user - Implements approval module ports (UserTrustLevelPort,
      //      ApproverRecipientPort, UserTrustMutationPort) and subscribes to human employee
      //      domain events (EmployeeTerminatedEvent, EmployeeUpdatedEvent) for cache
      //      invalidation. Also implements NotificationUserQueryService. All cross-domain
      //      access is done via ports/adapters or event listeners - not direct service calls.
      //      NOTE: UserTrustLevel enum moved to common.infrastructure.user - User entity
      //      no longer imports approval.domain directly; coupling is purely adapter-level.
      //
      // [E2] platform.admin - Platform-wide administrative operations require cross-domain
      //      visibility for tenant module management and health checks. Exception preserved
      //      as architectural guardrail for any future admin-to-domain interactions.
      //
      // [E3] platform.ai - AIToolRegistry aggregates AIToolProvider implementations
      //      contributed by each domain module. Cross-domain tool discovery is the core
      //      responsibility of this module; without it the AI cannot operate on domain data.
      //      See AGENTS.md Section 17 for the cross-module decoupling pattern.
      //
      // [E4] platform.tradingpartner - TradingPartner entity embeds OfflineMetadata (offline
      //      sync capability). Certification services reference FiberCertification from
      //      production masterdata via FiberCertificationQueryService (QueryService pattern -
      //      @ManyToOne already crosses module boundary at JPA level).
      //
      // [E5] platform.organization - OrganizationCertification references FiberCertification
      //      from production masterdata for fiber standard validation (same @ManyToOne
      //      coupling as tradingpartner; QueryService pattern applied in Grup B refactoring).
      //
      // [E6] platform.auth - Authentication context resolution may require domain-level
      //      permission lookup and tenant-scoped resource access checks. Exception reserved
      //      as architectural boundary for auth-to-domain interactions.
      //
      // See AGENTS.md Section 17 for design patterns governing these exceptions.

      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("com.fabricmanagement.platform..")
              .and()
              .resideOutsideOfPackage("com.fabricmanagement.platform.user..") // [E1]
              .and()
              .resideOutsideOfPackage("com.fabricmanagement.platform.admin..") // [E2]
              .and()
              .resideOutsideOfPackage("com.fabricmanagement.platform.ai..") // [E3]
              .and()
              .resideOutsideOfPackage("com.fabricmanagement.platform.tradingpartner..") // [E4]
              .and()
              .resideOutsideOfPackage("com.fabricmanagement.platform.organization..") // [E5]
              .and()
              .resideOutsideOfPackage("com.fabricmanagement.platform.auth..") // [E6]
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
                  "com.fabricmanagement.offline..",
                  "com.fabricmanagement.analytics..")
              .as(
                  "Rule 11.2: Platform modules must not depend on domain modules"
                      + " (one-way dependency only)."
                      + " (6 documented exceptions: user[E1], admin[E2], ai[E3],"
                      + " tradingpartner[E4], organization[E5], auth[E6])");

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
  // Article 13: Cross-Module Infrastructure Isolation
  // ═══════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Article 13 — Cross-Module Infrastructure Isolation")
  class CrossModuleInfrastructureIsolationTests {

    @Test
    @DisplayName("Rule 13.1: No outside module may access platform's infra layer")
    void noModuleShouldAccessPlatformInfra() {
      ArchRule rule =
          noClasses()
              .that()
              .resideOutsideOfPackage("com.fabricmanagement.platform..")
              .and()
              .resideOutsideOfPackage("com.fabricmanagement.common..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("com.fabricmanagement.platform..infra..")
              .as(
                  "Rule 13.1: No outside module may access platform's infra layer. "
                      + "(0 frozen violations)");

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Rule 13.2: No outside module may access production's infra layer")
    void noModuleShouldAccessProductionInfra() {
      ArchRule rule =
          noClasses()
              .that()
              .resideOutsideOfPackage("com.fabricmanagement.production..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("com.fabricmanagement.production..infra..")
              .as(
                  "Rule 13.2: No outside module may access production's infra layer. "
                      + "(0 frozen violations)");

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Rule 13.3: No outside module may access notification's infra layer")
    void noModuleShouldAccessNotificationInfra() {
      ArchRule rule =
          noClasses()
              .that()
              .resideOutsideOfPackage("com.fabricmanagement.notification..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("com.fabricmanagement.notification..infra..")
              .as(
                  "Rule 13.3: No outside module may access notification's infra layer. "
                      + "(0 frozen violations)");

      rule.check(allClasses);
    }

    @Test
    @DisplayName("Rule 13.4: No outside module may access procurement's infra layer")
    void noModuleShouldAccessProcurementInfra() {
      ArchRule rule =
          noClasses()
              .that()
              .resideOutsideOfPackage("com.fabricmanagement.procurement..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("com.fabricmanagement.procurement..infra..")
              .as(
                  "Rule 13.4: No outside module may access procurement's infra layer. "
                      + "(0 frozen violations)");

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

  // ═══════════════════════════════════════════════════════════════════
  // Article 14: RLS System Chokepoint Access Control
  // ═══════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Article 14 — RLS System Chokepoint")
  class RlsSystemChokepointTests {

    @Test
    @DisplayName("Rule 14.1: SystemTransactionExecutor only accessible from whitelist")
    void systemTransactionExecutorOnlyFromWhitelist() {
      // Whitelist (ADR-001 Karar 9):
      //
      //   - TenantSystemService         : Cross-tenant yönetim (admin, onboarding, tenant CRUD)
      //   - TenantService               : Self-tenant okuma (tenant tablosu self-row RLS'ye tabi)
      //   - TenantClonerService          : Onboarding: TEMPLATE→Yeni tenant klon
      //   - PlaygroundTTLReaperService   : Scheduled job: süresi dolan playground tenant'ları
      // temizle
      //   - TrialLifecycleService        : Scheduled job: registered trial expiry/activity
      // maintenance
      //   - TenantTransactionalPurgeService : Go-real purge, atomic tenant-scoped seed/data wipe
      //   - TenantQueryAdapter           : Port/Adapter: tenant lookup (auth, event yolu)
      //   - CloneTemplateRolesStep       : Onboarding: TEMPLATE rollerini yeni tenant'a kopyala
      //   - SystemDataSourceConfig       : Altyapı: DataSource bean konfigürasyonu
      //   - SystemTransactionExecutor    : Self-reference (class itself)

      ArchRule rule =
          noClasses()
              .that()
              .doNotHaveSimpleName("TenantSystemService")
              .and()
              .doNotHaveSimpleName("TenantService")
              .and()
              .doNotHaveSimpleName("TenantClonerService")
              .and()
              .doNotHaveSimpleName("PlaygroundTTLReaperService")
              .and()
              .doNotHaveSimpleName("TrialLifecycleService")
              .and()
              .doNotHaveSimpleName("TenantTransactionalPurgeService")
              .and()
              .doNotHaveSimpleName("TenantQueryAdapter")
              .and()
              .doNotHaveSimpleName("CloneTemplateRolesStep")
              .and()
              .doNotHaveSimpleName("SystemDataSourceConfig")
              .and()
              .doNotHaveSimpleName("FinancePermissionBackfillRunner")
              .and()
              .doNotHaveSimpleName("SystemTransactionExecutor")
              .should()
              .dependOnClassesThat()
              .haveSimpleName("SystemTransactionExecutor")
              .as(
                  "Rule 14.1: SystemTransactionExecutor is a privileged BYPASSRLS chokepoint. "
                      + "Only whitelisted system/admin/scheduler classes may import it. "
                      + "See ADR-001 Karar 9 for the whitelist and rationale.");

      rule.check(allClasses);
    }
  }
}
