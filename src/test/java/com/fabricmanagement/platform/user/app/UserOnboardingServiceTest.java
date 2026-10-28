package com.fabricmanagement.platform.user.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.organization.app.CompanyOnboardingProfileService;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.event.UserOnboardingCompletedEvent;
import com.fabricmanagement.platform.user.domain.port.EmployeeProjectionPort;
import com.fabricmanagement.platform.user.dto.CompleteOnboardingRequest;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.platform.user.security.OnboardingAccessService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserOnboardingServiceTest {
  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ACTOR = UUID.randomUUID();
  @Mock private UserRepository userRepository;
  @Mock private EmployeeProjectionPort employeeProjectionPort;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private CompanyOnboardingProfileService companyProfileService;
  @Mock private OnboardingCommandLockService commandLockService;
  @Mock private OnboardingRequestValidator requestValidator;
  @Mock private UserProfileService userProfileService;
  @Mock private OnboardingAccessService onboardingAccessService;
  @Mock private OnboardingLegacyRetentionService legacyRetention;
  @InjectMocks private UserOnboardingService service;

  @BeforeEach
  void context() {
    TenantContext.setCurrentTenantId(TENANT);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void personalMobileRemainsOwnedByTheCurrentUser() {
    User user = actor();
    when(onboardingAccessService.requireCurrentUser(ACTOR)).thenReturn(user);
    when(userRepository.save(user)).thenReturn(user);
    var request = CompleteOnboardingRequest.builder().mobilePhone("+447700900123").build();
    service.completeOnboarding(ACTOR, request);
    verify(userProfileService).updatePersonalContact(ACTOR, "+447700900123", ContactType.MOBILE);
    verifyNoInteractions(companyProfileService);
    verify(onboardingAccessService, never()).requireCompanySetup(any());
  }

  @Test
  void noBodyStillCompletesAndDoesNotRequireCompany() {
    User user = actor();
    when(onboardingAccessService.requireCurrentUser(ACTOR)).thenReturn(user);
    when(userRepository.save(user)).thenReturn(user);
    service.completeOnboarding(ACTOR, null);
    assertThat(user.hasCompletedOnboarding()).isTrue();
    verify(commandLockService).lockAndRefresh(user);
    verify(eventPublisher).publish(any(UserOnboardingCompletedEvent.class));
    verifyNoInteractions(companyProfileService, userProfileService);
  }

  @Test
  void rejectionBeforeWritesIncludesCompletedMixedCommands() {
    User user = actor();
    user.completeOnboarding();
    when(onboardingAccessService.requireCurrentUser(ACTOR)).thenReturn(user);
    doThrow(denied()).when(onboardingAccessService).requireCompanySetup(user);
    assertThatThrownBy(
            () ->
                service.completeOnboarding(
                    ACTOR,
                    CompleteOnboardingRequest.builder()
                        .selectedOS(List.of())
                        .mobilePhone("+447700900123")
                        .build()))
        .isInstanceOf(PlatformDomainException.class);
    verify(commandLockService).lockAndRefresh(user);
    verifyNoInteractions(
        requestValidator,
        companyProfileService,
        userProfileService,
        userRepository,
        eventPublisher);
  }

  @Test
  void preflightFailureCannotWritePersonalMobileOrCompletion() {
    User user = actor();
    when(onboardingAccessService.requireCurrentUser(ACTOR)).thenReturn(user);
    var request =
        CompleteOnboardingRequest.builder()
            .companyPhone("+442079460123")
            .mobilePhone("+447700900123")
            .build();
    when(companyProfileService.prepare(request))
        .thenThrow(
            new PlatformDomainException(
                "Root organisation not found", "ONBOARDING_ROOT_ORGANIZATION_NOT_FOUND", 409));
    assertThatThrownBy(() -> service.completeOnboarding(ACTOR, request))
        .isInstanceOf(PlatformDomainException.class);
    assertThat(user.hasCompletedOnboarding()).isFalse();
    verifyNoInteractions(userProfileService, userRepository, eventPublisher);
  }

  @Test
  void refreshAfterWaitingSeesCompletedMarkerAndDoesNotPublishAgain() {
    User user = actor();
    when(onboardingAccessService.requireCurrentUser(ACTOR)).thenReturn(user);
    doAnswer(
            invocation -> {
              user.completeOnboarding();
              return null;
            })
        .when(commandLockService)
        .lockAndRefresh(user);
    service.completeOnboarding(ACTOR, null);
    verifyNoInteractions(userRepository, eventPublisher);
  }

  @Test
  void authorityIsEvaluatedAfterTheDatabaseWaitBeforePreflight() {
    User user = actor();
    when(onboardingAccessService.requireCurrentUser(ACTOR)).thenReturn(user);
    doThrow(denied()).when(onboardingAccessService).requireCompanySetup(user);
    assertThatThrownBy(
            () ->
                service.completeOnboarding(
                    ACTOR,
                    CompleteOnboardingRequest.builder()
                        .organizationName("Hawthorn Weaving Ltd")
                        .build()))
        .isInstanceOf(PlatformDomainException.class);
    var order = inOrder(commandLockService, onboardingAccessService);
    order.verify(onboardingAccessService).requireCurrentUser(ACTOR);
    order.verify(commandLockService).lockAndRefresh(user);
    order.verify(onboardingAccessService).requireCurrentUser(ACTOR);
    order.verify(onboardingAccessService).requireCompanySetup(user);
    verifyNoInteractions(
        requestValidator,
        companyProfileService,
        userProfileService,
        userRepository,
        eventPublisher);
  }

  private PlatformDomainException denied() {
    return new PlatformDomainException(
        "Company setup denied", "ONBOARDING_COMPANY_PERMISSION_REQUIRED", 403);
  }

  private User actor() {
    User user = User.create("Eleanor", "Hughes", UUID.randomUUID());
    user.setId(ACTOR);
    user.setTenantId(TENANT);
    return user;
  }
}
