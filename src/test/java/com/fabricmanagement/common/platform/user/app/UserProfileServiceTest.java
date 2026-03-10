package com.fabricmanagement.common.platform.user.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.user.domain.event.UserProfileUpdatedEvent;
import com.fabricmanagement.common.platform.user.domain.value.ProfileCategory;
import com.fabricmanagement.common.platform.user.dto.UpdateUserProfileRequest;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserProfileService")
class UserProfileServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID REQUESTER_ID = UUID.randomUUID();

  @Mock private UserRepository userRepository;
  @Mock private UserProfilePermissionService permissionService;

  @Mock
  private com.fabricmanagement.common.platform.communication.app.ContactService contactService;

  @Mock private UserContactAssignmentService userContactAssignmentService;

  @Mock
  private com.fabricmanagement.common.platform.communication.app.AddressService addressService;

  @Mock private UserAddressAssignmentService userAddressAssignmentService;
  @Mock private UserDepartmentService userDepartmentService;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private UserQueryService userQueryService;

  @Mock
  private com.fabricmanagement.human.core.employee.application.EmployeeService employeeService;

  @InjectMocks private UserProfileService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private User user(UUID id) {
    User u = User.create("First", "Last", UUID.randomUUID());
    u.setId(id);
    u.setTenantId(TENANT_ID);
    return u;
  }

  @Nested
  @DisplayName("updateProfile")
  class UpdateProfile {

    @Test
    void throwsWhenNoUpdates() {
      UpdateUserProfileRequest request = UpdateUserProfileRequest.builder().build();
      when(userQueryService.findById(TENANT_ID, USER_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.updateProfile(USER_ID, request, REQUESTER_ID))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User not found");
    }

    @Test
    void throwsWhenSelfUpdate() {
      UpdateUserProfileRequest request =
          UpdateUserProfileRequest.builder().firstName("New").build();

      assertThatThrownBy(() -> service.updateProfile(USER_ID, request, USER_ID))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("cannot update their own profile");
    }

    @Test
    void throwsWhenNoPermissionForCategory() {
      UpdateUserProfileRequest request =
          UpdateUserProfileRequest.builder().firstName("New").build();
      when(permissionService.canUpdateWorkProfile(REQUESTER_ID, USER_ID)).thenReturn(false);

      assertThatThrownBy(() -> service.updateProfile(USER_ID, request, REQUESTER_ID))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("don't have permission");
    }

    @Test
    void throwsWhenUserNotFound() {
      UpdateUserProfileRequest request =
          UpdateUserProfileRequest.builder().firstName("New").build();
      when(permissionService.canUpdateWorkProfile(REQUESTER_ID, USER_ID)).thenReturn(true);
      when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.updateProfile(USER_ID, request, REQUESTER_ID))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User not found");
    }

    @Test
    void updatesNameAndPublishesEvent() {
      UpdateUserProfileRequest request =
          UpdateUserProfileRequest.builder().firstName("Updated").lastName("Name").build();
      User user = user(USER_ID);
      when(permissionService.canUpdateWorkProfile(REQUESTER_ID, USER_ID)).thenReturn(true);
      when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.of(user));
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
      when(employeeService.getEmployeeByUserId(USER_ID)).thenReturn(Optional.empty());

      UserDto result = service.updateProfile(USER_ID, request, REQUESTER_ID);

      verify(userRepository).save(user);
      assertThat(user.getFirstName()).isEqualTo("Updated");
      assertThat(user.getLastName()).isEqualTo("Name");
      ArgumentCaptor<UserProfileUpdatedEvent> captor =
          ArgumentCaptor.forClass(UserProfileUpdatedEvent.class);
      verify(eventPublisher).publish(captor.capture());
      UserProfileUpdatedEvent event = captor.getValue();
      assertThat(event.getTenantId()).isEqualTo(TENANT_ID);
      assertThat(event.getUserId()).isEqualTo(USER_ID);
      assertThat(event.getUpdatedBy()).isEqualTo(REQUESTER_ID);
      assertThat(event.getCategories()).contains(ProfileCategory.WORK_PROFILE);
      assertThat(result.getId()).isEqualTo(USER_ID);
    }

    @Test
    void throwsWhenNoPermissionForPersonalCategory() {
      UpdateUserProfileRequest request =
          UpdateUserProfileRequest.builder().personalPhone("+905551234567").build();
      when(permissionService.canUpdatePersonalProfile(REQUESTER_ID, USER_ID)).thenReturn(false);

      assertThatThrownBy(() -> service.updateProfile(USER_ID, request, REQUESTER_ID))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("personal_profile");
    }

    @Test
    void updatesWorkEmailViaContactAndAssignment() {
      UpdateUserProfileRequest request =
          UpdateUserProfileRequest.builder().workEmail("work@example.com").build();
      User user = user(USER_ID);
      var contact =
          com.fabricmanagement.common.platform.communication.domain.Contact.builder()
              .contactValue("work@example.com")
              .contactType(
                  com.fabricmanagement.common.platform.communication.domain.ContactType.EMAIL)
              .build();
      contact.setId(UUID.randomUUID());
      contact.setTenantId(TENANT_ID);
      when(permissionService.canUpdateWorkProfile(REQUESTER_ID, USER_ID)).thenReturn(true);
      when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.of(user));
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
      when(contactService.findByValueAndType(
              "work@example.com",
              com.fabricmanagement.common.platform.communication.domain.ContactType.EMAIL))
          .thenReturn(Optional.empty());
      when(contactService.createContact(
              eq("work@example.com"),
              eq(com.fabricmanagement.common.platform.communication.domain.ContactType.EMAIL),
              eq("Work email"),
              eq(false),
              eq(null)))
          .thenReturn(contact);
      when(userContactAssignmentService.existsUserContact(USER_ID, contact.getId()))
          .thenReturn(false);
      when(employeeService.getEmployeeByUserId(USER_ID)).thenReturn(Optional.empty());

      UserDto result = service.updateProfile(USER_ID, request, REQUESTER_ID);

      verify(contactService)
          .createContact(
              eq("work@example.com"),
              eq(com.fabricmanagement.common.platform.communication.domain.ContactType.EMAIL),
              eq("Work email"),
              eq(false),
              eq(null));
      verify(userContactAssignmentService)
          .assignContact(eq(USER_ID), eq(contact.getId()), eq(false));
      assertThat(result.getId()).isEqualTo(USER_ID);
    }
  }
}
