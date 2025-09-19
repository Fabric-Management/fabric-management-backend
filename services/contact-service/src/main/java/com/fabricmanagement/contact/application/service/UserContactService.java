package com.fabricmanagement.contact.application.service;

import com.fabricmanagement.common.core.application.dto.ApiResponse;
import com.fabricmanagement.common.core.application.dto.PageRequest;
import com.fabricmanagement.common.core.application.dto.PageResponse;
import com.fabricmanagement.contact.application.dto.contact.request.CreateContactRequest;
import com.fabricmanagement.contact.application.dto.contact.request.UpdateContactRequest;
import com.fabricmanagement.contact.application.dto.contact.response.ContactDetailResponse;
import com.fabricmanagement.contact.application.dto.contact.response.ContactResponse;
import com.fabricmanagement.contact.domain.exception.ContactNotFoundException;
import com.fabricmanagement.contact.domain.valueobject.ContactStatus;
import com.fabricmanagement.contact.domain.valueobject.ContactType;
import com.fabricmanagement.contact.infrastructure.integration.user.UserDto;
import com.fabricmanagement.contact.infrastructure.integration.user.UserServiceClient;
import com.fabricmanagement.contact.infrastructure.persistence.entity.UserContactEntity;
import com.fabricmanagement.contact.infrastructure.persistence.repository.ContactJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing user contacts.
 * Handles all contact operations specific to users.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class UserContactService {

    private final ContactJpaRepository contactRepository;
    private final UserServiceClient userServiceClient;
    private final ContactEmailService emailService;
    private final ContactPhoneService phoneService;
    private final ContactAddressService addressService;

    /**
     * Creates a new contact for a user.
     */
    public ContactDetailResponse createUserContact(UUID userId, CreateContactRequest request) {
        log.debug("Creating contact for user: {}", userId);

        // Verify user exists
        UserDto user = userServiceClient.getUserById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Check if contact already exists for this user
        if (contactRepository.existsByUserId(userId)) {
            throw new IllegalStateException("Contact already exists for user: " + userId);
        }

        // Create user contact entity
        UserContactEntity contact = UserContactEntity.createForUser(userId, user.getTenantId());
        contact.setContactType(ContactType.EMPLOYEE);
        contact.setStatus(ContactStatus.ACTIVE);

        // Set additional user-specific fields
        if (request.getJobTitle() != null) {
            contact.setJobTitle(request.getJobTitle());
        }
        if (request.getDepartment() != null) {
            contact.setDepartment(request.getDepartment());
        }
        if (request.getTimeZone() != null) {
            contact.setTimeZone(request.getTimeZone());
        }
        if (request.getLanguagePreference() != null) {
            contact.setLanguagePreference(request.getLanguagePreference());
        }

        // Save contact
        contact = contactRepository.save(contact);

        // Add emails if provided
        if (request.getEmails() != null && !request.getEmails().isEmpty()) {
            emailService.addEmailsToContact(contact.getId(), request.getEmails());
        }

        // Add phones if provided
        if (request.getPhones() != null && !request.getPhones().isEmpty()) {
            phoneService.addPhonesToContact(contact.getId(), request.getPhones());
        }

        // Add addresses if provided
        if (request.getAddresses() != null && !request.getAddresses().isEmpty()) {
            addressService.addAddressesToContact(contact.getId(), request.getAddresses());
        }

        log.info("Contact created successfully for user: {}", userId);
        return toDetailResponse(contact);
    }

    /**
     * Gets a user's contact by user ID.
     */
    @Transactional(readOnly = true)
    public ContactDetailResponse getUserContact(UUID userId) {
        log.debug("Fetching contact for user: {}", userId);

        UserContactEntity contact = contactRepository.findByUserId(userId)
            .orElseThrow(() -> new ContactNotFoundException("Contact not found for user: " + userId));

        return toDetailResponse(contact);
    }

    /**
     * Updates a user's contact.
     */
    public ContactDetailResponse updateUserContact(UUID userId, UpdateContactRequest request) {
        log.debug("Updating contact for user: {}", userId);

        UserContactEntity contact = contactRepository.findByUserId(userId)
            .orElseThrow(() -> new ContactNotFoundException("Contact not found for user: " + userId));

        // Update user-specific fields
        if (request.getJobTitle() != null) {
            contact.setJobTitle(request.getJobTitle());
        }
        if (request.getDepartment() != null) {
            contact.setDepartment(request.getDepartment());
        }
        if (request.getTimeZone() != null) {
            contact.setTimeZone(request.getTimeZone());
        }
        if (request.getLanguagePreference() != null) {
            contact.setLanguagePreference(request.getLanguagePreference());
        }
        if (request.getNotes() != null) {
            contact.setNotes(request.getNotes());
        }

        contact = contactRepository.save(contact);

        log.info("Contact updated successfully for user: {}", userId);
        return toDetailResponse(contact);
    }

    /**
     * Deletes a user's contact (soft delete).
     */
    public void deleteUserContact(UUID userId) {
        log.debug("Deleting contact for user: {}", userId);

        UserContactEntity contact = contactRepository.findByUserId(userId)
            .orElseThrow(() -> new ContactNotFoundException("Contact not found for user: " + userId));

        contact.markAsDeleted();
        contactRepository.save(contact);

        log.info("Contact deleted successfully for user: {}", userId);
    }

    /**
     * Gets all user contacts for a tenant with pagination.
     */
    @Transactional(readOnly = true)
    public PageResponse<ContactResponse> getUserContactsByTenant(UUID tenantId, PageRequest pageRequest) {
        log.debug("Fetching user contacts for tenant: {}", tenantId);

        Pageable pageable = pageRequest.toPageable();
        Page<UserContactEntity> contacts = contactRepository.findByTenantIdAndEntityType(tenantId, "USER", pageable);

        List<ContactResponse> content = contacts.getContent().stream()
            .map(this::toResponse)
            .collect(java.util.stream.Collectors.toList());
        
        return PageResponse.<ContactResponse>builder()
            .content(content)
            .page(contacts.getNumber())
            .size(contacts.getSize())
            .totalElements(contacts.getTotalElements())
            .totalPages(contacts.getTotalPages())
            .first(contacts.isFirst())
            .last(contacts.isLast())
            .build();
    }

    /**
     * Searches user contacts.
     */
    @Transactional(readOnly = true)
    public PageResponse<ContactResponse> searchUserContacts(String query, PageRequest pageRequest) {
        log.debug("Searching user contacts with query: {}", query);

        Pageable pageable = pageRequest.toPageable();
        Page<UserContactEntity> contacts = contactRepository.searchUserContacts(query, pageable);

        List<ContactResponse> content = contacts.getContent().stream()
            .map(this::toResponse)
            .collect(java.util.stream.Collectors.toList());
        
        return PageResponse.<ContactResponse>builder()
            .content(content)
            .page(contacts.getNumber())
            .size(contacts.getSize())
            .totalElements(contacts.getTotalElements())
            .totalPages(contacts.getTotalPages())
            .first(contacts.isFirst())
            .last(contacts.isLast())
            .build();
    }

    /**
     * Updates emergency contact information for a user.
     */
    public ContactDetailResponse updateEmergencyContact(UUID userId, String name, String phone, String relationship) {
        log.debug("Updating emergency contact for user: {}", userId);

        UserContactEntity contact = contactRepository.findByUserId(userId)
            .orElseThrow(() -> new ContactNotFoundException("Contact not found for user: " + userId));

        contact.setEmergencyContactName(name);
        contact.setEmergencyContactPhone(phone);
        contact.setEmergencyContactRelationship(relationship);

        contact = contactRepository.save(contact);

        log.info("Emergency contact updated for user: {}", userId);
        return toDetailResponse(contact);
    }

    /**
     * Converts entity to detail response.
     */
    private ContactDetailResponse toDetailResponse(UserContactEntity entity) {
        // Implementation would use mapper
        ContactDetailResponse response = new ContactDetailResponse();
        response.setId(entity.getId());
        response.setTenantId(entity.getTenantId());
        response.setContactType(entity.getContactType());
        response.setStatus(entity.getStatus());
        response.setFirstName(entity.getFirstName());
        response.setLastName(entity.getLastName());
        response.setDisplayName(entity.getDisplayName());
        response.setNotes(entity.getNotes());

        // Add user-specific fields
        response.setUserId(entity.getUserId());
        response.setJobTitle(entity.getJobTitle());
        response.setDepartment(entity.getDepartment());
        response.setTimeZone(entity.getTimeZone());
        response.setLanguagePreference(entity.getLanguagePreference());
        response.setPreferredContactMethod(entity.getPreferredContactMethod());
        response.setLinkedinUrl(entity.getLinkedinUrl());
        response.setTwitterHandle(entity.getTwitterHandle());
        response.setEmergencyContactName(entity.getEmergencyContactName());
        response.setEmergencyContactPhone(entity.getEmergencyContactPhone());
        response.setEmergencyContactRelationship(entity.getEmergencyContactRelationship());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        response.setIsActive(entity.getStatus() != null && "ACTIVE".equals(entity.getStatus().name()));

        // Load related data
        response.setEmails(emailService.getContactEmails(entity.getId()));
        response.setPhones(phoneService.getContactPhones(entity.getId()));
        response.setAddresses(addressService.getContactAddresses(entity.getId()));

        return response;
    }

    /**
     * Converts entity to response.
     */
    private ContactResponse toResponse(UserContactEntity entity) {
        ContactResponse response = new ContactResponse();
        response.setId(entity.getId());
        response.setTenantId(entity.getTenantId());
        response.setContactType(entity.getContactType());
        response.setStatus(entity.getStatus());
        response.setFirstName(entity.getFirstName());
        response.setLastName(entity.getLastName());
        response.setDisplayName(entity.getDisplayName());
        response.setUserId(entity.getUserId());
        response.setJobTitle(entity.getJobTitle());
        response.setDepartment(entity.getDepartment());
        response.setTimeZone(entity.getTimeZone());
        response.setLanguagePreference(entity.getLanguagePreference());
        response.setPreferredContactMethod(entity.getPreferredContactMethod());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        response.setIsActive(entity.getStatus() != null && "ACTIVE".equals(entity.getStatus().name()));

        // Add primary contact info
        if (entity.getPrimaryEmail() != null) {
            response.setPrimaryEmail(entity.getPrimaryEmail().getEmail());
        }
        if (entity.getPrimaryPhone() != null) {
            response.setPrimaryPhone(entity.getPrimaryPhone().getFullPhoneNumber());
        }
        if (entity.getPrimaryAddress() != null) {
            response.setPrimaryAddress(entity.getPrimaryAddress().getFullAddress());
        }

        return response;
    }
}