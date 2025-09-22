package com.fabricmanagement.contact.application.service;

import com.fabricmanagement.common.core.application.dto.PageResponse;
import com.fabricmanagement.common.security.context.SecurityContextUtil;
import com.fabricmanagement.contact.application.dto.common.PageRequestDto;
import com.fabricmanagement.contact.application.dto.usercontact.request.CreateUserContactRequest;
import com.fabricmanagement.contact.application.dto.usercontact.request.UpdateUserContactRequest;
import com.fabricmanagement.contact.application.dto.usercontact.response.UserContactResponse;
import com.fabricmanagement.contact.application.dto.usercontact.response.UserContactListResponse;
import com.fabricmanagement.contact.application.mapper.UserContactMapper;
import com.fabricmanagement.contact.domain.model.UserContact;
import com.fabricmanagement.contact.domain.repository.UserContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service for user contact operations.
 * Handles business logic and coordinates between domain and infrastructure layers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserContactService {

    private final UserContactRepository userContactRepository;
    private final UserContactMapper userContactMapper;

    /**
     * Creates a new user contact.
     */
    public UserContactResponse createUserContact(CreateUserContactRequest request) {
        log.info("Creating user contact for user: {}", request.userId());

        // Check if contact already exists for this user
        if (userContactRepository.existsByUserId(request.userId())) {
            throw new IllegalArgumentException("Contact already exists for user: " + request.userId());
        }

        // Convert request to domain object
        UserContact userContact = userContactMapper.toDomain(request);
        userContact.setTenantId(getCurrentTenantId());

        // Save and return response
        UserContact saved = userContactRepository.save(userContact);
        log.info("Successfully created user contact with ID: {} for user: {}", saved.getId(), request.userId());

        return userContactMapper.toResponse(saved);
    }

    /**
     * Gets a user contact by user ID.
     */
    @Transactional(readOnly = true)
    public UserContactResponse getUserContact(UUID userId) {
        log.info("Fetching user contact for user: {}", userId);

        UserContact userContact = userContactRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User contact not found for user: " + userId));

        // Verify tenant access
        validateTenantAccess(userContact.getTenantId());

        return userContactMapper.toResponse(userContact);
    }

    /**
     * Gets a user contact by contact ID.
     */
    @Transactional(readOnly = true)
    public UserContactResponse getUserContactById(UUID contactId) {
        log.info("Fetching user contact by ID: {}", contactId);

        UserContact userContact = userContactRepository.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("User contact not found: " + contactId));

        // Verify tenant access
        validateTenantAccess(userContact.getTenantId());

        return userContactMapper.toResponse(userContact);
    }

    /**
     * Updates a user contact.
     */
    public UserContactResponse updateUserContact(UUID userId, UpdateUserContactRequest request) {
        log.info("Updating user contact for user: {}", userId);

        UserContact userContact = userContactRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User contact not found for user: " + userId));

        // Verify tenant access
        validateTenantAccess(userContact.getTenantId());

        // Update domain object
        userContactMapper.updateDomainFromRequest(request, userContact);

        // Save and return response
        UserContact updated = userContactRepository.save(userContact);
        log.info("Successfully updated user contact for user: {}", userId);

        return userContactMapper.toResponse(updated);
    }

    /**
     * Deletes a user contact.
     */
    public void deleteUserContact(UUID userId) {
        log.info("Deleting user contact for user: {}", userId);

        UserContact userContact = userContactRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User contact not found for user: " + userId));

        // Verify tenant access
        validateTenantAccess(userContact.getTenantId());

        userContact.markAsDeleted();
        userContactRepository.save(userContact);

        log.info("Successfully deleted user contact for user: {}", userId);
    }

    /**
     * Gets all user contacts for the current tenant with pagination.
     */
    @Transactional(readOnly = true)
    public PageResponse<UserContactListResponse> getUserContactsByTenant(UUID tenantId, PageRequestDto pageRequest) {
        UUID effectiveTenantId = tenantId != null ? tenantId : getCurrentTenantId();
        log.info("Fetching user contacts for tenant: {} with pagination", effectiveTenantId);

        List<UserContact> userContacts = userContactRepository.findByTenantId(effectiveTenantId);

        // Convert to list responses
        List<UserContactListResponse> responses = userContacts.stream()
                .map(userContactMapper::toListResponse)
                .collect(Collectors.toList());

        // Apply pagination manually (for simplicity - in production, use database pagination)
        int start = pageRequest.getPage() * pageRequest.getSize();
        int end = Math.min(start + pageRequest.getSize(), responses.size());
        List<UserContactListResponse> paginatedResponses = responses.subList(start, end);

        return PageResponse.<UserContactListResponse>builder()
                .content(paginatedResponses)
                .totalElements((long) responses.size())
                .totalPages((int) Math.ceil((double) responses.size() / pageRequest.getSize()))
                .page(pageRequest.getPage())
                .size(pageRequest.getSize())
                .build();
    }

    /**
     * Searches user contacts by query string.
     */
    @Transactional(readOnly = true)
    public PageResponse<UserContactListResponse> searchUserContacts(String query, PageRequestDto pageRequest) {
        UUID tenantId = getCurrentTenantId();
        log.info("Searching user contacts with query: {} for tenant: {}", query, tenantId);

        List<UserContact> userContacts = userContactRepository.searchByQuery(query, tenantId);

        // Convert to list responses
        List<UserContactListResponse> responses = userContacts.stream()
                .map(userContactMapper::toListResponse)
                .collect(Collectors.toList());

        // Apply pagination manually
        int start = pageRequest.getPage() * pageRequest.getSize();
        int end = Math.min(start + pageRequest.getSize(), responses.size());
        List<UserContactListResponse> paginatedResponses = responses.subList(start, end);

        return PageResponse.<UserContactListResponse>builder()
                .content(paginatedResponses)
                .totalElements((long) responses.size())
                .totalPages((int) Math.ceil((double) responses.size() / pageRequest.getSize()))
                .page(pageRequest.getPage())
                .size(pageRequest.getSize())
                .build();
    }

    /**
     * Gets user contacts by preferred contact method.
     */
    @Transactional(readOnly = true)
    public List<UserContactResponse> getUserContactsByPreferredMethod(String method) {
        UUID tenantId = getCurrentTenantId();
        log.info("Fetching user contacts with preferred method: {} for tenant: {}", method, tenantId);

        List<UserContact> userContacts = userContactRepository.findByPreferredContactMethod(method, tenantId);

        return userContacts.stream()
                .map(userContactMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Gets public user profiles.
     */
    @Transactional(readOnly = true)
    public List<UserContactListResponse> getPublicUserProfiles() {
        UUID tenantId = getCurrentTenantId();
        log.info("Fetching public user profiles for tenant: {}", tenantId);

        List<UserContact> userContacts = userContactRepository.findPublicProfiles(tenantId);

        return userContacts.stream()
                .map(userContactMapper::toListResponse)
                .collect(Collectors.toList());
    }

    /**
     * Gets user contacts that allow direct messages.
     */
    @Transactional(readOnly = true)
    public List<UserContactListResponse> getUserContactsAllowingDirectMessages() {
        UUID tenantId = getCurrentTenantId();
        log.info("Fetching user contacts allowing direct messages for tenant: {}", tenantId);

        List<UserContact> userContacts = userContactRepository.findAllowingDirectMessages(tenantId);

        return userContacts.stream()
                .map(userContactMapper::toListResponse)
                .collect(Collectors.toList());
    }

    /**
     * Updates user contact privacy settings.
     */
    public UserContactResponse updatePrivacySettings(UUID userId, boolean publicProfile, boolean allowDirectMessages, boolean allowNotifications) {
        log.info("Updating privacy settings for user: {}", userId);

        UserContact userContact = userContactRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User contact not found for user: " + userId));

        // Verify tenant access
        validateTenantAccess(userContact.getTenantId());

        // Update privacy settings
        userContact.updatePrivacySettings(publicProfile, allowDirectMessages, allowNotifications);

        // Save and return response
        UserContact updated = userContactRepository.save(userContact);
        log.info("Successfully updated privacy settings for user: {}", userId);

        return userContactMapper.toResponse(updated);
    }

    /**
     * Updates user contact emergency contact information.
     */
    public UserContactResponse updateEmergencyContact(UUID userId, String name, String phone, String relation) {
        log.info("Updating emergency contact for user: {}", userId);

        UserContact userContact = userContactRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User contact not found for user: " + userId));

        // Verify tenant access
        validateTenantAccess(userContact.getTenantId());

        // Update emergency contact
        userContact.updateEmergencyContact(name, phone, relation);

        // Save and return response
        UserContact updated = userContactRepository.save(userContact);
        log.info("Successfully updated emergency contact for user: {}", userId);

        return userContactMapper.toResponse(updated);
    }

    /**
     * Gets the current tenant ID from security context.
     */
    private UUID getCurrentTenantId() {
        return SecurityContextUtil.getCurrentTenantId();
    }

    /**
     * Validates that the current user has access to the specified tenant.
     */
    private void validateTenantAccess(UUID tenantId) {
        UUID currentTenantId = getCurrentTenantId();
        if (!currentTenantId.equals(tenantId)) {
            throw new IllegalArgumentException("Access denied to tenant: " + tenantId);
        }
    }
}