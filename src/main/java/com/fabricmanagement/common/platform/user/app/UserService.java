package com.fabricmanagement.common.platform.user.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.communication.app.AddressService;
import com.fabricmanagement.common.platform.communication.app.CompanyAddressService;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.app.UserAddressService;
import com.fabricmanagement.common.platform.communication.app.UserContactService;
import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.domain.AddressType;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.communication.domain.ContactType;
import com.fabricmanagement.common.platform.communication.domain.UserAddress;
import com.fabricmanagement.common.platform.company.api.facade.CompanyFacade;
import com.fabricmanagement.common.platform.user.api.facade.UserFacade;
import com.fabricmanagement.common.platform.user.domain.Role;
import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.common.platform.user.domain.event.UserDeactivatedEvent;
import com.fabricmanagement.common.platform.user.domain.event.UserOnboardingCompletedEvent;
import com.fabricmanagement.common.platform.user.domain.event.UserProfileUpdatedEvent;
import com.fabricmanagement.common.platform.user.domain.value.ProfileCategory;
import com.fabricmanagement.common.platform.user.dto.CreateExternalUserRequest;
import com.fabricmanagement.common.platform.user.dto.CreateInternalUserRequest;
import com.fabricmanagement.common.platform.user.dto.UpdateUserProfileRequest;
import com.fabricmanagement.common.platform.user.dto.UpdateUserRequest;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User Service - Business logic for user management.
 *
 * <p>Implements UserFacade for cross-module communication.
 *
 * <p>Key responsibilities:
 *
 * <ul>
 *   <li>User CRUD with tenant isolation
 *   <li>displayName auto-generation
 *   <li>Company validation
 *   <li>Domain event publishing
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements UserFacade {

  private final UserRepository userRepository;
  private final CompanyFacade companyFacade;
  private final DomainEventPublisher eventPublisher;
  private final UserProfilePermissionService permissionService;
  private final ContactService contactService;
  private final UserContactService userContactService;

  // USER-FRIENDLY: Auto-create Address from Company
  private final AddressService addressService;
  private final UserAddressService userAddressService;
  private final CompanyAddressService companyAddressService;
  private final UserDepartmentService userDepartmentService;

  private final RoleService roleService;
  private final com.fabricmanagement.common.platform.company.infra.repository.PositionRepository
      positionRepository;

  // WhatsApp capability check
  private final com.fabricmanagement.common.platform.communication.infra.client.WhatsAppClient
      whatsAppClient;

  // HR/Employee management
  private final com.fabricmanagement.human.core.employee.application.EmployeeService
      employeeService;

  /** Internal helper: Create user with basic information (shared logic for internal/external). */
  private UserDto createUserBase(
      String firstName,
      String lastName,
      String contactValue,
      com.fabricmanagement.common.platform.user.domain.ContactType contactType,
      UUID companyId,
      String department,
      List<com.fabricmanagement.common.platform.user.dto.ContactData> additionalContacts,
      List<com.fabricmanagement.common.platform.user.dto.AddressData> addresses) {

    UUID tenantId = TenantContext.getCurrentTenantId();

    // Check if contact already exists
    if (userRepository.existsByContactValue(contactValue)) {
      throw new IllegalArgumentException("Contact value already registered");
    }

    if (!companyFacade.exists(tenantId, companyId)) {
      throw new IllegalArgumentException("Company not found");
    }

    // Create User (new system - no deprecated fields)
    User user = User.create(firstName, lastName, companyId);
    User saved = userRepository.save(user);

    // Create primary Contact entity (authentication contact)
    com.fabricmanagement.common.platform.communication.domain.Contact primaryContact =
        createContactWithWhatsAppCheck(
            contactValue,
            mapContactType(contactType),
            "Primary",
            true, // isPersonal
            null // parentContactId
            );

    // Create UserContact junction (authentication contact)
    userContactService.assignContact(
        saved.getId(), primaryContact.getId(), true // isDefault
        );

    // Create additional contacts if provided
    if (additionalContacts != null && !additionalContacts.isEmpty()) {
      for (com.fabricmanagement.common.platform.user.dto.ContactData contactData :
          additionalContacts) {
        // ✅ Map ContactData to communication domain ContactType (supports MOBILE/LANDLINE)
        com.fabricmanagement.common.platform.communication.domain.ContactType mappedType =
            mapContactType(contactData);

        com.fabricmanagement.common.platform.communication.domain.Contact additionalContact =
            createContactWithWhatsAppCheck(
                contactData.getContactValue(),
                mappedType,
                contactData.getLabel() != null
                    ? contactData.getLabel()
                    : (contactData.getContactType()
                            == com.fabricmanagement.common.platform.user.domain.ContactType.EMAIL
                        ? "Email"
                        : mappedType
                                == com.fabricmanagement.common.platform.communication.domain
                                    .ContactType.LANDLINE
                            ? "Landline"
                            : "Mobile"),
                contactData.getIsPersonal() != null ? contactData.getIsPersonal() : true,
                null // parentContactId
                );

        // ✅ Set WhatsApp flag only for MOBILE phones (LANDLINE cannot have WhatsApp)
        if (contactData.getIsWhatsApp() != null
            && mappedType
                == com.fabricmanagement.common.platform.communication.domain.ContactType.MOBILE) {
          additionalContact.setIsWhatsApp(contactData.getIsWhatsApp());
          additionalContact = contactService.updateContact(additionalContact);
        } else if (mappedType
            == com.fabricmanagement.common.platform.communication.domain.ContactType.LANDLINE) {
          // ✅ LANDLINE phones cannot have WhatsApp - explicitly set to false
          additionalContact.setIsWhatsApp(false);
          additionalContact = contactService.updateContact(additionalContact);
        }

        // Assign contact to user (not for authentication, not default)
        userContactService.assignContact(
            saved.getId(), additionalContact.getId(), false // isDefault
            );
      }
    }

    // Create addresses if provided
    if (addresses != null && !addresses.isEmpty()) {
      boolean isFirstAddress = true;
      for (com.fabricmanagement.common.platform.user.dto.AddressData addressData : addresses) {
        com.fabricmanagement.common.platform.communication.domain.AddressType addressType =
            parseAddressType(addressData.getAddressType());

        // Determine label - WORK deprecated but kept for backward compatibility
        @SuppressWarnings("deprecation")
        boolean isWorkType = addressType == AddressType.OFFICE || addressType == AddressType.WORK;
        String addressLabel =
            addressData.getLabel() != null
                ? addressData.getLabel()
                : (isWorkType ? "Work Address" : "Home Address");

        com.fabricmanagement.common.platform.communication.domain.Address address =
            addressService.createAddress(
                addressData.getStreetAddress(),
                addressData.getCity(),
                addressData.getState(),
                addressData.getPostalCode(),
                addressData.getCountry(),
                addressType,
                addressLabel);

        // Assign address to user
        boolean isPrimary = isFirstAddress || Boolean.TRUE.equals(addressData.getIsPrimary());
        // WORK deprecated but kept for backward compatibility
        @SuppressWarnings("deprecation")
        boolean isWorkAddress =
            addressType == AddressType.OFFICE || addressType == AddressType.WORK;

        userAddressService.assignAddress(saved.getId(), address.getId(), isPrimary, isWorkAddress);

        isFirstAddress = false;
      }
    } else {
      // USER-FRIENDLY: Auto-create UserAddress from Company if available
      autoCreateUserAddressFromCompany(saved.getId(), companyId, tenantId);
    }

    eventPublisher.publish(
        new UserCreatedEvent(
            saved.getTenantId(),
            saved.getId(),
            saved.getDisplayName(),
            contactValue,
            saved.getCompanyId()));

    log.info(
        "User created: id={}, uid={}, displayName={}, contactId={}",
        saved.getId(),
        saved.getUid(),
        saved.getDisplayName(),
        primaryContact.getId());

    return UserDto.from(saved);
  }

  /**
   * Create internal employee (own staff with HR data).
   *
   * <p><b>Use Case:</b> Creating employees for your own company (tenant users with HR records).
   *
   * <p><b>Process:</b>
   *
   * <ol>
   *   <li>Create User (authentication, basic identity)
   *   <li>Create Employee record (HR data, personal info, employment details)
   * </ol>
   */
  @Transactional
  public UserDto createInternalUser(CreateInternalUserRequest request) {
    log.info(
        "Creating internal employee: contactValue={}",
        PiiMaskingUtil.maskEmail(request.getContactValue()));

    // Create User (authentication, basic identity)
    UserDto user =
        createUserBase(
            request.getFirstName(),
            request.getLastName(),
            request.getContactValue(),
            request.getContactType(),
            request.getCompanyId(),
            request.getDepartment(),
            request.getAdditionalContacts(),
            request.getAddresses());

    // Create Employee record (HR data) if any HR fields provided
    // Auto-generate employee number if not provided and any HR data exists
    String employeeNumber = request.getEmployeeNumber();
    if (employeeNumber == null || employeeNumber.isBlank()) {
      // Auto-generate employee number if any HR data will be saved
      if (request.getTitle() != null
          || request.getGender() != null
          || request.getBirthDate() != null
          || request.getNationality() != null
          || request.getHireDate() != null
          || request.getEmergencyContact() != null) {

        employeeNumber = employeeService.generateEmployeeNumber();
        log.debug(
            "Auto-generated employee number: {} for user: userId={}", employeeNumber, user.getId());
      }
    }

    if (request.getTitle() != null
        || request.getGender() != null
        || request.getBirthDate() != null
        || request.getNationality() != null
        || employeeNumber != null
        || request.getHireDate() != null
        || request.getEmergencyContact() != null) {

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
          user.getId(),
          request.getTitle(),
          request.getGender(),
          request.getBirthDate(),
          request.getNationality(),
          employeeNumber,
          request.getHireDate(),
          emergencyContact);

      log.info(
          "Employee record created for user: userId={}, employeeNumber={}",
          user.getId(),
          employeeNumber);
    }

    // Assign role if provided
    if (request.getRoleId() != null) {
      UUID tenantId = TenantContext.getCurrentTenantId();
      Role role =
          roleService
              .findById(request.getRoleId())
              .orElseThrow(() -> new IllegalArgumentException("Role not found"));

      User userEntity =
          userRepository
              .findByTenantIdAndId(tenantId, user.getId())
              .orElseThrow(() -> new IllegalArgumentException("User not found"));

      userEntity.setRole(role);
      userRepository.save(userEntity);

      log.info("Role assigned to user: userId={}, roleId={}", user.getId(), request.getRoleId());
    }

    // Assign department if departmentId provided (preferred over department name)
    if (request.getDepartmentId() != null) {
      UUID assignedBy = TenantContext.getCurrentUserId();

      userDepartmentService.assignDepartment(
          user.getId(),
          request.getDepartmentId(),
          true, // isPrimary
          assignedBy);

      log.info(
          "Department assigned to user: userId={}, departmentId={}",
          user.getId(),
          request.getDepartmentId());
    }

    // Assign position if provided
    if (request.getPositionId() != null) {
      UUID tenantId = TenantContext.getCurrentTenantId();
      com.fabricmanagement.common.platform.company.domain.Position position =
          positionRepository
              .findById(request.getPositionId())
              .orElseThrow(() -> new IllegalArgumentException("Position not found"));

      if (!position.getTenantId().equals(tenantId)) {
        throw new IllegalArgumentException("Position must belong to the same tenant");
      }

      User userEntity =
          userRepository
              .findByTenantIdAndId(tenantId, user.getId())
              .orElseThrow(() -> new IllegalArgumentException("User not found"));

      UUID assignedBy = TenantContext.getCurrentUserId();
      com.fabricmanagement.common.platform.user.domain.UserPosition userPosition =
          com.fabricmanagement.common.platform.user.domain.UserPosition.create(
              userEntity,
              position,
              true, // isPrimary
              assignedBy);

      // Add to user's position list (cascade will save it)
      userEntity.getUserPositions().add(userPosition);
      userRepository.save(userEntity);

      log.info(
          "Position assigned to user: userId={}, positionId={}",
          user.getId(),
          request.getPositionId());
    }

    // Track HR compliance (even if Employee record doesn't exist yet, it will be created on update)
    checkAndTrackHrCompliance(user.getId(), request.getDepartment());

    return user;
  }

  /**
   * Check HR compliance and update employee record with tracking data.
   *
   * <p>Validates recommended HR fields and saves compliance status to Employee entity.
   *
   * @param userId User ID to check compliance for
   * @param department Department name from request (optional)
   */
  private void checkAndTrackHrCompliance(UUID userId, String department) {
    try {
      Optional<com.fabricmanagement.human.core.employee.domain.Employee> employeeOpt =
          employeeService.getEmployeeByUserId(userId);

      if (employeeOpt.isPresent()) {
        com.fabricmanagement.human.core.employee.domain.Employee employee = employeeOpt.get();
        List<String> missingFields = employeeService.checkAndUpdateCompliance(employee, department);

        if (!missingFields.isEmpty()) {
          log.warn(
              "⚠️ HR Compliance Tracking: userId={}, missingFields={}, status={}",
              userId,
              String.join(",", missingFields),
              employee.getHrComplianceStatus());
        } else {
          log.info("✅ HR Compliance: userId={} is complete", userId);
        }
      }
    } catch (Exception e) {
      log.error("Failed to track HR compliance for userId={}", userId, e);
      // Don't fail user creation if compliance tracking fails
    }
  }

  /**
   * Create external user (partner/supplier/customer users without HR data).
   *
   * <p><b>Use Case:</b> Creating users for partner companies, suppliers, or customers (no HR
   * records needed).
   *
   * <p><b>Process:</b>
   *
   * <ol>
   *   <li>Create User only (authentication, basic identity)
   *   <li>No Employee record created
   * </ol>
   */
  @Transactional
  public UserDto createExternalUser(CreateExternalUserRequest request) {
    log.info(
        "Creating external user: contactValue={}",
        PiiMaskingUtil.maskEmail(request.getContactValue()));

    // Create User only (no Employee record)
    return createUserBase(
        request.getFirstName(),
        request.getLastName(),
        request.getContactValue(),
        request.getContactType(),
        request.getCompanyId(),
        request.getDepartment(),
        request.getAdditionalContacts(),
        request.getAddresses());
  }

  /**
   * Create contact with automatic WhatsApp capability check for phone numbers.
   *
   * <p>For PHONE contacts, checks WhatsApp capability and sets isWhatsApp flag.
   *
   * @param contactValue Contact value (email or phone)
   * @param contactType Contact type (EMAIL or PHONE)
   * @param label Contact label
   * @param isPersonal Personal contact flag
   * @param parentContactId Parent contact ID (for extensions)
   * @return Created contact with WhatsApp flag set if applicable
   */
  private com.fabricmanagement.common.platform.communication.domain.Contact
      createContactWithWhatsAppCheck(
          String contactValue,
          com.fabricmanagement.common.platform.communication.domain.ContactType contactType,
          String label,
          Boolean isPersonal,
          UUID parentContactId) {

    com.fabricmanagement.common.platform.communication.domain.Contact contact =
        contactService.createContact(contactValue, contactType, label, isPersonal, parentContactId);

    // Check WhatsApp capability for PHONE contacts
    if (contactType.isMobile()) {
      try {
        boolean hasWhatsApp = whatsAppClient.phoneHasWhatsApp(contactValue);
        contact.setIsWhatsApp(hasWhatsApp);
        contact = contactService.updateContact(contact); // Save WhatsApp flag

        if (hasWhatsApp) {
          log.debug(
              "✅ WhatsApp capability detected: phone={}, contactId={}",
              PiiMaskingUtil.maskPhone(contactValue),
              contact.getId());
        }
      } catch (Exception e) {
        log.warn(
            "Failed to check WhatsApp capability for phone: {} - {}",
            PiiMaskingUtil.maskPhone(contactValue),
            e.getMessage());
        // Continue - WhatsApp check failure is not critical
      }
    }

    return contact;
  }

  /** Parse address type string to AddressType enum. */
  private com.fabricmanagement.common.platform.communication.domain.AddressType parseAddressType(
      String addressTypeStr) {
    if (addressTypeStr == null || addressTypeStr.isBlank()) {
      return AddressType.OFFICE;
    }

    try {
      return AddressType.valueOf(addressTypeStr.toUpperCase());
    } catch (IllegalArgumentException e) {
      log.warn("Invalid address type: {}, defaulting to OFFICE", addressTypeStr);
      return AddressType.OFFICE;
    }
  }

  /**
   * USER-FRIENDLY: Auto-create UserAddress from Company's primary address if available.
   *
   * <p>Benefits:
   *
   * <ul>
   *   <li>Users automatically get work address from their company
   *   <li>Reduces manual data entry
   *   <li>Ensures data consistency
   * </ul>
   */
  private void autoCreateUserAddressFromCompany(UUID userId, UUID companyId, UUID tenantId) {
    try {
      // Get company's primary address (if exists)
      Optional<com.fabricmanagement.common.platform.communication.domain.CompanyAddress>
          companyAddressOpt = companyAddressService.getPrimaryAddress(companyId);

      if (companyAddressOpt.isPresent()) {
        com.fabricmanagement.common.platform.communication.domain.CompanyAddress companyAddress =
            companyAddressOpt.get();

        // Get Address entity from AddressRepository (CompanyAddress has addressId)
        com.fabricmanagement.common.platform.communication.domain.Address companyAddr =
            addressService
                .findById(companyAddress.getAddressId())
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Company address found but Address entity not found: "
                                + companyAddress.getAddressId()));

        // Create UserAddress from Company Address (WORK address)
        com.fabricmanagement.common.platform.communication.domain.Address userWorkAddress =
            addressService.createAddress(
                companyAddr.getStreetAddress(),
                companyAddr.getCity(),
                companyAddr.getState(),
                companyAddr.getPostalCode(),
                companyAddr.getCountry(),
                AddressType.OFFICE,
                "Work Address");

        userAddressService.assignAddress(
            userId,
            userWorkAddress.getId(),
            true, // isPrimary (first address = primary)
            true // isWorkAddress
            );

        log.info(
            "✅ User work address auto-created from company: userId={}, companyId={}",
            userId,
            companyId);
      } else {
        log.debug(
            "Company has no primary address, skipping user address auto-creation: companyId={}",
            companyId);
      }
    } catch (Exception e) {
      log.warn(
          "Failed to auto-create user address from company: userId={}, companyId={}, error={}",
          userId,
          companyId,
          e.getMessage());
      // Continue - address creation is optional
    }
  }

  /** Map User module ContactType to Communication module ContactType. */
  /**
   * Map user domain ContactType to communication domain ContactType. For PHONE, defaults to MOBILE
   * (backward compatibility).
   */
  private com.fabricmanagement.common.platform.communication.domain.ContactType mapContactType(
      com.fabricmanagement.common.platform.user.domain.ContactType userContactType) {
    return switch (userContactType) {
      case EMAIL -> com.fabricmanagement.common.platform.communication.domain.ContactType.EMAIL;
      case PHONE -> com.fabricmanagement.common.platform.communication.domain.ContactType.MOBILE;
    };
  }

  /**
   * Map ContactData to communication domain ContactType. Supports MOBILE/LANDLINE distinction for
   * PHONE contacts.
   *
   * @param contactData ContactData with optional phoneType field
   * @return Communication domain ContactType (EMAIL, MOBILE, or LANDLINE)
   */
  private com.fabricmanagement.common.platform.communication.domain.ContactType mapContactType(
      com.fabricmanagement.common.platform.user.dto.ContactData contactData) {
    if (contactData.getContactType()
        == com.fabricmanagement.common.platform.user.domain.ContactType.EMAIL) {
      return com.fabricmanagement.common.platform.communication.domain.ContactType.EMAIL;
    }

    // PHONE contact - check phoneType field
    if (contactData.getContactType()
        == com.fabricmanagement.common.platform.user.domain.ContactType.PHONE) {
      String phoneType = contactData.getPhoneType();
      if ("LANDLINE".equalsIgnoreCase(phoneType)) {
        return com.fabricmanagement.common.platform.communication.domain.ContactType.LANDLINE;
      }
      // Default to MOBILE (backward compatibility)
      return com.fabricmanagement.common.platform.communication.domain.ContactType.MOBILE;
    }

    // Fallback (should not happen)
    return com.fabricmanagement.common.platform.communication.domain.ContactType.MOBILE;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserDto> findById(UUID tenantId, UUID userId) {
    log.debug("Finding user: tenantId={}, userId={}", tenantId, userId);

    return userRepository.findByTenantIdAndId(tenantId, userId).map(UserDto::from);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserDto> findByContactValue(String contactValue) {
    log.debug("Finding user by contact: contactValue={}", PiiMaskingUtil.maskEmail(contactValue));

    return userRepository.findByContactValue(contactValue).map(UserDto::from);
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserDto> findByTenant(UUID tenantId) {
    log.debug("Finding users by tenant: tenantId={}", tenantId);

    return userRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
        .map(UserDto::from)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserDto> findByCompany(UUID tenantId, UUID companyId) {
    log.debug("Finding users by company: tenantId={}, companyId={}", tenantId, companyId);

    return userRepository.findByTenantIdAndCompanyIdAndIsActiveTrue(tenantId, companyId).stream()
        .map(UserDto::from)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean exists(UUID tenantId, UUID userId) {
    return userRepository.existsByTenantIdAndId(tenantId, userId);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean contactExists(String contactValue) {
    return userRepository.existsByContactValue(contactValue);
  }

  @Transactional
  public UserDto updateUser(UUID userId, UpdateUserRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Updating user: tenantId={}, userId={}", tenantId, userId);

    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    user.updateProfile(request.getFirstName(), request.getLastName());
    // Department updates handled via UserDepartmentService

    User saved = userRepository.save(user);

    log.info("User updated: id={}, displayName={}", saved.getId(), saved.getDisplayName());

    return UserDto.from(saved);
  }

  @Transactional
  public void deactivateUser(UUID userId, String reason) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Deactivating user: tenantId={}, userId={}, reason={}", tenantId, userId, reason);

    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    user.delete();
    userRepository.save(user);

    eventPublisher.publish(new UserDeactivatedEvent(tenantId, userId, reason));

    log.warn("User deactivated: id={}, uid={}", user.getId(), user.getUid());
  }

  @Transactional(readOnly = true)
  public boolean hasCompletedOnboarding(UUID userId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.trace("Checking onboarding status: tenantId={}, userId={}", tenantId, userId);

    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    return user.hasCompletedOnboarding();
  }

  @Transactional
  public UserDto completeOnboarding(UUID userId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Completing onboarding: tenantId={}, userId={}", tenantId, userId);

    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    if (user.hasCompletedOnboarding()) {
      log.debug("User already completed onboarding: userId={}", userId);
      return UserDto.from(user);
    }

    user.completeOnboarding();
    User saved = userRepository.save(user);

    eventPublisher.publish(new UserOnboardingCompletedEvent(saved.getTenantId(), saved.getId()));

    log.info("✅ Onboarding completed: userId={}, uid={}", saved.getId(), saved.getUid());

    return UserDto.from(saved);
  }

  /**
   * Update user profile with permission checks.
   *
   * <p><b>Security:</b> Self-update is NOT allowed. Only Admin/HR/Dept Manager can update profiles.
   *
   * @param userId Target user ID
   * @param request Update request with field categories
   * @param requesterId User requesting the update (from TenantContext)
   * @return Updated user DTO
   */
  @Transactional
  @SuppressWarnings("deprecation")
  public UserDto updateProfile(UUID userId, UpdateUserProfileRequest request, UUID requesterId) {
    log.info("Updating user profile: userId={}, requesterId={}", userId, requesterId);

    // Validate request has updates
    if (!request.hasUpdates()) {
      throw new IllegalArgumentException("No fields provided for update");
    }

    // Permission checks
    Set<ProfileCategory> categories = request.getUpdatedCategories();

    for (ProfileCategory category : categories) {
      boolean allowed = false;

      if (category == ProfileCategory.WORK_PROFILE) {
        allowed = permissionService.canUpdateWorkProfile(requesterId, userId);
        if (!allowed) {
          throw new org.springframework.security.access.AccessDeniedException(
              "You don't have permission to update work profile. "
                  + "Only Admin, HR Manager, or Department Manager (same department) can update work profiles.");
        }
      } else if (category == ProfileCategory.PERSONAL_PROFILE) {
        allowed = permissionService.canUpdatePersonalProfile(requesterId, userId);
        if (!allowed) {
          throw new org.springframework.security.access.AccessDeniedException(
              "You don't have permission to update personal profile. "
                  + "Only Admin or HR Manager can update personal profiles.");
        }
      }
    }

    // Self-update prevention (additional check for safety)
    if (userId.equals(requesterId)) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Users cannot update their own profile. "
              + "Please contact HR or Admin to update your profile information.");
    }

    // Get user
    User user =
        userRepository
            .findByTenantIdAndId(TenantContext.getCurrentTenantId(), userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    // Update work profile fields
    if (request.getFirstName() != null || request.getLastName() != null) {
      String firstName =
          request.getFirstName() != null ? request.getFirstName() : user.getFirstName();
      String lastName = request.getLastName() != null ? request.getLastName() : user.getLastName();
      user.updateProfile(firstName, lastName);
    }

    // Update work email contact
    if (request.getWorkEmail() != null) {
      updateWorkContact(userId, request.getWorkEmail(), ContactType.EMAIL);
      log.debug(
          "Work email updated: userId={}, email={}",
          userId,
          PiiMaskingUtil.maskEmail(request.getWorkEmail()));
    }

    // Update work phone contact
    if (request.getWorkPhone() != null) {
      updateWorkContact(userId, request.getWorkPhone(), ContactType.MOBILE);
      log.debug(
          "Work phone updated: userId={}, phone={}",
          userId,
          PiiMaskingUtil.maskPhone(request.getWorkPhone()));
    }

    // Update work address
    if (request.getWorkAddress() != null) {
      updateWorkAddress(userId, request.getWorkAddress());
      log.debug("Work address updated: userId={}", userId);
    }

    // Update department
    if (request.getDepartmentId() != null) {
      // Check if user already has a primary department
      boolean hasPrimaryDept = userDepartmentService.getPrimaryDepartment(userId).isPresent();

      userDepartmentService.assignDepartment(
          userId,
          request.getDepartmentId(),
          !hasPrimaryDept, // Set as primary if no primary exists
          requesterId);
      log.debug(
          "Department updated: userId={}, departmentId={}", userId, request.getDepartmentId());
    }

    // Update personal profile fields
    if (request.getHomeAddress() != null) {
      updateHomeAddress(userId, request.getHomeAddress());
      log.debug("Home address updated: userId={}", userId);
    }

    if (request.getPersonalPhone() != null) {
      updatePersonalContact(userId, request.getPersonalPhone(), ContactType.MOBILE);
      log.debug("Personal phone updated: userId={}", userId);
    }

    // Note: birthDate and emergencyContact would require additional entity fields
    // These can be added in a future enhancement

    User saved = userRepository.save(user);

    // Publish event for audit
    eventPublisher.publish(
        new UserProfileUpdatedEvent(saved.getTenantId(), saved.getId(), requesterId, categories));

    log.info(
        "✅ Profile updated: userId={}, updatedBy={}, categories={}",
        saved.getId(),
        requesterId,
        categories);

    return UserDto.from(saved);
  }

  /**
   * Update work contact (email or phone) for user. Creates contact if not exists, assigns to user
   * as work contact.
   */
  private void updateWorkContact(UUID userId, String contactValue, ContactType contactType) {
    // Find or create contact
    Contact contact =
        contactService
            .findByValueAndType(contactValue, contactType)
            .orElseGet(
                () -> {
                  String maskedValue =
                      contactType == ContactType.EMAIL
                          ? PiiMaskingUtil.maskEmail(contactValue)
                          : PiiMaskingUtil.maskPhone(contactValue);
                  log.debug(
                      "Creating new work contact: type={}, value={}", contactType, maskedValue);
                  return contactService.createContact(
                      contactValue,
                      contactType,
                      "Work " + contactType.name().toLowerCase(), // Label
                      false, // isPersonal = false for work contacts
                      null // No parent contact
                      );
                });

    // ✅ Performance: Direct exists check instead of loading all contacts
    if (userContactService.existsUserContact(userId, contact.getId())) {
      log.debug(
          "Contact already assigned to user: userId={}, contactId={}", userId, contact.getId());
      return;
    }

    // Assign contact to user (work contact, not for authentication, can be default)
    userContactService.assignContact(userId, contact.getId(), false);
    log.debug(
        "Work contact assigned: userId={}, contactId={}, type={}",
        userId,
        contact.getId(),
        contactType);
  }

  /**
   * Update personal contact (phone) for user. Creates contact if not exists, assigns to user as
   * personal contact.
   */
  private void updatePersonalContact(UUID userId, String contactValue, ContactType contactType) {
    // Find or create contact
    Contact contact =
        contactService
            .findByValueAndType(contactValue, contactType)
            .orElseGet(
                () -> {
                  String maskedValue =
                      contactType == ContactType.EMAIL
                          ? PiiMaskingUtil.maskEmail(contactValue)
                          : PiiMaskingUtil.maskPhone(contactValue);
                  log.debug(
                      "Creating new personal contact: type={}, value={}", contactType, maskedValue);
                  return contactService.createContact(
                      contactValue,
                      contactType,
                      "Personal " + contactType.name().toLowerCase(), // Label
                      true, // isPersonal = true for personal contacts
                      null // No parent contact
                      );
                });

    // ✅ Performance: Direct exists check instead of loading all contacts
    if (userContactService.existsUserContact(userId, contact.getId())) {
      log.debug(
          "Personal contact already assigned to user: userId={}, contactId={}",
          userId,
          contact.getId());
      return;
    }

    // Assign contact to user (personal contact, not for authentication, not default)
    userContactService.assignContact(userId, contact.getId(), false);
    log.debug(
        "Personal contact assigned: userId={}, contactId={}, type={}",
        userId,
        contact.getId(),
        contactType);
  }

  /**
   * Update work address for user. Creates address if not exists, assigns to user as work address.
   */
  private void updateWorkAddress(UUID userId, UpdateUserProfileRequest.AddressData addressData) {
    // Create address
    Address address =
        addressService.createAddress(
            addressData.getStreetAddress(),
            addressData.getCity(),
            addressData.getState(),
            addressData.getPostalCode(),
            addressData.getCountry(),
            AddressType.OFFICE,
            "Work Address");

    // Check if already assigned to user
    if (userAddressService.getUserAddresses(userId).stream()
        .anyMatch(ua -> ua.getAddressId().equals(address.getId()))) {
      log.debug(
          "Work address already assigned to user: userId={}, addressId={}",
          userId,
          address.getId());
      return;
    }

    // Assign address to user (work address, not primary)
    userAddressService.assignAddress(userId, address.getId(), false, true); // isWorkAddress = true
    log.debug("Work address assigned: userId={}, addressId={}", userId, address.getId());
  }

  /**
   * Update home address for user. Creates address if not exists, assigns to user as home address.
   */
  private void updateHomeAddress(UUID userId, UpdateUserProfileRequest.AddressData addressData) {
    // Create address
    Address address =
        addressService.createAddress(
            addressData.getStreetAddress(),
            addressData.getCity(),
            addressData.getState(),
            addressData.getPostalCode(),
            addressData.getCountry(),
            AddressType.HOME,
            "Home Address");

    // Check if already assigned to user
    if (userAddressService.getUserAddresses(userId).stream()
        .anyMatch(ua -> ua.getAddressId().equals(address.getId()))) {
      log.debug(
          "Home address already assigned to user: userId={}, addressId={}",
          userId,
          address.getId());
      return;
    }

    // Assign address to user (home address, can be primary if no primary exists)
    Optional<UserAddress> primaryAddress = userAddressService.getPrimaryAddress(userId);
    boolean isPrimary = primaryAddress.isEmpty();

    userAddressService.assignAddress(
        userId, address.getId(), isPrimary, false); // isWorkAddress = false
    log.debug(
        "Home address assigned: userId={}, addressId={}, isPrimary={}",
        userId,
        address.getId(),
        isPrimary);
  }
}
