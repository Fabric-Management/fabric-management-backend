package com.fabricmanagement.platform.lead.app;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.platform.auth.domain.*;
import com.fabricmanagement.platform.auth.infra.repository.RegistrationTokenRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.domain.*;
import com.fabricmanagement.platform.communication.infra.repository.AddressRepository;
import com.fabricmanagement.platform.lead.domain.Lead;
import com.fabricmanagement.platform.lead.infra.repository.*;
import com.fabricmanagement.platform.organization.domain.*;
import com.fabricmanagement.platform.organization.infra.repository.*;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.user.app.OnboardingJourneyService;
import com.fabricmanagement.platform.user.app.UserOnboardingService;
import com.fabricmanagement.platform.user.domain.*;
import com.fabricmanagement.platform.user.dto.*;
import com.fabricmanagement.platform.user.infra.repository.*;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Runs real migration backfill against historical-shaped rows and checks actual SQL grants/RLS. */
class OnboardingRetentionMigrationIT extends AbstractIntegrationTest {
  @Autowired private TenantRepository tenants;
  @Autowired private OrganizationRepository organizations;
  @Autowired private RoleRepository roles;
  @Autowired private UserRepository users;
  @Autowired private RegistrationTokenRepository registrations;
  @Autowired private LeadRepository leads;
  @Autowired private LeadProfileSnapshotRepository snapshots;
  @Autowired private AddressRepository addresses;
  @Autowired private OrganizationAddressRepository assignments;
  @Autowired private OnboardingJourneyRepository journeys;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private OnboardingJourneyService commands;
  @Autowired private UserOnboardingService legacy;

