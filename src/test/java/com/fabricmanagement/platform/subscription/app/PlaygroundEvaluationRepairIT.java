package com.fabricmanagement.platform.subscription.app;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort;
import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort.SignupCompany;
import com.fabricmanagement.platform.lead.domain.CompanyEvidence;
import com.fabricmanagement.platform.lead.infra.repository.LeadProfileSnapshotRepository;
import com.fabricmanagement.platform.lead.infra.repository.LeadRepository;
import com.fabricmanagement.platform.organization.domain.*;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.subscription.domain.*;
import com.fabricmanagement.platform.subscription.infra.repository.*;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.user.domain.*;
import com.fabricmanagement.platform.user.domain.port.OnboardingEvidencePort;
import com.fabricmanagement.platform.user.infra.repository.*;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real owner-only repair function; fixtures model the pre-05 ignored package choices. */
class PlaygroundEvaluationRepairIT extends AbstractIntegrationTest {
  @Autowired private TenantRepository tenants;
  @Autowired private OrganizationRepository organisations;
  @Autowired private UserRepository users;
  @Autowired private RoleRepository roles;
  @Autowired private OnboardingJourneyRepository journeys;
  @Autowired private LeadRepository leads;
  @Autowired private LeadProfileSnapshotRepository history;
  @Autowired private SubscriptionRepository subscriptions;
  @Autowired private SubscriptionService legacy;
  @Autowired private SignupEvidencePort capture;
  @Autowired private OnboardingEvidencePort retained;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactions;

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void unevidencedLegacyDefaultExpandsOnceAndDoesNotRewriteOriginalRegistrationFacts() {
    var fixture = historical("PLAYGROUND", true, false);
    var original = leads.findBySignupId(fixture.signup()).orElseThrow().getSignupSnapshot();
    repair();
    assertThat(subscriptions.findByTenantId(fixture.tenant()))
        .allSatisfy(
            row -> {
              assertThat(row.getStatus()).isEqualTo(SubscriptionStatus.TRIAL);
              assertThat(row.isLearningAccess()).isTrue();
              assertThat(row.getTrialEndsAt()).isNull();
              assertThat(row.getEvaluationSelectionSource()).isEqualTo("REPAIRED_DEFAULT");
              assertThat(row.getPricingTier()).isNotBlank();
            });
    assertThat(subscriptions.findByTenantId(fixture.tenant()))
        .extracting(Subscription::getOsCode)
        .contains("FabricOS", "YarnOS");
    var ids =
        subscriptions.findByTenantId(fixture.tenant()).stream().map(Subscription::getId).toList();
    long version =
        journeys
            .findByTenantIdAndKind(fixture.tenant(), OnboardingJourney.Kind.COMPANY)
            .orElseThrow()
            .getCommandVersion();
    repair();
    assertThat(subscriptions.findByTenantId(fixture.tenant()))
        .extracting(Subscription::getId)
        .containsExactlyInAnyOrderElementsOf(ids);
    assertThat(
            journeys
                .findByTenantIdAndKind(fixture.tenant(), OnboardingJourney.Kind.COMPANY)
                .orElseThrow()
                .getCommandVersion())
        .isEqualTo(version);
    assertThat(leads.findBySignupId(fixture.signup()).orElseThrow().getSignupSnapshot())
        .isEqualTo(original);
    assertThat(tenants.findById(fixture.tenant()).orElseThrow().getTrialStartedAt()).isNull();
    assertThat(tenants.findById(fixture.tenant()).orElseThrow().getTrialEndsAt()).isNull();
  }

  @Test
  void acceptedOwnerChoiceKeepsDeselectedRowsAndDoesNotReceiveAllPackageDefaults() {
    var fixture = historical("PLAYGROUND", true, true);
    UUID yarn =
        subscriptions.findByTenantIdAndOsCode(fixture.tenant(), "YarnOS").orElseThrow().getId();
    var lead = leads.findBySignupId(fixture.signup()).orElseThrow();
    var before = lead.getAcceptedSnapshot();
    var ignoredLegacySelection =
        new CompanyEvidence(
            before.organisation(),
            before.headquarters(),
            before.registrant(),
            before.contacts(),
            List.of("FabricOS", "YarnOS"),
            before.knownFields());
    var journey =
        journeys
            .findByTenantIdAndKind(fixture.tenant(), OnboardingJourney.Kind.COMPANY)
            .orElseThrow();
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx ->
                retained.correctLegacy(
                    fixture.tenant(),
                    fixture.signup(),
                    journey.getOwnerUserId(),
                    UUID.randomUUID(),
                    ignoredLegacySelection,
                    null));
    repair();
    assertThat(
            subscriptions
                .findByTenantIdAndOsCode(fixture.tenant(), "FabricOS")
                .orElseThrow()
                .isActive())
        .isTrue();
    var paused = subscriptions.findById(yarn).orElseThrow();
    assertThat(paused.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
    assertThat(paused.getEvaluationSelectionSource()).isEqualTo("OWNER");
    repair();
    assertThat(subscriptions.findById(yarn).orElseThrow().getStatus())
        .isEqualTo(SubscriptionStatus.SUSPENDED);
    assertThat(
            leads.findBySignupId(fixture.signup()).orElseThrow().getAcceptedSnapshot().selectedOS())
        .containsExactly("FabricOS");
    assertThat(history.findByLeadIdOrderByRevisionAsc(lead.getId()))
        .extracting(row -> row.getSource())
        .containsExactly(
            "SIGNUP", "STEP_ACCEPTED", "LEGACY_PROFILE_CORRECTION", "EVALUATION_ACCESS_REPAIR");
  }

