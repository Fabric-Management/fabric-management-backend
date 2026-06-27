package com.fabricmanagement.platform.user.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.communication.app.AddressService;
import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.organization.app.OrganizationAddressAssignmentService;
import com.fabricmanagement.platform.organization.app.OrganizationContactAssignmentService;
import com.fabricmanagement.platform.organization.app.OrganizationService;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.port.EmployeeProjectionPort;
import com.fabricmanagement.platform.user.dto.CompleteOnboardingRequest;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserOnboardingService")
class UserOnboardingServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID ORG_ID = UUID.randomUUID();

  @Mock private UserRepository userRepository;
  @Mock private EmployeeProjectionPort employeeProjectionPort;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private OrganizationService organizationService;
  @Mock private AddressService addressService;
  @Mock private ContactService contactService;
  @Mock private OrganizationAddressAssignmentService addressAssignmentService;
  @Mock private OrganizationContactAssignmentService contactAssignmentService;
  @Mock private UserProfileService userProfileService;

  @InjectMocks private UserOnboardingService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("completeOnboarding stores mobilePhone as current user's personal mobile")
  void completeOnboarding_storesMobilePhoneAsCurrentUserPersonalMobile() {
    User user = user();
    CompleteOnboardingRequest request =
        CompleteOnboardingRequest.builder().mobilePhone(" +905551234567 ").build();
    when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.of(user));
    when(organizationService.getRootOrganization())
        .thenReturn(Optional.of(OrganizationDto.builder().id(ORG_ID).tenantId(TENANT_ID).build()));
    when(userRepository.save(user)).thenReturn(user);
    when(employeeProjectionPort.findByUserId(TENANT_ID, USER_ID)).thenReturn(Optional.empty());

    service.completeOnboarding(USER_ID, request);

    verify(userProfileService).updatePersonalContact(USER_ID, "+905551234567", ContactType.MOBILE);
    verify(contactService, never()).createContact(any(), any(), any(), any(), any());
    verify(contactAssignmentService, never()).assignContact(any(), any(), any(), any());
  }

  @Test
  @DisplayName("completeOnboarding does not create a personal mobile when mobilePhone is absent")
  void completeOnboarding_skipsPersonalMobileWhenMobilePhoneAbsent() {
    User user = user();
    CompleteOnboardingRequest request = CompleteOnboardingRequest.builder().build();
    when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.of(user));
    when(organizationService.getRootOrganization())
        .thenReturn(Optional.of(OrganizationDto.builder().id(ORG_ID).tenantId(TENANT_ID).build()));
    when(userRepository.save(user)).thenReturn(user);
    when(employeeProjectionPort.findByUserId(TENANT_ID, USER_ID)).thenReturn(Optional.empty());

    service.completeOnboarding(USER_ID, request);

    verify(userProfileService, never()).updatePersonalContact(any(), any(), any());
  }

  @Test
  @DisplayName("completeOnboarding keeps company phone on organization contacts")
  void completeOnboarding_keepsCompanyPhoneOnOrganizationContacts() {
    User user = user();
    CompleteOnboardingRequest request =
        CompleteOnboardingRequest.builder()
            .mobilePhone("+905551234567")
            .companyPhone("+902121234567")
            .build();
    var companyContact =
        com.fabricmanagement.platform.communication.domain.Contact.builder()
            .contactValue("+902121234567")
            .contactType(ContactType.MOBILE)
            .build();
    companyContact.setId(UUID.randomUUID());
    companyContact.setTenantId(TENANT_ID);
    when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.of(user));
    when(organizationService.getRootOrganization())
        .thenReturn(Optional.of(OrganizationDto.builder().id(ORG_ID).tenantId(TENANT_ID).build()));
    when(contactService.createContact(
            eq("+902121234567"), eq(ContactType.MOBILE), eq("Organization"), eq(false), eq(null)))
        .thenReturn(companyContact);
    when(userRepository.save(user)).thenReturn(user);
    when(employeeProjectionPort.findByUserId(TENANT_ID, USER_ID)).thenReturn(Optional.empty());

    service.completeOnboarding(USER_ID, request);

    verify(userProfileService).updatePersonalContact(USER_ID, "+905551234567", ContactType.MOBILE);
    verify(contactAssignmentService).assignContact(ORG_ID, companyContact.getId(), false, null);
  }

  private static User user() {
    User user = User.create("First", "Last", ORG_ID);
    user.setId(USER_ID);
    user.setTenantId(TENANT_ID);
    return user;
  }
}
