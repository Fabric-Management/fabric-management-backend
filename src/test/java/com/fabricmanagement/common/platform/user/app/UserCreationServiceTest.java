package com.fabricmanagement.common.platform.user.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.communication.domain.ContactType;
import com.fabricmanagement.common.platform.organization.api.facade.OrganizationFacade;
import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.user.dto.CreateExternalUserRequest;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserCreationService")
class UserCreationServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID COMPANY_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID CONTACT_ID = UUID.randomUUID();
  private static final String CONTACT_VALUE = "external@example.com";

  @Mock private UserRepository userRepository;
  @Mock private OrganizationFacade organizationFacade;

  @Mock
  private com.fabricmanagement.common.platform.communication.app.ContactService contactService;

  @Mock private UserContactAssignmentService userContactAssignmentService;

  @Mock
  private com.fabricmanagement.common.platform.communication.app.AddressService addressService;

  @Mock private UserAddressAssignmentService userAddressAssignmentService;
  @Mock private UserAddressAutoService userAddressAutoService;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private RoleService roleService;
  @Mock private UserDepartmentService userDepartmentService;

  @Mock
  private com.fabricmanagement.common.platform.organization.infra.repository.DepartmentRepository
      departmentRepository;

  @Mock
  private com.fabricmanagement.human.core.employee.application.EmployeeService employeeService;

  @InjectMocks private UserCreationService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Nested
  @DisplayName("createUserEntity")
  class CreateUserEntity {

    @Test
    void throwsWhenContactValueAlreadyRegistered() {
      when(userRepository.existsByTenantIdAndContactValue(TENANT_ID, CONTACT_VALUE))
          .thenReturn(true);

      assertThatThrownBy(
              () ->
                  service.createUserEntity(
                      "First",
                      "Last",
                      CONTACT_VALUE,
                      com.fabricmanagement.common.platform.user.domain.ContactType.EMAIL,
                      COMPANY_ID,
                      null,
                      null,
                      null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("already registered");
    }

    @Test
    void throwsWhenOrganizationNotFound() {
      when(userRepository.existsByTenantIdAndContactValue(TENANT_ID, CONTACT_VALUE))
          .thenReturn(false);
      when(organizationFacade.exists(TENANT_ID, COMPANY_ID)).thenReturn(false);

      assertThatThrownBy(
              () ->
                  service.createUserEntity(
                      "First",
                      "Last",
                      CONTACT_VALUE,
                      com.fabricmanagement.common.platform.user.domain.ContactType.EMAIL,
                      COMPANY_ID,
                      null,
                      null,
                      null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Organization not found");
    }

    @Test
    void createsUserAndContactAndAssignsWhenNoAddressesThenCopiesCompanyPrimaryAndPublishesEvent() {
      when(userRepository.existsByTenantIdAndContactValue(TENANT_ID, CONTACT_VALUE))
          .thenReturn(false);
      when(organizationFacade.exists(TENANT_ID, COMPANY_ID)).thenReturn(true);
      User savedUser = User.create("First", "Last", COMPANY_ID);
      savedUser.setId(USER_ID);
      savedUser.setTenantId(TENANT_ID);
      when(userRepository.save(any(User.class))).thenReturn(savedUser);

      Contact contact = new Contact();
      contact.setId(CONTACT_ID);
      contact.setContactValue(CONTACT_VALUE);
      contact.setContactType(ContactType.EMAIL);
      when(contactService.createContact(
              eq(CONTACT_VALUE), eq(ContactType.EMAIL), eq("Primary"), eq(true), eq(null)))
          .thenReturn(contact);

      User result =
          service.createUserEntity(
              "First",
              "Last",
              CONTACT_VALUE,
              com.fabricmanagement.common.platform.user.domain.ContactType.EMAIL,
              COMPANY_ID,
              null,
              null,
              null);

      verify(userRepository).save(any(User.class));
      verify(contactService).createContact(CONTACT_VALUE, ContactType.EMAIL, "Primary", true, null);
      verify(userContactAssignmentService).assignContact(eq(USER_ID), eq(CONTACT_ID), eq(true));
      verify(userAddressAutoService)
          .copyOrganizationPrimaryAddress(eq(USER_ID), eq(COMPANY_ID), eq(TENANT_ID));
      assertThat(result.getId()).isEqualTo(USER_ID);
      assertThat(result.getOrganizationId()).isEqualTo(COMPANY_ID);
    }
  }

  @Nested
  @DisplayName("createExternalUser")
  class CreateExternalUser {

    @Test
    void delegatesToCreateUserBaseAndReturnsUserDto() {
      CreateExternalUserRequest request =
          CreateExternalUserRequest.builder()
              .firstName("External")
              .lastName("User")
              .contactValue(CONTACT_VALUE)
              .contactType(com.fabricmanagement.common.platform.user.domain.ContactType.EMAIL)
              .organizationId(COMPANY_ID)
              .build();

      when(userRepository.existsByTenantIdAndContactValue(TENANT_ID, CONTACT_VALUE))
          .thenReturn(false);
      when(organizationFacade.exists(TENANT_ID, COMPANY_ID)).thenReturn(true);
      User savedUser = User.create("External", "User", COMPANY_ID);
      savedUser.setId(USER_ID);
      savedUser.setTenantId(TENANT_ID);
      when(userRepository.save(any(User.class))).thenReturn(savedUser);

      Contact contact = new Contact();
      contact.setId(CONTACT_ID);
      contact.setContactValue(CONTACT_VALUE);
      contact.setContactType(ContactType.EMAIL);
      when(contactService.createContact(
              eq(CONTACT_VALUE), eq(ContactType.EMAIL), eq("Primary"), eq(true), eq(null)))
          .thenReturn(contact);

      UserDto result = service.createExternalUser(request);

      verify(userRepository).save(any(User.class));
      verify(userAddressAutoService)
          .copyOrganizationPrimaryAddress(eq(USER_ID), eq(COMPANY_ID), eq(TENANT_ID));
      assertThat(result.getId()).isEqualTo(USER_ID);
      assertThat(result.getFirstName()).isEqualTo("External");
      assertThat(result.getLastName()).isEqualTo("User");
    }
  }
}