  @Test
  void cancellationsStayCancelledAndStillSelectedLearningPackagesLosePrematureDeadlines() {
    var fixture = historical("PLAYGROUND", true, false);
    var cancelled = subscriptions.findByTenantIdAndOsCode(fixture.tenant(), "YarnOS").orElseThrow();
    cancelled.cancel();
    subscriptions.saveAndFlush(cancelled);
    repair();
    assertThat(subscriptions.findById(cancelled.getId()).orElseThrow().getStatus())
        .isEqualTo(SubscriptionStatus.CANCELLED);
    var base = subscriptions.findByTenantIdAndOsCode(fixture.tenant(), "FabricOS").orElseThrow();
    assertThat(base.isLearningAccess()).isTrue();
    assertThat(base.getEvaluationSelectionSource()).isEqualTo("REPAIRED_PRESERVED");
    assertThat(base.getTrialEndsAt()).isNull();
  }

  @Test
  void paidTrialAndSalesLedTenantsAreOutsideTheRepairScope() {
    for (String intent : List.of("TRIAL", "SALES_LED", "PLAYGROUND")) {
      var fixture = historical(intent, intent.equals("PLAYGROUND"), false);
      if (intent.equals("PLAYGROUND")) {
        var tenant = tenants.findById(fixture.tenant()).orElseThrow();
        tenant.activate("Paid contract");
        tenants.saveAndFlush(tenant);
        var row = subscriptions.findByTenantIdAndOsCode(fixture.tenant(), "FabricOS").orElseThrow();
        row.activate();
        subscriptions.saveAndFlush(row);
      }
      var before =
          subscriptions.findByTenantId(fixture.tenant()).stream()
              .map(s -> s.getId() + ":" + s.getStatus() + ":" + s.getTrialEndsAt())
              .toList();
      repair();
      assertThat(
              subscriptions.findByTenantId(fixture.tenant()).stream()
                  .map(s -> s.getId() + ":" + s.getStatus() + ":" + s.getTrialEndsAt())
                  .toList())
          .containsExactlyInAnyOrderElementsOf(before);
      assertThat(subscriptions.findByTenantId(fixture.tenant()))
          .allSatisfy(s -> assertThat(s.getEvaluationSelectionSource()).isNull());
    }
  }

  @Test
  void expiredBasePackageNextToACancelledOneIsRestoredAndTheCancellationIsKept() {
    var fixture = historical("PLAYGROUND", true, false);
    // The old registration-time clock expired FabricOS; the owner had cancelled YarnOS themselves.
    jdbc.update(
        "UPDATE common_company.common_subscription SET status = 'EXPIRED', expiry_date = NULL,"
            + " learning_access = false, trial_ends_at = now() - interval '2 days'"
            + " WHERE tenant_id = ? AND os_code = 'FabricOS'",
        fixture.tenant());
    jdbc.update(
        "UPDATE common_company.common_subscription SET status = 'CANCELLED'"
            + " WHERE tenant_id = ? AND os_code = 'YarnOS'",
        fixture.tenant());

    repair();

    var base = subscriptions.findByTenantIdAndOsCode(fixture.tenant(), "FabricOS").orElseThrow();
    assertThat(base.getStatus()).isEqualTo(SubscriptionStatus.TRIAL);
    assertThat(base.isLearningAccess()).isTrue();
    assertThat(base.getTrialEndsAt()).isNull();
    assertThat(base.getEvaluationSelectionSource()).isEqualTo("REPAIRED_PRESERVED");
    assertThat(subscriptions.findByTenantIdAndOsCode(fixture.tenant(), "YarnOS").orElseThrow())
        .extracting(Subscription::getStatus)
        .isEqualTo(SubscriptionStatus.CANCELLED);
    assertThat(reconciliations(fixture, "EXPIRED_PACKAGE_UNPROVEN")).isZero();

    long version = base.getVersion();
    repair();
    assertThat(
            subscriptions
                .findByTenantIdAndOsCode(fixture.tenant(), "FabricOS")
                .orElseThrow()
                .getVersion())
        .as("a second run changes nothing")
        .isEqualTo(version);
  }

