package com.fabricmanagement.common.platform.user.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.company.api.facade.CompanyFacade;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.app.UserContactService;
import com.fabricmanagement.common.platform.user.api.facade.UserFacade;
import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.common.platform.user.domain.event.UserDeactivatedEvent;
import com.fabricmanagement.common.platform.user.dto.CreateUserRequest;
import com.fabricmanagement.common.platform.user.dto.UpdateUserRequest;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
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
    private final ContactService contactService;
    private final UserContactService userContactService;

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
}

