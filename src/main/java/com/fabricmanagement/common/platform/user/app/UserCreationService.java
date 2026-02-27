package com.fabricmanagement.common.platform.user.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.communication.app.AddressService;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.domain.AddressType;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.communication.domain.ContactType;
import com.fabricmanagement.common.platform.organization.api.facade.OrganizationFacade;
import com.fabricmanagement.common.platform.user.domain.Role;
import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.common.platform.user.dto.AddressData;
import com.fabricmanagement.common.platform.user.dto.ContactData;
import com.fabricmanagement.common.platform.user.dto.CreateAdminUserRequest;
import com.fabricmanagement.common.platform.user.dto.CreateExternalUserRequest;
import com.fabricmanagement.common.platform.user.dto.CreateInternalUserRequest;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User creation service — internal/external user creation.
 *
 * <p>Handles createUserBase, createInternal (with Employee, role, department, position),
 * createExternal. Uses {@link UserAddressAutoService} when no addresses provided.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserCreationService {

  private final UserRepository userRepository;
  private final OrganizationFacade organizationFacade;
  private final ContactService contactService;
  private final UserContactAssignmentService userContactAssignmentService;
  private final AddressService addressService;
  private final UserAddressAssignmentService userAddressAssignmentService;
  private final UserAddressAutoService userAddressAutoService;
  private final DomainEventPublisher eventPublisher;
  private final RoleService roleService;
  private final UserDepartmentService userDepartmentService;
  private final com.fabricmanagement.common.platform.organization.infra.repository
          .PositionRepository
      positionRepository;
  private final com.fabricmanagement.human.core.employee.application.EmployeeService
      employeeService;

  @Transactional
  public UserDto createInternalUser(CreateInternalUserRequest request) {
    log.info(
        "Creating internal employee: contactValue={}",
        PiiMaskingUtil.maskEmail(request.getContactValue()));

    // Step 1: Create base user with contacts and addresses (returns entity, not DTO)
    User userEntity =
        createUserEntity(
            request.getFirstName(),
            request.getLastName(),
            request.getContactValue(),
            request.getContactType(),
            request.getCompanyId(),
            request.getAdditionalContacts(),
            request.getAddresses());

    UUID tenantId = TenantContext.getCurrentTenantId();
    UUID assignedBy = TenantContext.getCurrentUserId();

    // Step 2: Assign role (on same entity, no reload)
    if (request.getRoleId() != null) {
      Role role =
          roleService
              .findById(request.getRoleId())
              .orElseThrow(() -> new IllegalArgumentException("Role not found"));
      userEntity.setRole(role);
    }

    // Step 3: Assign position (on same entity, no reload)
    if (request.getPositionId() != null) {
      com.fabricmanagement.common.platform.organization.domain.Position position =
          positionRepository
              .findById(request.getPositionId())
              .orElseThrow(() -> new IllegalArgumentException("Position not found"));
      if (!position.getTenantId().equals(tenantId)) {
        throw new IllegalArgumentException("Position must belong to the same tenant");
      }
      com.fabricmanagement.common.platform.user.domain.UserPosition userPosition =
          com.fabricmanagement.common.platform.user.domain.UserPosition.create(
              userEntity, position, true, assignedBy);
      userEntity.getUserPositions().add(userPosition);
    }

    // Single save for role + position changes
    User saved = userRepository.save(userEntity);

    // Step 4: Assign department (uses separate junction service)
    if (request.getDepartmentId() != null) {
      userDepartmentService.assignDepartment(
          saved.getId(), request.getDepartmentId(), true, assignedBy);
    }

    // Step 5: Create Employee record if any HR data provided
    createEmployeeIfNeeded(saved.getId(), request);

    // Step 6: Publish event
    String contactValue = request.getContactValue();
    eventPublisher.publish(
        new UserCreatedEvent(
            saved.getTenantId(),
            saved.getId(),
            saved.getDisplayName(),
            contactValue,
            saved.getOrganizationId()));

    checkAndTrackHrCompliance(saved.getId(), request.getDepartment());

    log.info(
        "Internal user created: id={}, uid={}, displayName={}",
        saved.getId(),
        saved.getUid(),
        saved.getDisplayName());
    return UserDto.from(saved);
  }

  @Transactional
  public UserDto createExternalUser(CreateExternalUserRequest request) {
    log.info(
        "Creating external user: contactValue={}",
        PiiMaskingUtil.maskEmail(request.getContactValue()));

    User userEntity =
        createUserEntity(
            request.getFirstName(),
            request.getLastName(),
            request.getContactValue(),
            request.getContactType(),
            request.getCompanyId(),
            request.getAdditionalContacts(),
            request.getAddresses());

    String contactValue = request.getContactValue();

    if (!request.isSuppressEmailInvitation()) {
      eventPublisher.publish(
          new UserCreatedEvent(
              userEntity.getTenantId(),
              userEntity.getId(),
              userEntity.getDisplayName(),
              contactValue,
              userEntity.getOrganizationId()));
    }

    log.info(
        "External user created: id={}, uid={}, displayName={}",
        userEntity.getId(),
        userEntity.getUid(),
        userEntity.getDisplayName());
    return UserDto.from(userEntity);
  }

  /**
   * Create tenant admin user during onboarding. User is created with primary contact (unverified),
   * assigned tenant ADMIN role. Contact verification deferred to PasswordSetupService.
   */
  @Transactional
  public UserDto createAdminUser(CreateAdminUserRequest request) {
    log.debug(
        "Creating admin user: contact={}", PiiMaskingUtil.maskEmail(request.getContactValue()));

    return TenantContext.executeInTenantContext(
        request.getTenantId(),
        () -> {
          User user =
              User.create(request.getFirstName(), request.getLastName(), request.getCompanyId());

          // Assign role before first save to avoid extra round-trip
          com.fabricmanagement.common.platform.user.domain.Role role =
              roleService.findTenantAdminRoleOrThrow(request.getTenantId());
          user.setRole(role);

          User saved = userRepository.save(user);

          // Create primary contact (unverified). Verification deferred to PasswordSetupService
          // when user proves email ownership by clicking the registration link.
          com.fabricmanagement.common.platform.communication.domain.Contact contact =
              contactService.createContact(
                  request.getContactValue(),
                  com.fabricmanagement.common.platform.communication.domain.ContactType.EMAIL,
                  "Primary",
                  true,
                  null);
          userContactAssignmentService.assignContact(saved.getId(), contact.getId(), true);

          // TODO(BACKLOG): Department assignment by name - add findDepartmentIdByName to
          // OrganizationFacade or DepartmentService when needed
          if (request.getDepartment() != null && !request.getDepartment().isBlank()) {
            log.debug(
                "Department by name skipped (no resolver): tenant={}, org={}, department={}",
                request.getTenantId(),
                request.getCompanyId(),
                request.getDepartment());
          }

          eventPublisher.publish(
              new UserCreatedEvent(
                  saved.getTenantId(),
                  saved.getId(),
                  saved.getDisplayName(),
                  contact.getContactValue(),
                  saved.getOrganizationId()));

          log.info("Admin user created: id={}, uid={}", saved.getId(), saved.getUid());
          return UserDto.from(saved);
        });
  }

  /**
   * Create user entity with contacts and addresses. Returns the persisted entity (not DTO) so
   * callers can continue enriching it (role, position, etc.) without extra DB round-trips.
   *
   * <p><b>Note:</b> Does NOT publish UserCreatedEvent — callers must publish after completing all
   * enrichment steps.
   */
  User createUserEntity(
      String firstName,
      String lastName,
      String contactValue,
      com.fabricmanagement.common.platform.user.domain.ContactType contactType,
      UUID companyId,
      List<ContactData> additionalContacts,
      List<AddressData> addresses) {

    UUID tenantId = TenantContext.getCurrentTenantId();
    // Tenant-scoped uniqueness: same contact can exist in different tenants (SaaS multi-tenancy)
    if (userRepository.existsByTenantIdAndContactValue(tenantId, contactValue)) {
      throw new IllegalArgumentException("Contact value already registered");
    }
    if (!organizationFacade.exists(tenantId, companyId)) {
      throw new IllegalArgumentException("Organization not found");
    }

    User user = User.create(firstName, lastName, companyId);
    User saved = userRepository.save(user);

    // Validate primary contact is not duplicated in additional contacts
    if (additionalContacts != null) {
      boolean hasDuplicate =
          additionalContacts.stream()
              .anyMatch(c -> contactValue.equalsIgnoreCase(c.getContactValue()));
      if (hasDuplicate) {
        throw new IllegalArgumentException(
            "Primary contact value must not be duplicated in additional contacts");
      }
    }

    Contact primaryContact =
        contactService.createContact(
            contactValue, mapContactType(contactType), "Primary", true, null);
    userContactAssignmentService.assignContact(saved.getId(), primaryContact.getId(), true);

    if (additionalContacts != null && !additionalContacts.isEmpty()) {
      for (ContactData contactData : additionalContacts) {
        ContactType mappedType = mapContactType(contactData);
        String label =
            contactData.getLabel() != null
                ? contactData.getLabel()
                : (contactData.getContactType()
                        == com.fabricmanagement.common.platform.user.domain.ContactType.EMAIL
                    ? "Email"
                    : mappedType == ContactType.LANDLINE ? "Landline" : "Mobile");
        Contact additionalContact =
            contactService.createContact(
                contactData.getContactValue(),
                mappedType,
                label,
                contactData.getIsPersonal() != null ? contactData.getIsPersonal() : true,
                null);
        userContactAssignmentService.assignContact(saved.getId(), additionalContact.getId(), false);
      }
    }

    assignAddresses(saved, companyId, tenantId, addresses);

    return saved;
  }

  /** Assign addresses to user. Uses company primary address as fallback when none provided. */
  private void assignAddresses(
      User user, UUID companyId, UUID tenantId, List<AddressData> addresses) {
    if (addresses != null && !addresses.isEmpty()) {
      boolean isFirstAddress = true;
      for (AddressData addressData : addresses) {
        com.fabricmanagement.common.platform.communication.domain.AddressType addressType =
            parseAddressType(addressData.getAddressType());
        boolean isWorkType = addressType == AddressType.OFFICE;
        String addressLabel =
            addressData.getLabel() != null
                ? addressData.getLabel()
                : (isWorkType ? "Work Address" : "Home Address");
        Address address =
            addressService.createAddress(
                addressData.getStreetAddress(),
                addressData.getCity(),
                addressData.getState(),
                addressData.getPostalCode(),
                addressData.getCountry(),
                addressType,
                addressLabel);
        boolean isPrimary = isFirstAddress || Boolean.TRUE.equals(addressData.getIsPrimary());
        boolean isWorkAddress = addressType == AddressType.OFFICE;
        userAddressAssignmentService.assignAddress(
            user.getId(), address.getId(), isPrimary, isWorkAddress);
        isFirstAddress = false;
      }
    } else {
      userAddressAutoService.copyCompanyPrimaryAddress(user.getId(), companyId, tenantId);
    }
  }

  /**
   * Create Employee record if any HR-related fields are provided. Consolidates the repeated null
   * checks into a single method.
   */
  private void createEmployeeIfNeeded(UUID userId, CreateInternalUserRequest request) {
    boolean hasAnyHrData =
        request.getTitle() != null
            || request.getGender() != null
            || request.getBirthDate() != null
            || request.getNationality() != null
            || request.getHireDate() != null
            || request.getEmergencyContact() != null
            || (request.getEmployeeNumber() != null && !request.getEmployeeNumber().isBlank());

    if (!hasAnyHrData) {
      return;
    }

    String employeeNumber = request.getEmployeeNumber();
    if (employeeNumber == null || employeeNumber.isBlank()) {
      employeeNumber = employeeService.generateEmployeeNumber();
    }

    com.fabricmanagement.human.core.employee.domain.EmergencyContact emergencyContact = null;
    if (request.getEmergencyContact() != null) {
      emergencyContact =
          com.fabricmanagement.human.core.employee.domain.EmergencyContact.builder()
              .name(request.getEmergencyContact().getName())
              .phone(request.getEmergencyContact().getPhone())
              .relationship(request.getEmergencyContact().getRelationship())
              .build();
    }

    employeeService.createOrUpdateEmployee(
        userId,
        request.getTitle(),
        request.getGender(),
        request.getBirthDate(),
        request.getNationality(),
        employeeNumber,
        request.getHireDate(),
        emergencyContact);
  }

  private com.fabricmanagement.common.platform.communication.domain.AddressType parseAddressType(
      String addressTypeStr) {
    if (addressTypeStr == null || addressTypeStr.isBlank()) {
      return AddressType.OFFICE;
    }
    String upper = addressTypeStr.toUpperCase();
    if ("WORK".equals(upper)) {
      return AddressType.OFFICE;
    }
    try {
      return com.fabricmanagement.common.platform.communication.domain.AddressType.valueOf(upper);
    } catch (IllegalArgumentException e) {
      log.warn("Invalid address type: {}, defaulting to OFFICE", addressTypeStr);
      return AddressType.OFFICE;
    }
  }

  private ContactType mapContactType(
      com.fabricmanagement.common.platform.user.domain.ContactType userContactType) {
    return switch (userContactType) {
      case EMAIL -> ContactType.EMAIL;
      case PHONE -> ContactType.MOBILE;
    };
  }

  private ContactType mapContactType(ContactData contactData) {
    if (contactData.getContactType()
        == com.fabricmanagement.common.platform.user.domain.ContactType.EMAIL) {
      return ContactType.EMAIL;
    }
    if (contactData.getContactType()
        == com.fabricmanagement.common.platform.user.domain.ContactType.PHONE) {
      String phoneType = contactData.getPhoneType();
      if ("LANDLINE".equalsIgnoreCase(phoneType)) {
        return ContactType.LANDLINE;
      }
      return ContactType.MOBILE;
    }
    return ContactType.MOBILE;
  }

  /**
   * Check and track HR compliance for employee. Non-critical — failures are logged as errors but do
   * NOT prevent user creation (compliance can be resolved later via HR module).
   */
  private void checkAndTrackHrCompliance(UUID userId, String department) {
    try {
      Optional<com.fabricmanagement.human.core.employee.domain.Employee> employeeOpt =
          employeeService.getEmployeeByUserId(userId);
      if (employeeOpt.isPresent()) {
        var employee = employeeOpt.get();
        List<String> missingFields = employeeService.checkAndUpdateCompliance(employee, department);
        if (!missingFields.isEmpty()) {
          log.warn(
              "HR Compliance incomplete: userId={}, department={}, missingFields={}. "
                  + "User created but compliance must be resolved via HR module.",
              userId,
              department,
              missingFields);
        }
      }
    } catch (Exception e) {
      log.error(
          "HR Compliance check failed: userId={}, department={}. "
              + "User created successfully but compliance status is unknown. Error: {}",
          userId,
          department,
          e.getMessage());
    }
  }
}
