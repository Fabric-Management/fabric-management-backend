package com.fabricmanagement.common.platform.user.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.communication.app.AddressService;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.domain.AddressType;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.communication.domain.ContactType;
import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.user.domain.event.UserProfileUpdatedEvent;
import com.fabricmanagement.common.platform.user.domain.value.ProfileCategory;
import com.fabricmanagement.common.platform.user.dto.UpdateUserProfileRequest;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.human.core.employee.application.EmployeeService;
import com.fabricmanagement.human.core.employee.domain.EmergencyContact;
import com.fabricmanagement.human.core.employee.domain.Employee;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User profile service — profile update with permission checks.
 *
 * <p>Self-update is NOT allowed. Only Admin/HR/Dept Manager can update profiles. Used by
 * ProfileUpdateRequestService when approving requests.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

  private final UserRepository userRepository;
  private final UserProfilePermissionService permissionService;
  private final ContactService contactService;
  private final UserContactAssignmentService userContactAssignmentService;
  private final AddressService addressService;
  private final UserAddressAssignmentService userAddressAssignmentService;
  private final UserDepartmentService userDepartmentService;
  private final com.fabricmanagement.common.platform.user.infra.repository.RoleRepository
      roleRepository;
  private final EmployeeService employeeService;
  private final DomainEventPublisher eventPublisher;

  @Transactional
  public UserDto updateProfile(UUID userId, UpdateUserProfileRequest request, UUID requesterId) {
    log.info("Updating user profile: userId={}, requesterId={}", userId, requesterId);

    if (!request.hasUpdates()) {
      throw new IllegalArgumentException("No fields provided for update");
    }

    Set<ProfileCategory> categories = request.getUpdatedCategories();
    for (ProfileCategory category : categories) {
      boolean allowed =
          category == ProfileCategory.WORK_PROFILE
              ? permissionService.canUpdateWorkProfile(requesterId, userId)
              : permissionService.canUpdatePersonalProfile(requesterId, userId);
      if (!allowed) {
        throw new org.springframework.security.access.AccessDeniedException(
            "You don't have permission to update " + category.name().toLowerCase() + " profile.");
      }
    }

    if (userId.equals(requesterId)) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Users cannot update their own profile. Please contact HR or Admin.");
    }

    User user =
        userRepository
            .findByTenantIdAndId(TenantContext.getCurrentTenantId(), userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    if (request.getFirstName() != null || request.getLastName() != null) {
      String firstName =
          request.getFirstName() != null ? request.getFirstName() : user.getFirstName();
      String lastName = request.getLastName() != null ? request.getLastName() : user.getLastName();
      user.updateProfile(firstName, lastName);
    }

    if (request.getWorkEmail() != null) {
      updateWorkContact(userId, request.getWorkEmail(), ContactType.EMAIL);
    }
    if (request.getWorkPhone() != null) {
      updateWorkContact(userId, request.getWorkPhone(), ContactType.MOBILE);
    }
    if (request.getWorkAddress() != null) {
      updateWorkAddress(userId, request.getWorkAddress());
    }
    if (request.getRoleId() != null) {
      com.fabricmanagement.common.platform.user.domain.Role role =
          roleRepository
              .findByTenantIdAndId(TenantContext.getCurrentTenantId(), request.getRoleId())
              .orElseThrow(() -> new IllegalArgumentException("Role not found"));
      user.setRole(role);
    }
    if (request.getDepartmentId() != null) {
      boolean hasPrimaryDept = userDepartmentService.getPrimaryDepartment(userId).isPresent();
      userDepartmentService.assignDepartment(
          userId, request.getDepartmentId(), !hasPrimaryDept, requesterId);
    }
    if (request.getHomeAddress() != null) {
      updateHomeAddress(userId, request.getHomeAddress());
    }
    if (request.getPersonalPhone() != null) {
      updatePersonalContact(userId, request.getPersonalPhone(), ContactType.MOBILE);
    }

    Employee employee = updateEmployeePersonalFields(userId, request);

    User saved = userRepository.save(user);
    eventPublisher.publish(
        new UserProfileUpdatedEvent(saved.getTenantId(), saved.getId(), requesterId, categories));

    log.info("Profile updated: userId={}, updatedBy={}", saved.getId(), requesterId);
    return UserDto.from(saved, employee);
  }

  /**
   * Persist birthDate and emergencyContact to the Employee record. If the user has no Employee
   * record yet, these fields are silently skipped (user is external or Employee wasn't created
   * during onboarding).
   */
  private Employee updateEmployeePersonalFields(UUID userId, UpdateUserProfileRequest request) {
    if (request.getBirthDate() == null && request.getEmergencyContact() == null) {
      return employeeService.getEmployeeByUserId(userId).orElse(null);
    }

    return employeeService
        .getEmployeeByUserId(userId)
        .map(
            employee -> {
              if (request.getBirthDate() != null) {
                employee.setBirthDate(request.getBirthDate());
              }
              if (request.getEmergencyContact() != null) {
                employee.setEmergencyContact(
                    EmergencyContact.builder()
                        .name(request.getEmergencyContact().getName())
                        .phone(request.getEmergencyContact().getPhone())
                        .relationship(request.getEmergencyContact().getRelationship())
                        .build());
              }
              return employeeService.saveEmployee(employee);
            })
        .orElseGet(
            () -> {
              log.debug(
                  "No Employee record for userId={}; skipping personal field update", userId);
              return null;
            });
  }

  private void updateWorkContact(UUID userId, String contactValue, ContactType contactType) {
    Contact contact =
        contactService
            .findByValueAndType(contactValue, contactType)
            .orElseGet(
                () ->
                    contactService.createContact(
                        contactValue,
                        contactType,
                        "Work " + contactType.name().toLowerCase(),
                        false,
                        null));
    if (userContactAssignmentService.existsUserContact(userId, contact.getId())) {
      return;
    }
    userContactAssignmentService.assignContact(userId, contact.getId(), false);
  }

  private void updatePersonalContact(UUID userId, String contactValue, ContactType contactType) {
    Contact contact =
        contactService
            .findByValueAndType(contactValue, contactType)
            .orElseGet(
                () ->
                    contactService.createContact(
                        contactValue,
                        contactType,
                        "Personal " + contactType.name().toLowerCase(),
                        true,
                        null));
    if (userContactAssignmentService.existsUserContact(userId, contact.getId())) {
      return;
    }
    userContactAssignmentService.assignContact(userId, contact.getId(), false);
  }

  private void updateWorkAddress(UUID userId, UpdateUserProfileRequest.AddressData addressData) {
    Address address =
        addressService.createAddress(
            addressData.getStreetAddress(),
            addressData.getCity(),
            addressData.getState(),
            addressData.getPostalCode(),
            addressData.getCountry(),
            AddressType.OFFICE,
            "Work Address");
    if (userAddressAssignmentService.getUserAddresses(userId).stream()
        .anyMatch(ua -> ua.getAddressId().equals(address.getId()))) {
      return;
    }
    userAddressAssignmentService.assignAddress(userId, address.getId(), false, true);
  }

  private void updateHomeAddress(UUID userId, UpdateUserProfileRequest.AddressData addressData) {
    Address address =
        addressService.createAddress(
            addressData.getStreetAddress(),
            addressData.getCity(),
            addressData.getState(),
            addressData.getPostalCode(),
            addressData.getCountry(),
            AddressType.HOME,
            "Home Address");
    if (userAddressAssignmentService.getUserAddresses(userId).stream()
        .anyMatch(ua -> ua.getAddressId().equals(address.getId()))) {
      return;
    }
    boolean isPrimary = userAddressAssignmentService.getPrimaryAddress(userId).isEmpty();
    userAddressAssignmentService.assignAddress(userId, address.getId(), isPrimary, false);
  }
}
