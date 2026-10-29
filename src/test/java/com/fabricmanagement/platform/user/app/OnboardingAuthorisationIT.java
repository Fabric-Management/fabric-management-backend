package com.fabricmanagement.platform.user.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionOverride;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.dto.CompleteOnboardingRequest;
import com.fabricmanagement.platform.user.infra.repository.OnboardingJourneyRepository;
import com.fabricmanagement.platform.user.infra.repository.PermissionOverrideRepository;
import com.fabricmanagement.platform.user.infra.repository.RoleRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import com.fabricmanagement.testsupport.CompletedCompanyOnboardingFixture;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Database-backed service boundary: real membership, effective grants and persisted company rows.
 */
class OnboardingAuthorisationIT extends AbstractIntegrationTest {
  @Autowired private UserOnboardingService onboarding;
  @Autowired private PermissionEvaluator permissions;
  @Autowired private TenantRepository tenants;
  @Autowired private OrganizationRepository organisations;
  @Autowired private RoleRepository roles;
  @Autowired private UserRepository users;
  @Autowired private PermissionOverrideRepository overrides;
  @MockitoBean private DomainEventPublisher events;
  @Autowired private SignupEvidencePort capture;
  @Autowired private OnboardingJourneyRepository journeys;
  @Autowired private PlatformTransactionManager transactions;

  private UUID tenant;
  private UUID organisation;
  private UUID actor;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    tenant =
        tenants.saveAndFlush(Tenant.create("Hawthorn Weaving " + suffix, "HW-" + suffix)).getId();
    TenantContext.setCurrentTenantId(tenant);
    Organization company =
        organisations.saveAndFlush(
            Organization.create("Hawthorn Weaving Ltd", "GB-" + suffix, OrganizationType.WEAVER));
    organisation = company.getId();
    Role worker =
        roles.saveAndFlush(Role.create("Warehouse worker", "WORKER", "Warehouse operations"));
    User member = User.create("Eleanor", "Hughes", organisation);
    member.setRole(worker);
    actor = users.saveAndFlush(member).getId();
    authenticate(tenant, actor);
    Role admin = roles.saveAndFlush(Role.create("Administrator", "ADMIN", "Company setup"));
    User registrant = User.create("Oliver", "Reed", organisation);
    registrant.setRole(admin);
    UUID registrantId = users.saveAndFlush(registrant).getId();
    CompletedCompanyOnboardingFixture.create(tenant, registrantId, capture, journeys, transactions);
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void deniedMixedRequestLeavesActualCompanyAndCompletionUntouched() {
    var request =
        CompleteOnboardingRequest.builder()
            .organizationName("Willow Cloth Ltd")
            .mobilePhone("+447700900123")
            .build();
    assertForbidden(() -> onboarding.completeOnboarding(actor, request));
    assertThat(organisations.findById(organisation).orElseThrow().getName())
        .isEqualTo("Hawthorn Weaving Ltd");
    assertThat(users.findByTenantIdAndId(tenant, actor).orElseThrow().getOnboardingCompletedAt())
        .isNull();
    verifyNoInteractions(events);
  }

  @Test
  void delegatedGrantIsFreshAndRevocationStillAppliesAfterCompletion() {
    PermissionOverride grant =
        PermissionOverride.builder()
            .userId(actor)
            .resource("organization")
            .action("configure")
            .dataScope(DataScope.ORGANIZATION)
            .grantedBy(actor)
            .reason("Delegated company setup")
            .build();
    grant = overrides.saveAndFlush(grant);
    assertThat(
            permissions
                .evaluate(tenant, "WORKER", List.of(), actor)
                .can("organization", "configure"))
        .isTrue();

    onboarding.completeOnboarding(
        actor, CompleteOnboardingRequest.builder().organizationName("Willow Cloth Ltd").build());
    assertThat(organisations.findById(organisation).orElseThrow().getName())
        .isEqualTo("Willow Cloth Ltd");
    assertThat(onboarding.getStatus(actor).getCompanySetup().allowed()).isTrue();

    grant.setDataScope(null);
    overrides.saveAndFlush(grant);
    // Even with a previously populated permission cache, commands/capabilities see revocation.
    assertThat(onboarding.getStatus(actor).getCompanySetup().allowed()).isFalse();
    assertForbidden(
        () ->
            onboarding.completeOnboarding(
                actor,
                CompleteOnboardingRequest.builder().organizationName("Ashford Cloth Ltd").build()));
    assertThat(organisations.findById(organisation).orElseThrow().getName())
        .isEqualTo("Willow Cloth Ltd");
  }

  @Test
  void cannotTargetAnotherUsersOnboardingOrChangeTenantContext() {
    assertForbidden(() -> onboarding.completeOnboarding(UUID.randomUUID(), null));
    TenantContext.setCurrentTenantId(UUID.randomUUID());
    assertForbidden(
        () ->
            onboarding.completeOnboarding(
                actor,
                CompleteOnboardingRequest.builder().organizationName("Willow Cloth Ltd").build()));
  }

  @Test
  void anotherTenantCannotUseTheSameUserIdAsAMembership() {
    UUID otherTenant =
        tenants
            .saveAndFlush(
                Tenant.create(
                    "Willow Cloth Ltd", "WC-" + UUID.randomUUID().toString().substring(0, 8)))
            .getId();
    authenticate(otherTenant, actor);
    assertForbidden(() -> onboarding.completeOnboarding(actor, null));
    authenticate(tenant, actor);
    assertThat(users.findByTenantIdAndId(tenant, actor).orElseThrow().getOnboardingCompletedAt())
        .isNull();
    assertThat(organisations.findById(organisation).orElseThrow().getName())
        .isEqualTo("Hawthorn Weaving Ltd");
  }

  private void authenticate(UUID tenantId, UUID userId) {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(userId);
    var identity = new AuthenticatedUserContext(userId, "ADMIN", List.of(), null, tenantId);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(identity, null, List.of()));
  }

  private void assertForbidden(Runnable command) {
    assertThatThrownBy(command::run)
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            failure -> assertThat(failure.getHttpStatus()).isEqualTo(403));
  }
}
