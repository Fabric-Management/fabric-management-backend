package com.fabricmanagement.platform.user.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort;
import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort.SignupCompany;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.lead.app.LeadService;
import com.fabricmanagement.platform.lead.domain.CompanyEvidence;
import com.fabricmanagement.platform.lead.infra.repository.*;
import com.fabricmanagement.platform.organization.domain.*;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.subscription.domain.OSDefinition;
import com.fabricmanagement.platform.subscription.infra.repository.OSDefinitionRepository;
import com.fabricmanagement.platform.tenant.app.TenantTransactionalPurgeService;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.user.domain.*;
import com.fabricmanagement.platform.user.domain.port.OnboardingJourneyProvisioningPort;
import com.fabricmanagement.platform.user.dto.*;
import com.fabricmanagement.platform.user.infra.repository.*;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real persisted commands, permissions, profiles and retention; no paid-subscription fixture. */
@org.springframework.context.annotation.Import(OnboardingJourneyIT.Observation.class)
class OnboardingJourneyIT extends AbstractIntegrationTest {
  @Autowired private OnboardingJourneyService journeys;
  @Autowired private UserOnboardingService legacy;
  @Autowired private TenantRepository tenants;
  @Autowired private OrganizationRepository organizations;
  @Autowired private UserRepository users;
  @Autowired private RoleRepository roles;
  @Autowired private OSDefinitionRepository definitions;
  @Autowired private SignupEvidencePort capture;

  @Autowired
  private com.fabricmanagement.platform.subscription.app.EvaluationSubscriptionService evaluations;

  @Autowired
  private com.fabricmanagement.platform.subscription.app.SubscriptionService subscriptions;

  @Autowired
  private com.fabricmanagement.platform.subscription.infra.repository.SubscriptionRepository
      subscriptionRows;

  @Autowired private OnboardingJourneyProvisioningPort provisioning;
  @Autowired private LeadRepository leads;
  @Autowired private LeadProfileSnapshotRepository snapshots;
  @Autowired private TenantTransactionalPurgeService purge;
  @Autowired private PlatformTransactionManager transactions;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CompletionObserver completions;
  @Autowired private com.fabricmanagement.platform.tenant.app.PlaygroundTTLReaperService expiry;

  @Autowired
  private com.fabricmanagement.common.infrastructure.tenant.TrialLifecyclePort trialLifecycle;

  @Autowired
  private com.fabricmanagement.platform.tenant.app.TrialLifecycleService realTrialLifecycle;

  @Autowired
  private com.fabricmanagement.common.infrastructure.tenant.TenantAccessPort tenantAccess;

  @Autowired
  private com.fabricmanagement.platform.subscription.app.EnhancedSubscriptionService entitlements;

  @Autowired
  private com.fabricmanagement.platform.subscription.infra.repository.FeatureCatalogRepository
      features;

  @Autowired private org.springframework.cache.CacheManager caches;

  @Autowired
  private com.fabricmanagement.platform.subscription.app.EvaluationAccessInvalidator invalidator;

  @MockitoSpyBean private LeadService retained;
  @MockitoSpyBean private OnboardingJourneyRepository journeyRows;

