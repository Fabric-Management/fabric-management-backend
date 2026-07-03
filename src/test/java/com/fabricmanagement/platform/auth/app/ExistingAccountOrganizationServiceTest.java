package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.tenant.TrialLifecyclePort;
import com.fabricmanagement.platform.auth.app.onboarding.OnboardingContext;
import com.fabricmanagement.platform.auth.app.onboarding.TenantOnboardingOrchestrator;
import com.fabricmanagement.platform.auth.domain.LoginIdentity;
import com.fabricmanagement.platform.auth.domain.Membership;
import com.fabricmanagement.platform.auth.domain.MembershipStatus;
import com.fabricmanagement.platform.auth.dto.CreateExistingAccountOrganizationRequest;
import com.fabricmanagement.platform.auth.dto.LoginResponse;
import com.fabricmanagement.platform.auth.dto.TenantOnboardingResponse;
import com.fabricmanagement.platform.auth.infra.repository.LoginIdentityRepository;
import com.fabricmanagement.platform.auth.infra.repository.MembershipRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.api.facade.OrganizationFacade;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class ExistingAccountOrganizationServiceTest {

  private final TenantOnboardingOrchestrator orchestrator =
      Mockito.mock(TenantOnboardingOrchestrator.class);
  private final OrganizationFacade organizationFacade = Mockito.mock(OrganizationFacade.class);
  private final MembershipRepository membershipRepository =
      Mockito.mock(MembershipRepository.class);
  private final LoginIdentityRepository loginIdentityRepository =
      Mockito.mock(LoginIdentityRepository.class);
  private final UserRepository userRepository = Mockito.mock(UserRepository.class);
  private final TrialLifecyclePort trialLifecyclePort = Mockito.mock(TrialLifecyclePort.class);
  private final SwitchOrganizationService switchOrganizationService =
      Mockito.mock(SwitchOrganizationService.class);
  private final com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder
      tenantSessionBinder =
          Mockito.mock(
              com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder.class);

  private ExistingAccountOrganizationService service;

  @BeforeEach
  void setUp() {
    service =
        new ExistingAccountOrganizationService(
            orchestrator,
            organizationFacade,
            membershipRepository,
            loginIdentityRepository,
            userRepository,
            trialLifecyclePort,
            switchOrganizationService,
            tenantSessionBinder);
    ReflectionTestUtils.setField(service, "maxOrganizations", 5);
  }

  @Test
  void createsCleanTrialTenantForCurrentIdentityAndSwitchesIntoIt() {
    UUID currentUserId = UUID.randomUUID();
    UUID currentTenantId = UUID.randomUUID();
    UUID identityId = UUID.randomUUID();
    UUID newTenantId = UUID.randomUUID();
    UUID newUserId = UUID.randomUUID();
    Membership currentMembership =
        Membership.builder()
            .loginIdentityId(identityId)
            .tenantId(currentTenantId)
            .userId(currentUserId)
            .status(MembershipStatus.ACTIVE)
            .build();
    LoginIdentity identity =
        LoginIdentity.builder()
            .id(identityId)
            .email("owner@example.com")
            .passwordHash("hash")
            .isActive(true)
            .emailVerified(true)
            .build();
    User currentUser = User.create("Fatih", "Akkaya", UUID.randomUUID());
    currentUser.setId(currentUserId);
    currentUser.setTenantId(currentTenantId);
    CreateExistingAccountOrganizationRequest request =
        CreateExistingAccountOrganizationRequest.builder()
            .organizationName("Sirket B")
            .taxId(" TAX-2 ")
            .organizationType(OrganizationType.WEAVER)
            .selectedOS(java.util.List.of("FabricOS", "WarehouseOS"))
            .build();
    when(membershipRepository.findByUserId(currentUserId))
        .thenReturn(Optional.of(currentMembership));
    when(loginIdentityRepository.findById(identityId)).thenReturn(Optional.of(identity));
    when(membershipRepository.countByLoginIdentityIdAndStatus(identityId, MembershipStatus.ACTIVE))
        .thenReturn(1L);
    when(organizationFacade.existsByTaxId("TAX-2")).thenReturn(false);
    when(userRepository.findByTenantIdAndId(currentTenantId, currentUserId))
        .thenReturn(Optional.of(currentUser));
    when(orchestrator.onboard(Mockito.any(OnboardingContext.class)))
        .thenReturn(
            TenantOnboardingResponse.builder()
                .tenantId(newTenantId)
                .adminUserId(newUserId)
                .organizationName("Sirket B")
                .build());
    when(switchOrganizationService.switchOrganization(
            currentUserId, newTenantId, "127.0.0.1", "JUnit"))
        .thenReturn(LoginResponse.builder().needsOnboarding(false).build());

    LoginResponse response =
        service.createOrganization(currentUserId, request, "127.0.0.1", "JUnit");

    ArgumentCaptor<OnboardingContext> contextCaptor =
        ArgumentCaptor.forClass(OnboardingContext.class);
    verify(orchestrator).onboard(contextCaptor.capture());
    OnboardingContext context = contextCaptor.getValue();
    assertThat(context.isExistingIdentity()).isTrue();
    assertThat(context.isSalesLed()).isFalse();
    assertThat(context.isDemoMode()).isFalse();
    assertThat(context.getSignupIntent()).isEqualTo("TRIAL");
    assertThat(context.getOrganizationName()).isEqualTo("Sirket B");
    assertThat(context.getTaxId()).isEqualTo("TAX-2");
    assertThat(context.getAdminFirstName()).isEqualTo("Fatih");
    assertThat(context.getAdminLastName()).isEqualTo("Akkaya");
    assertThat(context.getAdminContact()).isEqualTo("owner@example.com");
    assertThat(context.getSelectedOS()).containsExactly("FabricOS", "WarehouseOS");
    verify(trialLifecyclePort).startSelfServiceTrialIfNeeded(newTenantId);
    verify(switchOrganizationService)
        .switchOrganization(currentUserId, newTenantId, "127.0.0.1", "JUnit");
    assertThat(response.getNeedsOnboarding()).isTrue();
    assertThat(response.getOnboardingPrefill().getPrimaryEmail()).isEqualTo("owner@example.com");
    assertThat(response.getOnboardingPrefill().getOrganizationType()).isEqualTo("WEAVER");
  }

  @Test
  void rejectsWhenIdentityHasReachedOrganizationLimit() {
    UUID currentUserId = UUID.randomUUID();
    UUID currentTenantId = UUID.randomUUID();
    UUID identityId = UUID.randomUUID();
    Membership currentMembership =
        Membership.builder()
            .loginIdentityId(identityId)
            .tenantId(currentTenantId)
            .userId(currentUserId)
            .status(MembershipStatus.ACTIVE)
            .build();
    LoginIdentity identity =
        LoginIdentity.builder()
            .id(identityId)
            .email("owner@example.com")
            .passwordHash("hash")
            .isActive(true)
            .build();
    CreateExistingAccountOrganizationRequest request =
        CreateExistingAccountOrganizationRequest.builder()
            .organizationName("Sirket B")
            .organizationType(OrganizationType.WEAVER)
            .build();
    when(membershipRepository.findByUserId(currentUserId))
        .thenReturn(Optional.of(currentMembership));
    when(loginIdentityRepository.findById(identityId)).thenReturn(Optional.of(identity));
    when(membershipRepository.countByLoginIdentityIdAndStatus(identityId, MembershipStatus.ACTIVE))
        .thenReturn(5L);

    assertThatThrownBy(() -> service.createOrganization(currentUserId, request, "ip", "agent"))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            ex -> {
              assertThat(ex.getErrorCode()).isEqualTo("AUTH_ORG_LIMIT_REACHED");
              assertThat(ex.getHttpStatus()).isEqualTo(400);
            });

    verify(orchestrator, never()).onboard(Mockito.any(OnboardingContext.class));
    verify(switchOrganizationService, never())
        .switchOrganization(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
  }
}