  @AfterEach
  void clear() {
    TenantContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void historicalOriginalFactsSurviveEnrichmentAndBackfillIsIdempotent() {
    var f = historical(false);
    Lead old =
        leads.saveAndFlush(
            Lead.create(
                "Hawthorn Historic Ltd",
                "GB123456789",
                OrganizationType.WEAVER,
                "Eleanor",
                "Hughes",
                f.email(),
                List.of("FabricOS"),
                "PLAYGROUND",
                f.tenant()));
    Address address =
        addresses.saveAndFlush(
            Address.builder()
                .streetAddress("14 Mill Street")
                .city("Leeds")
                .country("United Kingdom")
                .label("Mill office")
                .addressType(AddressType.HEADQUARTERS)
                .build());
    assignments.saveAndFlush(
        OrganizationAddress.builder()
            .organizationId(f.organization())
            .addressId(address.getId())
            .isPrimary(true)
            .isHeadquarters(false)
            .build());
    backfill();
    Lead retained = leads.findById(old.getId()).orElseThrow();
    assertThat(retained.getCompanyName()).isEqualTo("Hawthorn Historic Ltd");
    assertThat(retained.getSignupSnapshot().organisation().organizationName())
        .isEqualTo("Hawthorn Historic Ltd");
    assertThat(retained.getAcceptedSnapshot().organisation().organizationName())
        .isEqualTo("Hawthorn Current Ltd");
    assertThat(retained.getAcceptedSnapshot().headquarters().city()).isEqualTo("Leeds");
    assertThat(retained.getSignupUserId()).isEqualTo(f.owner());
    assertThat(retained.getTermsAcceptedAt()).isNull();
    var journey =
        journeys.findByTenantIdAndKind(f.tenant(), OnboardingJourney.Kind.COMPANY).orElseThrow();
    assertThat(journey.getAcceptedSteps()).isEmpty();
    assertThat(journey.firstIncomplete()).isEqualTo(OnboardingStepId.ORGANIZATION_DETAILS);
    var history = snapshots.findByLeadIdOrderByRevisionAsc(old.getId());
    assertThat(history).hasSize(1);
    assertThat(history.get(0).getSource()).isEqualTo("LEGACY_BACKFILL");
    backfill();
    assertThat(leads.findByTrialTenantId(f.tenant())).hasSize(1);
    assertThat(snapshots.findByLeadIdOrderByRevisionAsc(old.getId()))
        .extracting(row -> row.getId())
        .containsExactly(history.get(0).getId());
    assertThat(
            journeys
                .findByTenantIdAndKind(f.tenant(), OnboardingJourney.Kind.COMPANY)
                .orElseThrow()
                .getId())
        .isEqualTo(journey.getId());
  }

  @Test
  void missingHistoricalCaptureIsRecoveredWithoutInventingStepsConsentOrPaidConversion() {
    var f = historical(true);
    backfill();
    Lead retained = leads.findByTrialTenantId(f.tenant()).get(0);
    assertThat(retained.getSignupIntent()).isEqualTo("LEGACY_UNKNOWN");
    assertThat(retained.getSignupSnapshot().selectedOS()).isNull();
    assertThat(retained.getTermsAcceptedAt()).isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT signup_provenance FROM common_company.common_lead WHERE id = ?",
                String.class,
                retained.getId()))
        .isEqualTo("LEGACY_RECORDS");
    var journey =
        journeys.findByTenantIdAndKind(f.tenant(), OnboardingJourney.Kind.COMPANY).orElseThrow();
    assertThat(journey.isLegacyCompleted()).isTrue();
    assertThat(journey.getCompletedAt()).isNotNull();
    assertThat(journey.getAcceptedSteps()).isEmpty();
    assertThat(journey.firstIncomplete()).isNull();
    backfill();
    assertThat(leads.findByTrialTenantId(f.tenant())).hasSize(1);
  }

  @Test
  void ambiguousHistoricalLeadsAreReportedWithoutGuessingOrOverwritingFacts() {
    var f = historical(false);
    for (String name : List.of("Hawthorn First Ltd", "Hawthorn Second Ltd"))
      leads.saveAndFlush(
          Lead.create(
              name,
              "GB123456789",
              OrganizationType.WEAVER,
              "Eleanor",
              "Hughes",
              f.email(),
              List.of("FabricOS"),
              "PLAYGROUND",
              f.tenant()));
    backfill();
    assertThat(leads.findByTrialTenantId(f.tenant()))
        .hasSize(2)
        .allSatisfy(lead -> assertThat(lead.getSignupUserId()).isNull());
    assertThat(journeys.findByTenantIdAndKind(f.tenant(), OnboardingJourney.Kind.COMPANY))
        .isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM common_company.onboarding_link_issue i JOIN common_company.common_lead l ON l.id = i.lead_id WHERE l.trial_tenant_id = ? AND reason = 'MULTIPLE_LEADS_FOR_TENANT'",
                Long.class,
                f.tenant()))
        .isEqualTo(2);
    assertUnresolvedCompanyCannotComplete(f);
  }

  @Test
  void historicalExistingIdentityWithoutSignupProvenanceCannotFallBackToPersonalCompletion() {
    var f = historical(false);
    // Historical existing-identity creation issued no registration token or Lead event.
    jdbc.update(
        "DELETE FROM common_auth.common_registration_token WHERE tenant_id = ? AND user_id = ?",
        f.tenant(),
        f.owner());
    backfill();
    assertThat(journeys.findByTenantIdAndKind(f.tenant(), OnboardingJourney.Kind.COMPANY))
        .isEmpty();
    assertThat(leads.findByTrialTenantId(f.tenant())).isEmpty();
    assertUnresolvedCompanyCannotComplete(f);
  }

  private void assertUnresolvedCompanyCannotComplete(Fixture f) {
    TenantContext.setCurrentUserId(f.owner());
    var identity = new AuthenticatedUserContext(f.owner(), "ADMIN", List.of(), null, f.tenant());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(identity, null, List.of()));
    var priorLeads = leads.findByTrialTenantId(f.tenant());
    var revisions = priorLeads.stream().map(Lead::getAcceptedRevision).toList();
    long historySize =
        jdbc.queryForObject(
            "SELECT count(*) FROM common_company.lead_profile_snapshot s "
                + "JOIN common_company.common_lead l ON l.id = s.lead_id WHERE l.trial_tenant_id = ?",
            Long.class,
            f.tenant());
    List<Runnable> attempts =
        List.of(
            () -> commands.get(),
            () ->
                commands.save(
                    OnboardingStepId.YOUR_DETAILS,
                    new SaveOnboardingStepRequest(
                        0L,
                        UUID.randomUUID(),
                        new OnboardingStepPayload.YourDetails("+447700900123"))),
            () ->
                commands.complete(
                    new CompleteOnboardingJourneyRequest(0L, UUID.randomUUID(), null)),
            () -> legacy.completeOnboarding(f.owner(), null),
            () ->
                legacy.completeOnboarding(
                    f.owner(),
                    CompleteOnboardingRequest.builder()
                        .organizationName("Willow Cloth Ltd")
                        .mobilePhone("+447700900123")
                        .build()));
    for (var attempt : attempts) {
      assertThatThrownBy(attempt::run)
          .isInstanceOfSatisfying(
              PlatformDomainException.class,
              failure -> {
                assertThat(failure.getHttpStatus()).isEqualTo(409);
                assertThat(failure.getErrorCode()).isEqualTo("ONBOARDING_SIGNUP_LINK_UNRESOLVED");
              });
    }
    assertThat(organizations.findById(f.organization()).orElseThrow().getName())
        .isEqualTo("Hawthorn Current Ltd");
    assertThat(users.findById(f.owner()).orElseThrow().getOnboardingCompletedAt()).isNull();
    assertThat(journeys.findByTenantIdAndOwnerUserId(f.tenant(), f.owner())).isEmpty();
    assertThat(leads.findByTrialTenantId(f.tenant()))
        .extracting(Lead::getAcceptedRevision)
        .containsExactlyInAnyOrderElementsOf(revisions);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM common_company.lead_profile_snapshot s "
                    + "JOIN common_company.common_lead l ON l.id = s.lead_id WHERE l.trial_tenant_id = ?",
                Long.class,
                f.tenant()))
        .isEqualTo(historySize);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM common_user.common_user_contact WHERE tenant_id = ? AND user_id = ?",
                Long.class,
                f.tenant(),
                f.owner()))
        .isZero();
  }

  @Test
  void independentHistoryHasNoTenantScopeAndOperationalLinkKeepsSetNull() {
    assertThat(
            jdbc.queryForObject(
                "SELECT relrowsecurity FROM pg_class WHERE oid = 'common_company.lead_profile_snapshot'::regclass",
                Boolean.class))
        .isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'common_company' AND table_name = 'lead_profile_snapshot' AND column_name = 'tenant_id'",
                Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT confdeltype::text FROM pg_constraint WHERE conname = 'fk_common_lead_trial_tenant'",
                String.class))
        .isEqualTo("n");
    assertThat(
            jdbc.queryForObject(
                "SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid = 'common_user.onboarding_journey'::regclass",
                Boolean.class))
        .isTrue();
  }

  private void backfill() {
    jdbc.execute("SELECT common_company.backfill_onboarding_evidence()");
  }

  private Fixture historical(boolean completed) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    UUID tenant = tenants.saveAndFlush(Tenant.create("Hawthorn " + suffix, "HW-" + suffix)).getId();
    TenantContext.setCurrentTenantId(tenant);
    Organization org =
        organizations.saveAndFlush(
            Organization.create("Hawthorn Current Ltd", "GB123456789", OrganizationType.WEAVER));
    Role role = roles.saveAndFlush(Role.create("Administrator", "ADMIN", "Company setup"));
    User owner = User.create("Eleanor", "Hughes", org.getId());
    owner.setRole(role);
    if (completed) owner.completeOnboarding();
    owner = users.saveAndFlush(owner);
    String email = "eleanor." + suffix + "@example.com";
    RegistrationToken registration =
        RegistrationToken.create(email, RegistrationTokenType.SELF_SERVICE);
    registration.linkTo(owner.getId(), org.getId());
    registrations.saveAndFlush(registration);
    return new Fixture(tenant, org.getId(), owner.getId(), email);
  }

  private record Fixture(UUID tenant, UUID organization, UUID owner, String email) {}
}