  @AfterEach
  void clear() {
    TenantContext.clear();
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @ValueSource(strings = {"PLAYGROUND", "TRIAL"})
  void acceptedStepsResumeAndRetainCompanyWithoutBuying(String intent) {
    var fixture = register(intent, "eleanor.hughes@example.com");
    var initial = journeys.get();
    assertThat(initial.kind()).isEqualTo(OnboardingJourney.Kind.COMPANY);
    assertThat(initial.steps()).hasSize(6);
    assertThat(initial.firstIncompleteStep()).isEqualTo(OnboardingStepId.ORGANIZATION_DETAILS);
    var current = save(0, organization());
    current = save(current.version(), address("Leeds"));
    UUID leadId = leads.findBySignupId(fixture.signup()).orElseThrow().getId();
    clear(); // New browser/session has no authority over the accepted state.
    authenticate(fixture.tenant(), fixture.owner());
    current = journeys.get();
    assertThat(current.version()).isEqualTo(2);
    assertThat(current.firstIncompleteStep()).isEqualTo(OnboardingStepId.YOUR_DETAILS);
    assertThat(current.acceptedValues().headquarters().city()).isEqualTo("Leeds");
    assertThat(users.findById(fixture.owner()).orElseThrow().hasCompletedOnboarding()).isFalse();
    assertThat(completions.count(fixture.owner())).isZero();
    assertThat(leads.findById(leadId).orElseThrow().getCompanyName())
        .isEqualTo("Hawthorn Weaving Ltd");
    assertThat(
            leads
                .findById(leadId)
                .orElseThrow()
                .getAcceptedSnapshot()
                .organisation()
                .organizationName())
        .isEqualTo("Hawthorn Cloth Ltd");
    assertThat(snapshots.findByLeadIdOrderByRevisionAsc(leadId)).hasSize(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM common_company.common_subscription WHERE tenant_id = ? AND status = 'ACTIVE'",
                Long.class,
                fixture.tenant()))
        .isZero();
    assertCode(
        () -> legacy.completeOnboarding(fixture.owner(), null), "ONBOARDING_JOURNEY_REQUIRED");
    current = save(current.version(), new OnboardingStepPayload.YourDetails("+447700900123"));
    current =
        save(
            current.version(),
            new OnboardingStepPayload.OrganizationContacts(
                "office@hawthorn.example", "+442079460123"));
    current =
        save(
            current.version(),
            new OnboardingStepPayload.PlatformModules(
                List.of("FabricOS"), journeys.get().packages().catalogueVersion()));
    var review =
        new SaveOnboardingStepRequest(
            current.version(),
            UUID.randomUUID(),
            new OnboardingStepPayload.Review(true, current.version()));
    current = journeys.save(OnboardingStepId.REVIEW, review);
    assertThat(current.completedAt()).isNull();
    var finalCommand =
        new CompleteOnboardingJourneyRequest(
            current.version(), UUID.randomUUID(), review.operationId());
    var completed = journeys.complete(finalCommand);
    assertThat(completed.completedAt()).isNotNull();
    assertThat(journeys.complete(finalCommand)).isEqualTo(completed);
    assertThat(completions.count(fixture.owner())).isEqualTo(1);
    assertThat(users.findById(fixture.owner()).orElseThrow().hasCompletedOnboarding()).isTrue();
    assertThat(snapshots.findByLeadIdOrderByRevisionAsc(leadId)).hasSize(8);
    assertThat(leads.findById(leadId).orElseThrow().getSignupSnapshot().registrant().mobilePhone())
        .isNull();
    assertThat(
            leads.findById(leadId).orElseThrow().getAcceptedSnapshot().registrant().mobilePhone())
        .isEqualTo("+447700900123");
    // Final acceptance is not billing consent and creates no subscription.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM common_company.common_subscription WHERE tenant_id = ? AND status = 'ACTIVE'",
                Long.class,
                fixture.tenant()))
        .isZero();
  }

  @Test
  void replayIsFrozenAndChangedOrStaleCommandsDoNotOverwriteNewerEvidence() {
    var fixture = register("PLAYGROUND", "eleanor.hughes@example.com");
    var command = new SaveOnboardingStepRequest(0L, UUID.randomUUID(), organization());
    var first = journeys.save(OnboardingStepId.ORGANIZATION_DETAILS, command);
    var second = save(first.version(), address("Leeds"));
    assertThat(journeys.save(OnboardingStepId.ORGANIZATION_DETAILS, command)).isEqualTo(first);
    assertThat(journeys.get().version()).isEqualTo(second.version());
    assertCode(
        () ->
            journeys.save(
                OnboardingStepId.ADDRESS,
                new SaveOnboardingStepRequest(0L, command.operationId(), address("York"))),
        "ONBOARDING_OPERATION_CONFLICT");
    assertCode(
        () ->
            journeys.save(
                OnboardingStepId.ADDRESS,
                new SaveOnboardingStepRequest(0L, UUID.randomUUID(), address("York"))),
        "ONBOARDING_VERSION_CONFLICT");
    assertThat(
            leads
                .findBySignupId(fixture.signup())
                .orElseThrow()
                .getAcceptedSnapshot()
                .headquarters()
                .city())
        .isEqualTo("Leeds");
  }

  @Test
  void invalidOutOfOrderAndIncompleteFinalCommandsNeverBecomeAccepted() {
    var fixture = register("TRIAL", "eleanor.hughes@example.com");
    assertCode(() -> save(0, address("Leeds")), "ONBOARDING_STEP_OUT_OF_ORDER");
    assertCode(
        () ->
            save(
                0,
                new OnboardingStepPayload.OrganizationDetails(
                    "", "GB123456789", OrganizationType.WEAVER, null, null, "TEXTILE", null, null)),
        "ONBOARDING_STEP_INVALID");
    assertCode(
        () ->
            journeys.complete(
                new CompleteOnboardingJourneyRequest(0L, UUID.randomUUID(), UUID.randomUUID())),
        "ONBOARDING_REVIEW_REQUIRED");
    assertThat(journeys.get().version()).isZero();
    assertThat(leads.findBySignupId(fixture.signup()).orElseThrow().getAcceptedRevision()).isZero();
  }

  @Test
  void leadWriteFailureRollsBackStepAndLiveProfileAndRetryCreatesOneHistoryVersion() {
    var fixture = register("PLAYGROUND", "eleanor.hughes@example.com");
    UUID lead = leads.findBySignupId(fixture.signup()).orElseThrow().getId();
    doAnswer(
            call -> {
              call.callRealMethod();
              snapshots.flush();
              throw new PlatformDomainException(
                  "Injected retention failure", "LEAD_ACCEPT_FAILED", 503);
            })
        .when(leadSpy())
        .accept(any(), any(), any(), any(), any(), any(), any());
    var command = new SaveOnboardingStepRequest(0L, UUID.randomUUID(), organization());
    try {
      assertCode(
          () -> journeys.save(OnboardingStepId.ORGANIZATION_DETAILS, command),
          "LEAD_ACCEPT_FAILED");
      assertThat(journeys.get().version()).isZero();
      assertThat(organizations.findById(fixture.organization()).orElseThrow().getName())
          .isEqualTo("Hawthorn Weaving Ltd");
      assertThat(snapshots.findByLeadIdOrderByRevisionAsc(lead)).hasSize(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM common_user.onboarding_operation WHERE tenant_id = ?",
                  Long.class,
                  fixture.tenant()))
          .isZero();
    } finally {
      reset(retained);
    }
    journeys.save(OnboardingStepId.ORGANIZATION_DETAILS, command);
    journeys.save(OnboardingStepId.ORGANIZATION_DETAILS, command);
    assertThat(snapshots.findByLeadIdOrderByRevisionAsc(lead)).hasSize(2);
  }

  @Test
  void finalRetentionFailureRollsBackUserMarkerJourneyAndCommittedEventThenRetriesOnce() {
    var fixture = register("TRIAL", "eleanor.hughes@example.com");
    var review = acceptThroughReview();
    UUID lead = leads.findBySignupId(fixture.signup()).orElseThrow().getId();
    var command =
        new CompleteOnboardingJourneyRequest(
            journeys.get().version(), UUID.randomUUID(), review.operationId());
    doAnswer(
            call -> {
              call.callRealMethod();
              snapshots.flush();
              throw new PlatformDomainException(
                  "Injected final retention failure", "LEAD_ACCEPT_FAILED", 503);
            })
        .when(leadSpy())
        .accept(any(), any(), any(), any(), any(), any(), any());
    try {
      assertCode(() -> journeys.complete(command), "LEAD_ACCEPT_FAILED");
      assertThat(users.findById(fixture.owner()).orElseThrow().hasCompletedOnboarding()).isFalse();
      assertThat(journeys.get().completedAt()).isNull();
      assertThat(journeys.get().version()).isEqualTo(6);
      assertThat(snapshots.findByLeadIdOrderByRevisionAsc(lead)).hasSize(7);
      assertThat(completions.count(fixture.owner())).isZero();
    } finally {
      reset(retained);
    }
    journeys.complete(command);
    journeys.complete(command);
    assertThat(snapshots.findByLeadIdOrderByRevisionAsc(lead)).hasSize(8);
    assertThat(completions.count(fixture.owner())).isEqualTo(1);
  }

  @Test
  void earlierCorrectionInvalidatesOnlyReviewAndExplicitClearRemainsKnownAbsence() {
    var fixture = register("PLAYGROUND", "eleanor.hughes@example.com");
    acceptThroughReview();
    long version = journeys.get().version();
    var changed =
        save(
            version,
            new OnboardingStepPayload.OrganizationDetails(
                "Hawthorn Cloth Ltd",
                "GB123456789",
                OrganizationType.WEAVER,
                "",
                null,
                "TEXTILE",
                null,
                null));
    assertThat(changed.requiresReconfirmation()).containsExactly(OnboardingStepId.REVIEW);
    assertThat(changed.firstIncompleteStep()).isEqualTo(OnboardingStepId.REVIEW);
    assertThat(changed.acceptedValues().knownFields()).contains("legalName");
    assertThat(changed.acceptedValues().organisation().legalName()).isNull();
    assertThat(changed.acceptedValues().organisation().website())
        .isEqualTo("https://hawthorn.example.com");
    assertThat(
            leads.findBySignupId(fixture.signup()).orElseThrow().getSignupSnapshot().knownFields())
        .doesNotContain("legalName");
    assertCode(
        () ->
            journeys.complete(
                new CompleteOnboardingJourneyRequest(
                    changed.version(), UUID.randomUUID(), UUID.randomUUID())),
        "ONBOARDING_REVIEW_REQUIRED");
  }

  @Test
  void legacyCorrectionCannotFinalizeAnEarlierPendingReview() {
    var fixture = register("PLAYGROUND", "eleanor.hughes@example.com");
    var initialReview = acceptThroughReview();
    var completed =
        journeys.complete(
            new CompleteOnboardingJourneyRequest(
                journeys.get().version(), UUID.randomUUID(), initialReview.operationId()));
    var changed = save(completed.version(), address("York"));
    var pendingReview =
        new SaveOnboardingStepRequest(
            changed.version(),
            UUID.randomUUID(),
            new OnboardingStepPayload.Review(true, changed.version()));
    journeys.save(OnboardingStepId.REVIEW, pendingReview);
    // Identical legacy data does not invalidate a review or advance the business version.
    long reviewedVersion = journeys.get().version();
    legacy.completeOnboarding(
        fixture.owner(), CompleteOnboardingRequest.builder().city("York").build());
    assertThat(journeys.get().version()).isEqualTo(reviewedVersion);
    legacy.completeOnboarding(
        fixture.owner(), CompleteOnboardingRequest.builder().city("Bath").build());
    var corrected = journeys.get();
    assertThat(corrected.version()).isEqualTo(reviewedVersion + 1);
    assertThat(corrected.firstIncompleteStep()).isEqualTo(OnboardingStepId.REVIEW);
    assertThat(corrected.acceptedValues().headquarters().city()).isEqualTo("Bath");
    UUID lead = leads.findBySignupId(fixture.signup()).orElseThrow().getId();
    int historySize = snapshots.findByLeadIdOrderByRevisionAsc(lead).size();
    assertCode(
        () ->
            journeys.complete(
                new CompleteOnboardingJourneyRequest(
                    corrected.version(), UUID.randomUUID(), pendingReview.operationId())),
        "ONBOARDING_REVIEW_REQUIRED");
    assertThat(snapshots.findByLeadIdOrderByRevisionAsc(lead)).hasSize(historySize);
    var renewed =
        new SaveOnboardingStepRequest(
            corrected.version(),
            UUID.randomUUID(),
            new OnboardingStepPayload.Review(true, corrected.version()));
    var accepted = journeys.save(OnboardingStepId.REVIEW, renewed);
    var finalState =
        journeys.complete(
            new CompleteOnboardingJourneyRequest(
                accepted.version(), UUID.randomUUID(), renewed.operationId()));
    assertThat(finalState.completedAt()).isEqualTo(completed.completedAt());
    assertThat(completions.count(fixture.owner())).isEqualTo(1);
    assertThat(snapshots.findByLeadIdOrderByRevisionAsc(lead)).hasSize(historySize + 2);
  }

  @Test
  void trialOrDormancyStyleExpiryDoesNotRemoveRetainedEvidence() {
    var fixture = register("PLAYGROUND", "eleanor.hughes@example.com");
    save(0, organization());
    UUID lead = leads.findBySignupId(fixture.signup()).orElseThrow().getId();
    jdbc.update(
        "UPDATE common_tenant.common_tenant SET type = 'PLAYGROUND', created_at = now() - interval '365 days' WHERE id = ?",
        fixture.tenant());
    expiry.reapExpiredPlaygrounds();
    assertThat(tenants.findById(fixture.tenant()).orElseThrow().getIsActive()).isFalse();
    assertThat(
            leads
                .findById(lead)
                .orElseThrow()
                .getAcceptedSnapshot()
                .organisation()
                .organizationName())
        .isEqualTo("Hawthorn Cloth Ltd");
    assertThat(snapshots.findByLeadIdOrderByRevisionAsc(lead)).hasSize(2);
  }

  @Test
  void ownerAndAdministratorRaceReturnsOneVersionConflictWithCurrentVersion() throws Exception {
    var fixture = register("PLAYGROUND", "eleanor.hughes@example.com");
    User colleague = User.create("Oliver", "Reed", fixture.organization());
    colleague.setRole(users.findById(fixture.owner()).orElseThrow().getRole());
    UUID colleagueId = users.saveAndFlush(colleague).getId();
    var bothLoaded = new CountDownLatch(2);
    // The repository spy delegates to the real repository; callRealMethod() would hit an interface.
    org.mockito.stubbing.Answer<?> realRead =
        mockingDetails(journeyRows).getMockCreationSettings().getDefaultAnswer();
    doAnswer(
            call -> {
              var read = realRead.answer(call);
              bothLoaded.countDown();
              if (!bothLoaded.await(10, TimeUnit.SECONDS))
                throw new AssertionError("Both stale journey reads required");
              return read;
            })
        .when(journeyRows)
        .findByTenantIdAndKind(fixture.tenant(), OnboardingJourney.Kind.COMPANY);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var tasks =
          List.of(fixture.owner(), colleagueId).stream()
              .map(
                  actor ->
                      pool.submit(
                          () -> {
                            authenticate(fixture.tenant(), actor);
                            try {
                              save(0, organization());
                              return "OK";
                            } catch (PlatformDomainException failure) {
                              assertThat(failure.getDetails().get("currentVersion")).isEqualTo(1L);
                              return failure.getErrorCode();
                            } finally {
                              clear();
                            }
                          }))
              .toList();
      assertThat(
              List.of(
                  tasks.get(0).get(30, TimeUnit.SECONDS), tasks.get(1).get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("OK", "ONBOARDING_VERSION_CONFLICT");
    } finally {
      reset(journeyRows);
    }
    assertThat(journeys.get().version()).isEqualTo(1);
    assertThat(leads.findBySignupId(fixture.signup()).orElseThrow().getAcceptedRevision())
        .isEqualTo(1);
  }

  @Test
  void sameEmailAcrossCompaniesAndUnprivilegedMembershipNeverCrossEvidenceBoundaries() {
    var first = register("TRIAL", "eleanor.hughes@example.com");
    var second = register("PLAYGROUND", "eleanor.hughes@example.com");
    save(0, organization());
    assertThat(leads.findBySignupId(second.signup()).orElseThrow().getAcceptedRevision())
        .isEqualTo(1);
    assertThat(leads.findBySignupId(first.signup()).orElseThrow().getAcceptedRevision()).isZero();
    authenticate(first.tenant(), second.owner());
    assertCode(journeys::get, "ONBOARDING_MEMBERSHIP_REQUIRED");
    authenticate(second.tenant(), second.owner());
    Role worker =
        roles.saveAndFlush(Role.create("Warehouse worker", "WORKER", "Warehouse operations"));
    User invited = User.create("Sophie", "Bennett", second.organization());
    invited.setRole(worker);
    UUID workerId = users.saveAndFlush(invited).getId();
    authenticate(second.tenant(), workerId);
    var personal = journeys.get();
    assertThat(personal.kind()).isEqualTo(OnboardingJourney.Kind.PERSONAL);
    assertThat(personal.acceptedValues()).isNull();
    assertCode(() -> save(0, organization()), "ONBOARDING_STEP_INVALID");
    var accepted = save(0, new OnboardingStepPayload.YourDetails("+447700900456"));
    journeys.complete(
        new CompleteOnboardingJourneyRequest(accepted.version(), UUID.randomUUID(), null));
    assertThat(
            leads
                .findBySignupId(second.signup())
                .orElseThrow()
                .getAcceptedSnapshot()
                .registrant()
                .mobilePhone())
        .isNull();
  }

  @Test
  void personalMemberWithoutCompanyJourneyCanResumeAfterReceivingCompanyAuthority() {
    var fixture = register("TRIAL", "eleanor.hughes@example.com");
    jdbc.update("DELETE FROM common_user.onboarding_journey WHERE tenant_id = ?", fixture.tenant());
    Role worker =
        roles.saveAndFlush(Role.create("Warehouse worker", "WORKER", "Warehouse operations"));
    User invited = User.create("Sophie", "Bennett", fixture.organization());
    invited.setRole(worker);
    UUID member = users.saveAndFlush(invited).getId();
    authenticate(fixture.tenant(), member);
    assertThat(journeys.get().kind()).isEqualTo(OnboardingJourney.Kind.PERSONAL);
    save(0, new OnboardingStepPayload.YourDetails("+447700900456"));
    invited = users.findById(member).orElseThrow();
    invited.setRole(users.findById(fixture.owner()).orElseThrow().getRole());
    users.saveAndFlush(invited);
    assertThat(journeys.get().kind()).isEqualTo(OnboardingJourney.Kind.PERSONAL);
    legacy.completeOnboarding(member, null);
    assertThat(users.findById(member).orElseThrow().hasCompletedOnboarding()).isTrue();
    assertCode(
        () ->
            legacy.completeOnboarding(
                member,
                CompleteOnboardingRequest.builder().organizationName("Willow Cloth Ltd").build()),
        "ONBOARDING_SIGNUP_LINK_UNRESOLVED");
    assertThat(organizations.findById(fixture.organization()).orElseThrow().getName())
        .isEqualTo("Hawthorn Weaving Ltd");
    assertThat(leads.findBySignupId(fixture.signup()).orElseThrow().getAcceptedRevision()).isZero();
  }

  @Test
  void newCataloguePackageDefaultsToRealLearningAccessAndOwnerChoicesPersistWithoutDuplicateRows() {
    var offered = offerPackage(true, true);
    var internal = offerPackage(true, false);
    var inactive = offerPackage(false, true);
    var fixture = register("PLAYGROUND", "eleanor.hughes@example.com");
    var initial = journeys.get();
    assertThat(initial.packages().selectedCodes())
        .contains("FabricOS", offered.getOsCode())
        .doesNotContain(internal.getOsCode(), inactive.getOsCode());
    assertThat(initial.packages().source()).isEqualTo("SERVER_DEFAULT");
    var originalRow =
        subscriptionRows
            .findByTenantIdAndOsCode(fixture.tenant(), offered.getOsCode())
            .orElseThrow();
    assertThat(originalRow.getPricingTier()).isEqualTo(offered.getDefaultTier());
    assertThat(originalRow.getStatus())
        .isEqualTo(com.fabricmanagement.platform.subscription.domain.SubscriptionStatus.TRIAL);
    assertThat(entitlements.hasActiveSubscription(fixture.tenant(), offered.getOsCode())).isTrue();
    var feature =
        com.fabricmanagement.platform.subscription.domain.FeatureCatalog.builder()
            .featureId("warehouse.operations." + UUID.randomUUID().toString().substring(0, 8))
            .osCode(offered.getOsCode())
            .featureName("Warehouse operations")
            .availableInTiers(List.of("Enterprise"))
            .build();
    feature.setTenantId(TenantContext.SYSTEM_TENANT_ID);
    features.saveAndFlush(feature);
    assertThat(entitlements.hasFeature(fixture.tenant(), feature.getFeatureId())).isTrue();
    assertThat(completions.paidActivations(fixture.tenant())).isZero();
    // A date well beyond the old independent package trial window must not close learning access.
    assertThat(
            subscriptionRows.findActiveSubscriptionByOsCode(
                fixture.tenant(),
                offered.getOsCode(),
                java.time.Instant.now().plusSeconds(365L * 86400)))
        .isPresent();
    acceptThroughReview(); // The owner explicitly deselects optional packages.
    assertThat(entitlements.hasActiveSubscription(fixture.tenant(), offered.getOsCode())).isFalse();
    assertThat(entitlements.hasFeature(fixture.tenant(), feature.getFeatureId())).isFalse();
    var current = journeys.get();
    var choose =
        new SaveOnboardingStepRequest(
            current.version(),
            UUID.randomUUID(),
            new OnboardingStepPayload.PlatformModules(
                List.of("FabricOS", offered.getOsCode()), current.packages().catalogueVersion()));
    var chosen = journeys.save(OnboardingStepId.PLATFORM_MODULES, choose);
    assertThat(journeys.save(OnboardingStepId.PLATFORM_MODULES, choose)).isEqualTo(chosen);
    clear();
    authenticate(fixture.tenant(), fixture.owner());
    assertThat(journeys.get().packages().selectedCodes())
        .containsExactlyInAnyOrder("FabricOS", offered.getOsCode());
    assertThat(journeys.get().packages().source()).isEqualTo("OWNER");
    assertThat(
            subscriptionRows
                .findByTenantIdAndOsCode(fixture.tenant(), offered.getOsCode())
                .orElseThrow()
                .getId())
        .isEqualTo(originalRow.getId());
    assertThat(
            leads.findBySignupId(fixture.signup()).orElseThrow().getAcceptedSnapshot().selectedOS())
        .containsExactly("FabricOS", offered.getOsCode());
    assertThat(
            subscriptionRows.findByTenantId(fixture.tenant()).stream()
                .filter(s -> s.getOsCode().equals(offered.getOsCode())))
        .hasSize(1);
  }

  @Test
  void companyAdministratorCannotChangeTheRegistrantEvaluationSelection() {
    var fixture = register("PLAYGROUND", "eleanor.hughes@example.com");
    acceptThroughReview();
    var before = journeys.get();
    User colleague = User.create("Oliver", "Reed", fixture.organization());
    colleague.setRole(users.findById(fixture.owner()).orElseThrow().getRole());
    UUID colleagueId = users.saveAndFlush(colleague).getId();
    authenticate(fixture.tenant(), colleagueId);
    assertThat(journeys.get().selectModules().allowed()).isFalse();
    assertThat(journeys.get().packages().editable()).isFalse();
    assertCode(
        () ->
            save(
                before.version(),
                new OnboardingStepPayload.PlatformModules(
                    List.of("FabricOS"), before.packages().catalogueVersion())),
        "ONBOARDING_REGISTRANT_REQUIRED");
    authenticate(fixture.tenant(), fixture.owner());
    assertThat(journeys.get().version()).isEqualTo(before.version());
  }

  @Test
  void staleCatalogueUnknownCodesAndPaidTenantsCannotPartiallyGrantPackages() {
    var fixture = register("PLAYGROUND", "eleanor.hughes@example.com");
    acceptThroughReview();
    var previous = journeys.get();
    var offered = offerPackage(true, true);
    assertCode(
        () ->
            save(
                previous.version(),
                new OnboardingStepPayload.PlatformModules(
                    List.of("FabricOS", offered.getOsCode()),
                    previous.packages().catalogueVersion())),
        "ONBOARDING_CATALOGUE_CONFLICT");
    var current = journeys.get();
    assertCode(
        () ->
            save(
                current.version(),
                new OnboardingStepPayload.PlatformModules(
                    List.of("FabricOS", "UnknownPackage"), current.packages().catalogueVersion())),
        "ONBOARDING_PACKAGES_INVALID");
    assertCode(
        () ->
            save(
                current.version(),
                new OnboardingStepPayload.PlatformModules(
                    List.of("FabricOS", "FabricOS"), current.packages().catalogueVersion())),
        "ONBOARDING_PACKAGES_INVALID");
    assertThat(subscriptionRows.findByTenantIdAndOsCode(fixture.tenant(), offered.getOsCode()))
        .isEmpty();
    Tenant tenant = tenants.findById(fixture.tenant()).orElseThrow();
    tenant.activate("Paid contract");
    tenants.saveAndFlush(tenant);
    assertThat(journeys.get().selectModules().allowed()).isFalse();
    assertCode(
        () ->
            save(
                current.version(),
                new OnboardingStepPayload.PlatformModules(
                    List.of("FabricOS", offered.getOsCode()),
                    current.packages().catalogueVersion())),
        "ONBOARDING_EVALUATION_UNAVAILABLE");
    assertThat(journeys.get().version()).isEqualTo(previous.version());
    assertThat(
            leads.findBySignupId(fixture.signup()).orElseThrow().getAcceptedSnapshot().selectedOS())
        .containsExactly("FabricOS");
  }

  @Test
  void retentionFailureRollsBackModuleAccessChoiceReviewAndReceipt() {
    var offered = offerPackage(true, true);
    var fixture = register("PLAYGROUND", "eleanor.hughes@example.com");
    acceptThroughReview();
    var before = journeys.get();
    UUID lead = leads.findBySignupId(fixture.signup()).orElseThrow().getId();
    int history = snapshots.findByLeadIdOrderByRevisionAsc(lead).size();
    doAnswer(
            call -> {
              call.callRealMethod();
              snapshots.flush();
              throw new PlatformDomainException(
                  "Injected retention failure", "LEAD_ACCEPT_FAILED", 503);
            })
        .when(leadSpy())
        .accept(any(), any(), any(), any(), any(), any(), any());
    var command =
        new SaveOnboardingStepRequest(
            before.version(),
            UUID.randomUUID(),
            new OnboardingStepPayload.PlatformModules(
                List.of("FabricOS", offered.getOsCode()), before.packages().catalogueVersion()));
    try {
      assertCode(
          () -> journeys.save(OnboardingStepId.PLATFORM_MODULES, command), "LEAD_ACCEPT_FAILED");
      assertThat(journeys.get()).isEqualTo(before);
      assertThat(entitlements.hasActiveSubscription(fixture.tenant(), offered.getOsCode()))
          .isFalse();
      assertThat(snapshots.findByLeadIdOrderByRevisionAsc(lead)).hasSize(history);
    } finally {
      reset(retained);
    }
    journeys.save(OnboardingStepId.PLATFORM_MODULES, command);
    journeys.save(OnboardingStepId.PLATFORM_MODULES, command);
    assertThat(snapshots.findByLeadIdOrderByRevisionAsc(lead)).hasSize(history + 1);
    assertThat(entitlements.hasActiveSubscription(fixture.tenant(), offered.getOsCode())).isTrue();
  }

  @Test
  void learningNeverStartsRealTrialAndGoRealAlignsTenantAndSelectedPackageDeadlines() {
    var fixture = register("PLAYGROUND", "eleanor.hughes@example.com");
    // Password setup invokes this same lifecycle port; its database guard excludes demoMode.
    assertThat(trialLifecycle.startSelfServiceTrialIfNeeded(fixture.tenant())).isZero();
    acceptThroughReview();
    assertThat(tenants.findById(fixture.tenant()).orElseThrow().getTrialStartedAt()).isNull();
    assertThat(tenants.findById(fixture.tenant()).orElseThrow().getTrialEndsAt()).isNull();
    var result = purge.goReal(fixture.tenant());
    var base = subscriptionRows.findByTenantIdAndOsCode(fixture.tenant(), "FabricOS").orElseThrow();
    assertThat(base.isLearningAccess()).isFalse();
    assertThat(
            subscriptionRows
                .findExpiredButNotExpiredStatus(
                    result.trialEndsAt().plusSeconds(1),
                    org.springframework.data.domain.PageRequest.of(0, 1000))
                .getContent())
        .extracting(row -> row.getId())
        .doesNotContain(base.getId());
    assertThat(base.getTrialEndsAt())
        .isEqualTo(result.trialEndsAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    assertThat(
            subscriptionRows.findActiveSubscriptionByOsCode(
                fixture.tenant(), "FabricOS", result.trialEndsAt().plusSeconds(1)))
        .isEmpty();
    assertThat(result.trialEndsAt()).isEqualTo(result.trialStartedAt().plusSeconds(90L * 86400));
    assertCode(
        () ->
            save(
                journeys.get().version(),
                new OnboardingStepPayload.PlatformModules(
                    List.of("FabricOS"), journeys.get().packages().catalogueVersion())),
        "ONBOARDING_EVALUATION_UNAVAILABLE");
  }

  @Test
  void realTrialSlidingAndExpiryKeepPackageAccessAndReadOnlyEnforcementAligned() {
    var fixture = register("PLAYGROUND", "eleanor.hughes@example.com");
    purge.goReal(fixture.tenant());
    jdbc.update(
        "UPDATE common_tenant.common_tenant SET trial_started_at = now() - interval '30 days', "
            + "last_activity_at = now() - interval '1 day' WHERE id = ?",
        fixture.tenant());
    realTrialLifecycle.refreshTrialWindows();
    var tenant = tenants.findById(fixture.tenant()).orElseThrow();
    var base = subscriptionRows.findByTenantIdAndOsCode(fixture.tenant(), "FabricOS").orElseThrow();
    assertThat(base.getTrialEndsAt()).isEqualTo(tenant.getTrialEndsAt());
    assertThat(entitlements.hasActiveSubscription(fixture.tenant(), "FabricOS")).isTrue();
    assertThat(tenantAccess.isWritable(fixture.tenant())).isTrue();
    jdbc.update(
        "UPDATE common_tenant.common_tenant SET trial_started_at = now() - interval '200 days', "
            + "last_activity_at = now() - interval '100 days' WHERE id = ?",
        fixture.tenant());
    realTrialLifecycle.refreshTrialWindows();
    assertThat(tenants.findById(fixture.tenant()).orElseThrow().getStatus())
        .isEqualTo(com.fabricmanagement.platform.tenant.domain.TenantStatus.EXPIRED);
    assertThat(tenantAccess.isWritable(fixture.tenant())).isFalse();
    assertThat(entitlements.hasActiveSubscription(fixture.tenant(), "FabricOS")).isFalse();
  }

  @Test
  void derivedAccessCachesAreInvalidatedAfterCommitAndPreservedOnRollback() {
    var cache = caches.getCache("subscription-check");
    assertThat(cache).isNotNull();
    String key = UUID.randomUUID().toString();
    cache.put(key, true);
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              invalidator.afterCommit();
              assertThat(cache.get(key)).isNotNull();
              tx.setRollbackOnly();
            });
    assertThat(cache.get(key)).isNotNull();
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              invalidator.afterCommit();
              assertThat(cache.get(key)).isNotNull();
            });
    assertThat(cache.get(key)).isNull();
  }

  @Test
  void concurrentOwnerModuleCommandsAcceptOnlyOneExpectedJourneyVersion() throws Exception {
    var fixture = register("PLAYGROUND", "eleanor.hughes@example.com");
    acceptThroughReview();
    var before = journeys.get();
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var tasks =
          java.util.stream.IntStream.range(0, 2)
              .mapToObj(
                  ignored ->
                      pool.submit(
                          () -> {
                            authenticate(fixture.tenant(), fixture.owner());
                            try {
                              if (!start.await(10, TimeUnit.SECONDS))
                                throw new AssertionError("Concurrent start timed out");
                              save(
                                  before.version(),
                                  new OnboardingStepPayload.PlatformModules(
                                      List.of("FabricOS"), before.packages().catalogueVersion()));
                              return "OK";
                            } catch (PlatformDomainException failure) {
                              return failure.getErrorCode();
                            } finally {
                              clear();
                            }
                          }))
              .toList();
      start.countDown();
      assertThat(
              List.of(
                  tasks.get(0).get(30, TimeUnit.SECONDS), tasks.get(1).get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("OK", "ONBOARDING_VERSION_CONFLICT");
    }
    assertThat(journeys.get().version()).isEqualTo(before.version() + 1);
  }

  private OSDefinition offerPackage(boolean active, boolean available) {
    OSDefinition definition =
        OSDefinition.builder()
            .osCode("WarehouseOS" + UUID.randomUUID().toString().substring(0, 8))
            .osName("Warehouse evaluation")
            .defaultTier("Standard")
            .availableTiers(List.of("Standard", "Enterprise"))
            .evaluationAvailable(available)
            .build();
    definition.setTenantId(TenantContext.SYSTEM_TENANT_ID);
    definition.setIsActive(active);
    return definitions.saveAndFlush(definition);
  }

  @Test
  void resetGoRealAndPhysicalTenantRemovalRetainEvidenceAndClearOnlyOperationalLink() {
    var fixture = register("PLAYGROUND", "eleanor.hughes@example.com");
    save(0, organization());
    UUID id = leads.findBySignupId(fixture.signup()).orElseThrow().getId();
    var original = leads.findById(id).orElseThrow().getSignupSnapshot();
    var accepted = leads.findById(id).orElseThrow().getAcceptedSnapshot();
    var historyIds =
        snapshots.findByLeadIdOrderByRevisionAsc(id).stream().map(row -> row.getId()).toList();
    purge.purgeDemoData(fixture.tenant());
    assertThat(journeys.get().version()).isEqualTo(1);
    purge.goReal(fixture.tenant());
    assertThat(leads.findById(id).orElseThrow().getAcceptedSnapshot()).isEqualTo(accepted);
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              jdbc.update(
                  "DELETE FROM common_user.common_user WHERE tenant_id = ?", fixture.tenant());
              jdbc.update(
                  "DELETE FROM common_user.common_role WHERE tenant_id = ?", fixture.tenant());
              jdbc.update(
                  "DELETE FROM common_company.common_organization WHERE tenant_id = ?",
                  fixture.tenant());
              jdbc.update(
                  "DELETE FROM common_company.common_subscription WHERE tenant_id = ?",
                  fixture.tenant());
              jdbc.update("DELETE FROM common_tenant.common_tenant WHERE id = ?", fixture.tenant());
            });
    var surviving = leads.findById(id).orElseThrow();
    assertThat(surviving.getTrialTenantId()).isNull();
    assertThat(surviving.getSignupTenantId()).isEqualTo(fixture.tenant());
    assertThat(surviving.getSignupId()).isEqualTo(fixture.signup());
    assertThat(surviving.getSignupSnapshot()).isEqualTo(original);
    assertThat(surviving.getAcceptedSnapshot()).isEqualTo(accepted);
    assertThat(snapshots.findByLeadIdOrderByRevisionAsc(id))
        .extracting(row -> row.getId())
        .containsExactlyElementsOf(historyIds);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM common_user.onboarding_journey WHERE tenant_id = ?",
                Long.class,
                fixture.tenant()))
        .isZero();
  }

  /**
   * The spy behind LeadService's transactional proxy. Stubbing through the proxy would run its
   * MANDATORY transaction interceptor outside any transaction, fail, and leave the half-finished
   * stubbing on the thread for the next test to trip over.
   */
  private LeadService leadSpy() {
    return org.springframework.test.util.AopTestUtils.getUltimateTargetObject(retained);
  }

  private OnboardingJourneyResponse save(long version, OnboardingStepPayload payload) {
    return journeys.save(
        payload.id(), new SaveOnboardingStepRequest(version, UUID.randomUUID(), payload));
  }

  private SaveOnboardingStepRequest acceptThroughReview() {
    var current = save(0, organization());
    current = save(current.version(), address("Leeds"));
    current = save(current.version(), new OnboardingStepPayload.YourDetails("+447700900123"));
    current = save(current.version(), new OnboardingStepPayload.OrganizationContacts(null, null));
    current =
        save(
            current.version(),
            new OnboardingStepPayload.PlatformModules(
                List.of("FabricOS"), journeys.get().packages().catalogueVersion()));
    var review =
        new SaveOnboardingStepRequest(
            current.version(),
            UUID.randomUUID(),
            new OnboardingStepPayload.Review(true, current.version()));
    journeys.save(OnboardingStepId.REVIEW, review);
    return review;
  }

  private OnboardingStepPayload.OrganizationDetails organization() {
    return new OnboardingStepPayload.OrganizationDetails(
        "Hawthorn Cloth Ltd",
        "GB123456789",
        OrganizationType.WEAVER,
        "Hawthorn Cloth Limited",
        "11223344",
        "TEXTILE",
        "https://hawthorn.example.com",
        "British woven cloth");
  }

  private OnboardingStepPayload.Address address(String city) {
    return new OnboardingStepPayload.Address(
        "14 Mill Street", null, city, null, null, "LS1 4AB", "United Kingdom", "Mill office");
  }

  private Fixture register(String intent, String email) {
    clear();
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    Tenant tenant = Tenant.create("Hawthorn Weaving " + suffix, "HW-" + suffix);
    tenant.setDemoMode(intent.equals("PLAYGROUND"));
    UUID tenantId = tenants.saveAndFlush(tenant).getId();
    TenantContext.setCurrentTenantId(tenantId);
    Organization company =
        organizations.saveAndFlush(
            Organization.create("Hawthorn Weaving Ltd", "GB" + suffix, OrganizationType.WEAVER));
    Role admin = roles.saveAndFlush(Role.create("Administrator", "ADMIN", "Company setup"));
    User member = User.create("Eleanor", "Hughes", company.getId());
    member.setRole(admin);
    UUID actor = users.saveAndFlush(member).getId();
    UUID signup = UUID.randomUUID();
    authenticate(tenantId, actor);
    if (definitions.findByOsCode("FabricOS").isEmpty()) {
      OSDefinition definition =
          OSDefinition.builder().osCode("FabricOS").osName("FabricOS").defaultTier("Base").build();
      definition.setTenantId(TenantContext.SYSTEM_TENANT_ID);
      definitions.saveAndFlush(definition);
    }
    var evidence =
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
            new CompanyEvidence.Registrant("Eleanor", "Hughes", email, null),
            null,
            List.of("FabricOS"),
            Set.of(
                "organizationName",
                "taxId",
                "organizationType",
                "firstName",
                "lastName",
                "workEmail",
                "selectedOS"));
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              capture.capture(new SignupCompany(signup, tenantId, actor, intent, evidence, null));
              provisioning.provision(tenantId, actor, signup, intent);
              if (intent.equals("PLAYGROUND")) evaluations.initialisePlayground(tenantId);
              else subscriptions.createInitialSubscriptions(tenantId, List.of("FabricOS"), 90);
            });
    if (intent.equals("TRIAL")) trialLifecycle.startSelfServiceTrialIfNeeded(tenantId);
    return new Fixture(tenantId, company.getId(), actor, signup);
  }

  private void authenticate(UUID tenant, UUID user) {
    TenantContext.setCurrentTenantId(tenant);
    TenantContext.setCurrentUserId(user);
    var identity = new AuthenticatedUserContext(user, "ADMIN", List.of(), null, tenant);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(identity, null, List.of()));
  }

  private void assertCode(Runnable command, String code) {
    assertThatThrownBy(command::run)
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(code));
  }

  private record Fixture(UUID tenant, UUID organization, UUID owner, UUID signup) {}

  @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
  static class Observation {
    @org.springframework.context.annotation.Bean
    CompletionObserver completionObserver() {
      return new CompletionObserver();
    }
  }

  static class CompletionObserver {
    private final ConcurrentMap<UUID, Integer> counts = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Integer> paidActivations = new ConcurrentHashMap<>();

    @org.springframework.transaction.event.TransactionalEventListener(
        phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void activated(
        com.fabricmanagement.platform.subscription.domain.event.SubscriptionActivatedEvent event) {
      paidActivations.merge(event.getTenantId(), 1, Integer::sum);
    }

    @org.springframework.transaction.event.TransactionalEventListener(
        phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void completed(
        com.fabricmanagement.platform.user.domain.event.UserOnboardingCompletedEvent event) {
      counts.merge(event.getUserId(), 1, Integer::sum);
    }

    int count(UUID user) {
      return counts.getOrDefault(user, 0);
    }

    int paidActivations(UUID tenant) {
      return paidActivations.getOrDefault(tenant, 0);
    }
  }
}