  @Test
  void expiryWithoutTheOldClocksFootprintIsLeftAsFoundAndRecordedForTheOperator() {
    var fixture = historical("PLAYGROUND", true, false);
    // A dated expiry is what a paid contract leaves behind, not the registration-time clock.
    jdbc.update(
        "UPDATE common_company.common_subscription SET status = 'EXPIRED', learning_access = false,"
            + " expiry_date = now() - interval '2 days', trial_ends_at = NULL"
            + " WHERE tenant_id = ? AND os_code = 'FabricOS'",
        fixture.tenant());
    jdbc.update(
        "UPDATE common_company.common_subscription SET status = 'CANCELLED'"
            + " WHERE tenant_id = ? AND os_code = 'YarnOS'",
        fixture.tenant());

    repair();
    repair();

    assertThat(subscriptions.findByTenantIdAndOsCode(fixture.tenant(), "FabricOS").orElseThrow())
        .satisfies(
            row -> {
              assertThat(row.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
              assertThat(row.getEvaluationSelectionSource()).isNull();
            });
    assertThat(reconciliations(fixture, "EXPIRED_PACKAGE_UNPROVEN"))
        .as("recorded once however often the repair runs")
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT r.details->'packages'->0->>'osCode' FROM common_company.evaluation_reconciliation r"
                    + " JOIN common_company.common_lead l ON l.id = r.lead_id"
                    + " WHERE l.signup_id = ? AND r.reason = 'EXPIRED_PACKAGE_UNPROVEN'",
                String.class,
                fixture.signup()))
        .isEqualTo("FabricOS");
  }

  @Test
  void anAcceptedSelectionWithoutItsEvidenceIsRecordedInsteadOfAbortingTheMigration() {
    var fixture = historical("PLAYGROUND", true, true);
    jdbc.update(
        "DELETE FROM common_company.lead_profile_snapshot WHERE step_id = 'PLATFORM_MODULES'"
            + " AND lead_id IN (SELECT id FROM common_company.common_lead WHERE signup_id = ?)",
        fixture.signup());

    assertThatCode(this::repair).doesNotThrowAnyException();

    assertThat(reconciliations(fixture, "ACCEPTED_SELECTION_UNRESOLVED")).isEqualTo(1);
    assertThat(subscriptions.findByTenantId(fixture.tenant()))
        .allSatisfy(row -> assertThat(row.getEvaluationSelectionSource()).isNull());
  }

  private long reconciliations(Fixture fixture, String reason) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM common_company.evaluation_reconciliation r"
            + " JOIN common_company.common_lead l ON l.id = r.lead_id"
            + " WHERE l.signup_id = ? AND r.reason = ?",
        Long.class,
        fixture.signup(),
        reason);
  }

  private void repair() {
    jdbc.execute("SELECT common_company.repair_playground_evaluation_packages()");
  }

  private Fixture historical(String intent, boolean demo, boolean ownerChoice) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    var tenant = Tenant.create("Hawthorn " + suffix, "HW-" + suffix);
    tenant.setDemoMode(demo);
    tenant.startTrialNow(
        90); // Historical bug: learning had already begun consuming the real trial.
    UUID id = tenants.saveAndFlush(tenant).getId();
    TenantContext.setCurrentTenantId(id);
    var org =
        organisations.saveAndFlush(
            Organization.create("Hawthorn Weaving Ltd", "GB" + suffix, OrganizationType.WEAVER));
    var role = roles.saveAndFlush(Role.create("Administrator", "ADMIN", "Company setup"));
    User owner = User.create("Eleanor", "Hughes", org.getId());
    owner.setRole(role);
    UUID actor = users.saveAndFlush(owner).getId();
    UUID signup = UUID.randomUUID();
    var facts =
        new CompanyEvidence(
            new CompanyEvidence.Organisation(
                "Hawthorn Weaving Ltd",
                "GB" + suffix,
                OrganizationType.WEAVER,
                null,
                null,
                null,
                null,
                null),
            null,
            new CompanyEvidence.Registrant(
                "Eleanor", "Hughes", "eleanor." + suffix + "@example.com", null),
            null,
            List.of("FabricOS"),
            Set.of("organizationName", "taxId", "selectedOS"));
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              capture.capture(new SignupCompany(signup, id, actor, intent, facts, null));
              var journey = OnboardingJourney.company(id, actor, signup, intent);
              if (ownerChoice) {
                for (var step :
                    List.of(
                        OnboardingStepId.ORGANIZATION_DETAILS,
                        OnboardingStepId.ADDRESS,
                        OnboardingStepId.YOUR_DETAILS,
                        OnboardingStepId.ORGANIZATION_CONTACTS,
                        OnboardingStepId.PLATFORM_MODULES))
                  journey.accept(step, UUID.randomUUID(), true);
                retained.accept(
                    id,
                    signup,
                    actor,
                    UUID.randomUUID(),
                    OnboardingStepId.PLATFORM_MODULES,
                    facts,
                    null);
              }
              journeys.saveAndFlush(journey);
              legacy.createInitialSubscriptions(id, List.of("FabricOS", "YarnOS"), 90);
            });
    return new Fixture(id, signup);
  }

  private record Fixture(UUID tenant, UUID signup) {}
}
