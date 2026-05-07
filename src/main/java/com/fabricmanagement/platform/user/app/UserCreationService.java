package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.identity.EmergencyContactData;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.app.AddressService;
import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.domain.AddressType;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.organization.api.facade.OrganizationFacade;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.platform.user.domain.port.EmployeeCompliancePort;
import com.fabricmanagement.platform.user.domain.port.EmployeeCreationCommand;
import com.fabricmanagement.platform.user.domain.port.EmployeeCreationPort;
import com.fabricmanagement.platform.user.domain.port.EmployeeProjectionPort;
import com.fabricmanagement.platform.user.domain.port.JobTitlePresetRepository;
import com.fabricmanagement.platform.user.dto.AddressData;
import com.fabricmanagement.platform.user.dto.ContactData;
import com.fabricmanagement.platform.user.dto.CreateAdminUserRequest;
import com.fabricmanagement.platform.user.dto.CreateExternalUserRequest;
import com.fabricmanagement.platform.user.dto.CreateInternalUserRequest;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
  private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{1,14}$");

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
  private final DepartmentRepository departmentRepository;
  private final UserWorkLocationService userWorkLocationService;
  private final EmployeeCreationPort employeeCreationPort;
  private final EmployeeProjectionPort employeeProjectionPort;
  private final EmployeeCompliancePort employeeCompliancePort;
  private final JobTitlePresetRepository jobTitlePresetRepository;

  @Transactional
  public UserDto createInternalUser(CreateInternalUserRequest request) {
    log.info(
        "Creating internal employee: contactValue={}",
        PiiMaskingUtil.maskEmail(request.getContactValue()));

    User userEntity =
        createUserEntity(
            request.getFirstName(),
            request.getLastName(),
            request.getContactValue(),
            request.getContactType(),
            request.getOrganizationId(),
            com.fabricmanagement.platform.user.domain.UserType.INTERNAL,
            request.getAdditionalContacts(),
            request.getAddresses());

    UUID assignedBy = TenantContext.getCurrentUserId();

    // Step 2: Assign role
    if (request.getRoleId() != null) {
      Role role =
          roleService
              .findById(request.getRoleId())
              .orElseThrow(
                  () ->
                      new PlatformDomainException(
                          "Role not found", "USER_CREATION_ROLE_NOT_FOUND", 404));
      userEntity.setRole(role);
    }

    User saved = userRepository.save(userEntity);

    // Step 3: Assign department (uses separate junction service)
    if (request.getDepartmentId() != null) {
      userDepartmentService.assignDepartment(
          saved.getId(), request.getDepartmentId(), true, assignedBy);
    }

    // Step 4: Assign work location (org address the user works at)
    if (request.getWorkLocationOrgAddressId() != null) {
      userWorkLocationService.assignLocation(
          saved.getId(), request.getWorkLocationOrgAddressId(), true, null);
    }

    // Step 5: Create Employee record if any HR data provided
    createEmployeeIfNeeded(saved.getId(), request);

    // Step 6: Publish event
    String contactValue = request.getContactValue().trim().toLowerCase(Locale.ROOT);
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
    return UserDto.from(saved, employeeProjectionPort.findByUserId(saved.getId()).orElse(null));
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
            request.getOrganizationId(),
            com.fabricmanagement.platform.user.domain.UserType.EXTERNAL,
            request.getAdditionalContacts(),
            request.getAddresses());

    // Assign default external role if available (EXTERNAL_USER or VIEWER)
    roleService
        .findByCode("EXTERNAL_USER")
        .or(() -> roleService.findByCode("VIEWER"))
        .ifPresentOrElse(
            role -> {
              userEntity.setRole(role);
              userRepository.save(userEntity);
            },
            () ->
                log.warn(
                    "No default role for external user. "
                        + "Consider creating EXTERNAL_USER or VIEWER platform role. userId={}",
                    userEntity.getId()));

    String contactValue = request.getContactValue().trim().toLowerCase(Locale.ROOT);

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

    String normalizedContact = request.getContactValue().trim().toLowerCase(Locale.ROOT);
    String trimmedFirstName = request.getFirstName().trim();
    String trimmedLastName = request.getLastName().trim();

    validateContactFormat(
        normalizedContact, com.fabricmanagement.platform.user.domain.ContactType.EMAIL);

    return TenantContext.executeInTenantContext(
        request.getTenantId(),
        () -> {
          if (userRepository.existsByTenantIdAndContactValue(
              request.getTenantId(), normalizedContact)) {
            throw new PlatformDomainException(
                "Contact value already registered for this tenant",
                "USER_CONTACT_ALREADY_REGISTERED",
                409);
          }

          User user =
              User.create(
                  trimmedFirstName,
                  trimmedLastName,
                  request.getOrganizationId(),
                  com.fabricmanagement.platform.user.domain.UserType.INTERNAL);

          com.fabricmanagement.platform.user.domain.Role role =
              roleService.findTenantAdminRoleOrThrow(request.getTenantId());
          user.setRole(role);

          User saved = userRepository.save(user);

          com.fabricmanagement.platform.communication.domain.Contact contact =
              contactService.createContact(
                  normalizedContact,
                  com.fabricmanagement.platform.communication.domain.ContactType.EMAIL,
                  "Primary",
                  true,
                  null);
          userContactAssignmentService.assignContact(saved.getId(), contact.getId(), true);

          assignAdminDefaultDepartment(saved, request);

          try {
            String employeeNumber = employeeCreationPort.generateEmployeeNumber();
            employeeCreationPort.createOrUpdate(
                new EmployeeCreationCommand(
                    saved.getId(),
                    null,
                    null,
                    null,
                    null,
                    employeeNumber,
                    java.time.LocalDate.now(),
                    null,
                    null));
            log.info(
                "Initialized default Employee profile for admin: userId={}, empNo={}",
                saved.getId(),
                employeeNumber);
          } catch (Exception e) {
            log.error("Failed to initialize HR profile for admin user: {}", e.getMessage());
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
      com.fabricmanagement.platform.user.domain.ContactType contactType,
      UUID organizationId,
      com.fabricmanagement.platform.user.domain.UserType userType,
      List<ContactData> additionalContacts,
      List<AddressData> addresses) {

    String trimmedFirstName = firstName.trim();
    String trimmedLastName = lastName.trim();
    String normalizedContact = contactValue.trim().toLowerCase(Locale.ROOT);

    UUID tenantId = TenantContext.getCurrentTenantId();

    // --- All validations BEFORE any persistence ---
    validateContactFormat(normalizedContact, contactType);

    if (userRepository.existsByTenantIdAndContactValue(tenantId, normalizedContact)) {
      throw new PlatformDomainException(
          "Contact value already registered", "USER_CONTACT_ALREADY_REGISTERED", 409);
    }
    if (!organizationFacade.exists(tenantId, organizationId)) {
      throw new PlatformDomainException("Organization not found", "ORG_NOT_FOUND", 404);
    }

    // Validate primary contact is not duplicated in additional contacts
    if (additionalContacts != null && !additionalContacts.isEmpty()) {
      Set<String> seenContacts = new HashSet<>();
      seenContacts.add(normalizedContact);

      for (ContactData cd : additionalContacts) {
        String normalized = cd.getContactValue().trim().toLowerCase(Locale.ROOT);

        validateContactFormat(normalized, cd.getContactType());

        if (!seenContacts.add(normalized)) {
          throw new PlatformDomainException(
              "Duplicate contact value detected",
              "USER_CONTACT_DUPLICATE",
              400,
              new Object[] {PiiMaskingUtil.maskEmail(cd.getContactValue())});
        }
      }
    }

    // --- Persistence starts here ---
    try {
      User user = User.create(trimmedFirstName, trimmedLastName, organizationId, userType);
      User saved = userRepository.save(user);

      Contact primaryContact =
          contactService.createContact(
              normalizedContact, mapContactType(contactType), "Primary", true, null);
      userContactAssignmentService.assignContact(saved.getId(), primaryContact.getId(), true);

      if (additionalContacts != null && !additionalContacts.isEmpty()) {
        for (ContactData contactData : additionalContacts) {
          ContactType mappedType = mapContactType(contactData);
          String label =
              contactData.getLabel() != null && !contactData.getLabel().isBlank()
                  ? contactData.getLabel().trim()
                  : (contactData.getContactType()
                          == com.fabricmanagement.platform.user.domain.ContactType.EMAIL
                      ? "Email"
                      : mappedType == ContactType.LANDLINE ? "Landline" : "Mobile");

          String normalizedContactValue =
              contactData.getContactValue().trim().toLowerCase(Locale.ROOT);

          Contact additionalContact =
              contactService.createContact(
                  normalizedContactValue,
                  mappedType,
                  label,
                  contactData.getIsPersonal() != null ? contactData.getIsPersonal() : true,
                  null);
          userContactAssignmentService.assignContact(
              saved.getId(), additionalContact.getId(), false);
        }
      }

      assignAddresses(saved, organizationId, tenantId, addresses);

      return saved;
    } catch (DataIntegrityViolationException e) {
      log.warn("Concurrent duplicate contact detected: {}", normalizedContact);
      throw new PlatformDomainException(
          "Contact value already registered", "USER_CONTACT_ALREADY_REGISTERED", 409);
    }
  }

  /** Assign addresses to user. Uses organization primary address as fallback when none provided. */
  private void assignAddresses(
      User user, UUID organizationId, UUID tenantId, List<AddressData> addresses) {
    if (addresses != null && !addresses.isEmpty()) {
      // Check if any address is explicitly marked as primary
      boolean hasExplicitPrimary =
          addresses.stream().anyMatch(a -> Boolean.TRUE.equals(a.getIsPrimary()));

      boolean primaryAlreadyAssigned = false;
      for (int i = 0; i < addresses.size(); i++) {
        AddressData addressData = addresses.get(i);
        com.fabricmanagement.platform.communication.domain.AddressType addressType =
            parseAddressType(addressData.getAddressType());
        boolean isWorkType = addressType == AddressType.OFFICE;
        String addressLabel =
            addressData.getLabel() != null && !addressData.getLabel().isBlank()
                ? addressData.getLabel().trim()
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

        boolean isPrimary;
        if (hasExplicitPrimary) {
          isPrimary = Boolean.TRUE.equals(addressData.getIsPrimary()) && !primaryAlreadyAssigned;
        } else {
          isPrimary = (i == 0);
        }

        if (isPrimary) {
          primaryAlreadyAssigned = true;
        }

        boolean isWorkAddress = addressType == AddressType.OFFICE;
        userAddressAssignmentService.assignAddress(
            user.getId(), address.getId(), isPrimary, isWorkAddress);
      }
    } else {
      userAddressAutoService.copyOrganizationPrimaryAddress(user.getId(), organizationId, tenantId);
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
            || (request.getJobTitleCode() != null && !request.getJobTitleCode().isBlank())
            || (request.getEmployeeNumber() != null && !request.getEmployeeNumber().isBlank());

    if (!hasAnyHrData) {
      return;
    }

    String employeeNumber = request.getEmployeeNumber();
    if (employeeNumber == null || employeeNumber.isBlank()) {
      employeeNumber = employeeCreationPort.generateEmployeeNumber();
    }

    EmergencyContactData emergencyContact = null;
    if (request.getEmergencyContact() != null) {
      emergencyContact =
          new EmergencyContactData(
              request.getEmergencyContact().getName(),
              request.getEmergencyContact().getPhone(),
              request.getEmergencyContact().getRelationship());
    }

    UUID jobTitlePresetId = null;
    if (request.getJobTitleCode() != null && !request.getJobTitleCode().isBlank()) {
      jobTitlePresetId =
          jobTitlePresetRepository
              .findByTenantIdAndJobTitleCode(
                  TenantContext.getCurrentTenantId(), request.getJobTitleCode())
              .map(com.fabricmanagement.platform.user.domain.JobTitlePreset::getId)
              .orElse(null);
    }

    employeeCreationPort.createOrUpdate(
        new EmployeeCreationCommand(
            userId,
            request.getTitle(),
            request.getGender(),
            request.getBirthDate(),
            request.getNationality(),
            employeeNumber,
            request.getHireDate(),
            emergencyContact,
            jobTitlePresetId));
  }

  private com.fabricmanagement.platform.communication.domain.AddressType parseAddressType(
      String addressTypeStr) {
    if (addressTypeStr == null || addressTypeStr.isBlank()) {
      return AddressType.OFFICE;
    }
    String upper = addressTypeStr.toUpperCase();
    if ("WORK".equals(upper)) {
      return AddressType.OFFICE;
    }
    try {
      return com.fabricmanagement.platform.communication.domain.AddressType.valueOf(upper);
    } catch (IllegalArgumentException e) {
      log.warn("Invalid address type: {}, defaulting to OFFICE", addressTypeStr);
      return AddressType.OFFICE;
    }
  }

  private void validateContactFormat(
      String contactValue, com.fabricmanagement.platform.user.domain.ContactType contactType) {
    if (contactType == com.fabricmanagement.platform.user.domain.ContactType.EMAIL) {
      if (!EMAIL_PATTERN.matcher(contactValue).matches()) {
        throw new PlatformDomainException(
            "Invalid email format",
            "USER_CONTACT_INVALID_EMAIL",
            400,
            new Object[] {PiiMaskingUtil.maskEmail(contactValue)});
      }
    } else if (contactType == com.fabricmanagement.platform.user.domain.ContactType.PHONE) {
      if (!PHONE_PATTERN.matcher(contactValue).matches()) {
        throw new PlatformDomainException(
            "Invalid phone format. Must be E.164 format (e.g., +905551234567)",
            "USER_CONTACT_INVALID_PHONE",
            400);
      }
    }
  }

  private ContactType mapContactType(
      com.fabricmanagement.platform.user.domain.ContactType userContactType) {
    return switch (userContactType) {
      case EMAIL -> ContactType.EMAIL;
      case PHONE -> ContactType.MOBILE;
    };
  }

  private ContactType mapContactType(ContactData contactData) {
    if (contactData.getContactType()
        == com.fabricmanagement.platform.user.domain.ContactType.EMAIL) {
      return ContactType.EMAIL;
    }
    if (contactData.getContactType()
        == com.fabricmanagement.platform.user.domain.ContactType.PHONE) {
      String phoneType = contactData.getPhoneType();
      if (ContactData.PHONE_TYPE_LANDLINE.equalsIgnoreCase(phoneType)) {
        return ContactType.LANDLINE;
      }
      return ContactType.MOBILE;
    }
    return ContactType.MOBILE;
  }

  private static final String ADMIN_DEFAULT_DEPARTMENT = "Administration Office";

  /**
   * Assign the default "Administration Office" department to admin user during onboarding. Falls
   * back silently if the department doesn't exist yet (seed step may not have run).
   */
  private void assignAdminDefaultDepartment(User savedUser, CreateAdminUserRequest request) {
    try {
      Optional<Department> adminDept =
          departmentRepository.findByTenantIdAndOrganizationIdAndDepartmentName(
              request.getTenantId(), request.getOrganizationId(), ADMIN_DEFAULT_DEPARTMENT);

      if (adminDept.isPresent()) {
        userDepartmentService.assignDepartment(
            savedUser.getId(), adminDept.get().getId(), true, null);
        log.debug(
            "Admin default department assigned: userId={}, department={}",
            savedUser.getId(),
            ADMIN_DEFAULT_DEPARTMENT);
      } else {
        log.warn(
            "Admin default department '{}' not found — skipping assignment: tenantId={}, orgId={}",
            ADMIN_DEFAULT_DEPARTMENT,
            request.getTenantId(),
            request.getOrganizationId());
      }
    } catch (Exception e) {
      log.error(
          "Failed to assign admin default department: userId={}, error={}",
          savedUser.getId(),
          e.getMessage());
    }
  }

  /**
   * Check and track HR compliance for employee. Non-critical — failures are logged as errors but do
   * NOT prevent user creation (compliance can be resolved later via HR module).
   */
  private void checkAndTrackHrCompliance(UUID userId, String department) {
    try {
      List<String> missingFields =
          employeeCompliancePort.runComplianceEvaluation(userId, department);
      if (!missingFields.isEmpty()) {
        log.warn(
            "HR Compliance incomplete: userId={}, department={}, missingFields={}. "
                + "User created but compliance must be resolved via HR module.",
            userId,
            department,
            missingFields);
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
