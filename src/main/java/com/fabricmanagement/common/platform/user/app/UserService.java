package com.fabricmanagement.common.platform.user.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.company.api.facade.CompanyFacade;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.app.UserContactService;
import com.fabricmanagement.common.platform.communication.app.AddressService;
import com.fabricmanagement.common.platform.communication.app.UserAddressService;
import com.fabricmanagement.common.platform.communication.app.CompanyAddressService;
import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.domain.AddressType;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.communication.domain.ContactType;
import com.fabricmanagement.common.platform.communication.domain.UserAddress;
import com.fabricmanagement.common.platform.user.api.facade.UserFacade;
import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.common.platform.user.domain.event.UserDeactivatedEvent;
import com.fabricmanagement.common.platform.user.domain.event.UserOnboardingCompletedEvent;
import com.fabricmanagement.common.platform.user.domain.event.UserProfileUpdatedEvent;
import com.fabricmanagement.common.platform.user.domain.value.ProfileCategory;
import com.fabricmanagement.common.platform.user.dto.CreateUserRequest;
import com.fabricmanagement.common.platform.user.dto.UpdateUserRequest;
import com.fabricmanagement.common.platform.user.dto.UpdateUserProfileRequest;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * User Service - Business logic for user management.
 *
 * <p>Implements UserFacade for cross-module communication.</p>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>User CRUD with tenant isolation</li>
 *   <li>displayName auto-generation</li>
 *   <li>Company validation</li>
 *   <li>Domain event publishing</li>
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

    @Transactional
    public UserDto createUser(CreateUserRequest request) {
        log.info("Creating user: contactValue={}", 
            PiiMaskingUtil.maskEmail(request.getContactValue()));

        UUID tenantId = TenantContext.getCurrentTenantId();

        // Check if contact already exists
        if (userRepository.existsByContactValue(request.getContactValue())) {
            throw new IllegalArgumentException("Contact value already registered");
        }

        if (!companyFacade.exists(tenantId, request.getCompanyId())) {
            throw new IllegalArgumentException("Company not found");
        }

        // Create User (new system - no deprecated fields)
        User user = User.create(
            request.getFirstName(),
            request.getLastName(),
            request.getCompanyId()
        );

        User saved = userRepository.save(user);

        // Create Contact entity (new system)
        com.fabricmanagement.common.platform.communication.domain.Contact contact = 
            contactService.createContact(
                request.getContactValue(),
                mapContactType(request.getContactType()),
                "Primary",
                true, // isPersonal
                null  // parentContactId
            );

        // Create UserContact junction (authentication contact)
        userContactService.assignContact(
            saved.getId(),
            contact.getId(),
            true,  // isDefault
            true   // isForAuthentication
        );

        // USER-FRIENDLY: Auto-create UserAddress from Company if available
        // This reduces user errors and provides default work address
        autoCreateUserAddressFromCompany(saved.getId(), request.getCompanyId(), tenantId);

        eventPublisher.publish(new UserCreatedEvent(
            saved.getTenantId(),
            saved.getId(),
            saved.getDisplayName(),
            request.getContactValue(), // Use request value (from Contact entity now)
            saved.getCompanyId()
        ));

        log.info("User created: id={}, uid={}, displayName={}, contactId={}", 
            saved.getId(), saved.getUid(), saved.getDisplayName(), contact.getId());

        return UserDto.from(saved);
    }

    /**
     * USER-FRIENDLY: Auto-create UserAddress from Company's primary address if available.
     * 
     * <p>Benefits:
     * <ul>
     *   <li>Users automatically get work address from their company</li>
     *   <li>Reduces manual data entry</li>
     *   <li>Ensures data consistency</li>
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
                    addressService.findById(companyAddress.getAddressId())
                        .orElseThrow(() -> new IllegalStateException(
                            "Company address found but Address entity not found: " + companyAddress.getAddressId()));
                
                // Create UserAddress from Company Address (WORK address)
                com.fabricmanagement.common.platform.communication.domain.Address userWorkAddress = 
                    addressService.createAddress(
                        companyAddr.getStreetAddress(),
                        companyAddr.getCity(),
                        companyAddr.getState(),
                        companyAddr.getPostalCode(),
                        companyAddr.getCountry(),
                        AddressType.WORK,
                        "Work Address"
                    );
                
                userAddressService.assignAddress(
                    userId,
                    userWorkAddress.getId(),
                    true,  // isPrimary (first address = primary)
                    true   // isWorkAddress
                );
                
                log.info("✅ User work address auto-created from company: userId={}, companyId={}", 
                    userId, companyId);
            } else {
                log.debug("Company has no primary address, skipping user address auto-creation: companyId={}", 
                    companyId);
            }
        } catch (Exception e) {
            log.warn("Failed to auto-create user address from company: userId={}, companyId={}, error={}", 
                userId, companyId, e.getMessage());
            // Continue - address creation is optional
        }
    }

    /**
     * Map User module ContactType to Communication module ContactType.
     */
    private com.fabricmanagement.common.platform.communication.domain.ContactType mapContactType(
            com.fabricmanagement.common.platform.user.domain.ContactType userContactType) {
        return switch (userContactType) {
            case EMAIL -> com.fabricmanagement.common.platform.communication.domain.ContactType.EMAIL;
            case PHONE -> com.fabricmanagement.common.platform.communication.domain.ContactType.PHONE;
        };
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDto> findById(UUID tenantId, UUID userId) {
        log.debug("Finding user: tenantId={}, userId={}", tenantId, userId);

        return userRepository.findByTenantIdAndId(tenantId, userId)
            .map(UserDto::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDto> findByContactValue(String contactValue) {
        log.debug("Finding user by contact: contactValue={}", 
            PiiMaskingUtil.maskEmail(contactValue));

        return userRepository.findByContactValue(contactValue)
            .map(UserDto::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> findByTenant(UUID tenantId) {
        log.debug("Finding users by tenant: tenantId={}", tenantId);

        return userRepository.findByTenantIdAndIsActiveTrue(tenantId)
            .stream()
            .map(UserDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> findByCompany(UUID tenantId, UUID companyId) {
        log.debug("Finding users by company: tenantId={}, companyId={}", tenantId, companyId);

        return userRepository.findByTenantIdAndCompanyIdAndIsActiveTrue(tenantId, companyId)
            .stream()
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

        User user = userRepository.findByTenantIdAndId(tenantId, userId)
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

        User user = userRepository.findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.delete();
        userRepository.save(user);

        eventPublisher.publish(new UserDeactivatedEvent(
            tenantId,
            userId,
            reason
        ));

        log.warn("User deactivated: id={}, uid={}", user.getId(), user.getUid());
    }

    @Transactional(readOnly = true)
    public boolean hasCompletedOnboarding(UUID userId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.trace("Checking onboarding status: tenantId={}, userId={}", tenantId, userId);

        User user = userRepository.findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return user.hasCompletedOnboarding();
    }

    @Transactional
    public UserDto completeOnboarding(UUID userId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Completing onboarding: tenantId={}, userId={}", tenantId, userId);

        User user = userRepository.findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.hasCompletedOnboarding()) {
            log.debug("User already completed onboarding: userId={}", userId);
            return UserDto.from(user);
        }

        user.completeOnboarding();
        User saved = userRepository.save(user);

        eventPublisher.publish(new UserOnboardingCompletedEvent(
            saved.getTenantId(),
            saved.getId()
        ));

        log.info("✅ Onboarding completed: userId={}, uid={}", saved.getId(), saved.getUid());

        return UserDto.from(saved);
    }

    /**
     * Update user profile with permission checks.
     * 
     * <p><b>Security:</b> Self-update is NOT allowed. Only Admin/HR/Dept Manager can update profiles.</p>
     * 
     * @param userId Target user ID
     * @param request Update request with field categories
     * @param requesterId User requesting the update (from TenantContext)
     * @return Updated user DTO
     */
    @Transactional
    public UserDto updateProfile(UUID userId, UpdateUserProfileRequest request, UUID requesterId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Updating user profile: tenantId={}, userId={}, requesterId={}", 
            tenantId, userId, requesterId);

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
                        "You don't have permission to update work profile. " +
                        "Only Admin, HR Manager, or Department Manager (same department) can update work profiles."
                    );
                }
            } else if (category == ProfileCategory.PERSONAL_PROFILE) {
                allowed = permissionService.canUpdatePersonalProfile(requesterId, userId);
                if (!allowed) {
                    throw new org.springframework.security.access.AccessDeniedException(
                        "You don't have permission to update personal profile. " +
                        "Only Admin or HR Manager can update personal profiles."
                    );
                }
            }
        }

        // Self-update prevention (additional check for safety)
        if (userId.equals(requesterId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "Users cannot update their own profile. " +
                "Please contact HR or Admin to update your profile information."
            );
        }

        // Get user
        User user = userRepository.findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Update work profile fields
        if (request.getFirstName() != null || request.getLastName() != null) {
            String firstName = request.getFirstName() != null ? request.getFirstName() : user.getFirstName();
            String lastName = request.getLastName() != null ? request.getLastName() : user.getLastName();
            user.updateProfile(firstName, lastName);
        }

        // Update work email contact
        if (request.getWorkEmail() != null) {
            updateWorkContact(userId, request.getWorkEmail(), ContactType.EMAIL);
            log.debug("Work email updated: userId={}, email={}", userId, 
                PiiMaskingUtil.maskEmail(request.getWorkEmail()));
        }

        // Update work phone contact
        if (request.getWorkPhone() != null) {
            updateWorkContact(userId, request.getWorkPhone(), ContactType.PHONE);
            log.debug("Work phone updated: userId={}, phone={}", userId, 
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
                requesterId
            );
            log.debug("Department updated: userId={}, departmentId={}", userId, request.getDepartmentId());
        }

        // Update personal profile fields
        if (request.getHomeAddress() != null) {
            updateHomeAddress(userId, request.getHomeAddress());
            log.debug("Home address updated: userId={}", userId);
        }

        if (request.getPersonalPhone() != null) {
            updatePersonalContact(userId, request.getPersonalPhone(), ContactType.PHONE);
            log.debug("Personal phone updated: userId={}", userId);
        }

        // Note: birthDate and emergencyContact would require additional entity fields
        // These can be added in a future enhancement

        User saved = userRepository.save(user);

        // Publish event for audit
        eventPublisher.publish(new UserProfileUpdatedEvent(
            saved.getTenantId(),
            saved.getId(),
            requesterId,
            categories
        ));

        log.info("✅ Profile updated: userId={}, updatedBy={}, categories={}", 
            saved.getId(), requesterId, categories);

        return UserDto.from(saved);
    }

    /**
     * Update work contact (email or phone) for user.
     * Creates contact if not exists, assigns to user as work contact.
     */
    private void updateWorkContact(UUID userId, String contactValue, ContactType contactType) {
        // Find or create contact
        Contact contact = contactService.findByValueAndType(contactValue, contactType)
            .orElseGet(() -> {
                String maskedValue = contactType == ContactType.EMAIL 
                    ? PiiMaskingUtil.maskEmail(contactValue)
                    : PiiMaskingUtil.maskPhone(contactValue);
                log.debug("Creating new work contact: type={}, value={}", contactType, maskedValue);
                return contactService.createContact(
                    contactValue,
                    contactType,
                    "Work " + contactType.name().toLowerCase(), // Label
                    false, // isPersonal = false for work contacts
                    null  // No parent contact
                );
            });

        // ✅ Performance: Direct exists check instead of loading all contacts
        if (userContactService.existsUserContact(userId, contact.getId())) {
            log.debug("Contact already assigned to user: userId={}, contactId={}", userId, contact.getId());
            return;
        }

        // Assign contact to user (work contact, not for authentication, can be default)
        userContactService.assignContact(userId, contact.getId(), false, false);
        log.debug("Work contact assigned: userId={}, contactId={}, type={}", userId, contact.getId(), contactType);
    }

    /**
     * Update personal contact (phone) for user.
     * Creates contact if not exists, assigns to user as personal contact.
     */
    private void updatePersonalContact(UUID userId, String contactValue, ContactType contactType) {
        // Find or create contact
        Contact contact = contactService.findByValueAndType(contactValue, contactType)
            .orElseGet(() -> {
                String maskedValue = contactType == ContactType.EMAIL 
                    ? PiiMaskingUtil.maskEmail(contactValue)
                    : PiiMaskingUtil.maskPhone(contactValue);
                log.debug("Creating new personal contact: type={}, value={}", contactType, maskedValue);
                return contactService.createContact(
                    contactValue,
                    contactType,
                    "Personal " + contactType.name().toLowerCase(), // Label
                    true, // isPersonal = true for personal contacts
                    null  // No parent contact
                );
            });

        // ✅ Performance: Direct exists check instead of loading all contacts
        if (userContactService.existsUserContact(userId, contact.getId())) {
            log.debug("Personal contact already assigned to user: userId={}, contactId={}", userId, contact.getId());
            return;
        }

        // Assign contact to user (personal contact, not for authentication, not default)
        userContactService.assignContact(userId, contact.getId(), false, false);
        log.debug("Personal contact assigned: userId={}, contactId={}, type={}", userId, contact.getId(), contactType);
    }

    /**
     * Update work address for user.
     * Creates address if not exists, assigns to user as work address.
     */
    private void updateWorkAddress(UUID userId, UpdateUserProfileRequest.AddressData addressData) {
        // Create address
        Address address = addressService.createAddress(
            addressData.getStreetAddress(),
            addressData.getCity(),
            addressData.getState(),
            addressData.getPostalCode(),
            addressData.getCountry(),
            AddressType.WORK,
            "Work Address"
        );

        // Check if already assigned to user
        if (userAddressService.getUserAddresses(userId).stream()
            .anyMatch(ua -> ua.getAddressId().equals(address.getId()))) {
            log.debug("Work address already assigned to user: userId={}, addressId={}", userId, address.getId());
            return;
        }

        // Assign address to user (work address, not primary)
        userAddressService.assignAddress(userId, address.getId(), false, true); // isWorkAddress = true
        log.debug("Work address assigned: userId={}, addressId={}", userId, address.getId());
    }

    /**
     * Update home address for user.
     * Creates address if not exists, assigns to user as home address.
     */
    private void updateHomeAddress(UUID userId, UpdateUserProfileRequest.AddressData addressData) {
        // Create address
        Address address = addressService.createAddress(
            addressData.getStreetAddress(),
            addressData.getCity(),
            addressData.getState(),
            addressData.getPostalCode(),
            addressData.getCountry(),
            AddressType.HOME,
            "Home Address"
        );

        // Check if already assigned to user
        if (userAddressService.getUserAddresses(userId).stream()
            .anyMatch(ua -> ua.getAddressId().equals(address.getId()))) {
            log.debug("Home address already assigned to user: userId={}, addressId={}", userId, address.getId());
            return;
        }

        // Assign address to user (home address, can be primary if no primary exists)
        Optional<UserAddress> primaryAddress = userAddressService.getPrimaryAddress(userId);
        boolean isPrimary = primaryAddress.isEmpty();
        
        userAddressService.assignAddress(userId, address.getId(), isPrimary, false); // isWorkAddress = false
        log.debug("Home address assigned: userId={}, addressId={}, isPrimary={}", userId, address.getId(), isPrimary);
    }
}

