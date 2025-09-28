package com.fabricmanagement.identity.application.service;

import com.fabricmanagement.identity.application.dto.user.*;
import com.fabricmanagement.identity.domain.model.User;
import com.fabricmanagement.identity.domain.model.UserContact;
import com.fabricmanagement.identity.domain.repository.UserRepository;
import com.fabricmanagement.identity.domain.valueobject.ContactId;
import com.fabricmanagement.identity.domain.valueobject.ContactType;
import com.fabricmanagement.identity.domain.valueobject.UserId;
import com.fabricmanagement.identity.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for user profile and contact management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * Gets current user profile.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        UserId userId = UserId.of(userPrincipal.getId());
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return mapToProfileResponse(user);
    }

    /**
     * Updates current user profile.
     */
    public UserProfileResponse updateCurrentUserProfile(UpdateProfileRequest request, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        UserId userId = UserId.of(userPrincipal.getId());
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.updateProfile(request.getFirstName(), request.getLastName(), userPrincipal.getUsername());
        user = userRepository.save(user);

        log.info("User profile updated for user: {}", user.getUsername());
        return mapToProfileResponse(user);
    }

    /**
     * Gets user contacts.
     */
    @Transactional(readOnly = true)
    public List<UserContactResponse> getUserContacts(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        UserId userId = UserId.of(userPrincipal.getId());
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return user.getContacts().stream()
            .map(this::mapToContactResponse)
            .collect(Collectors.toList());
    }

    /**
     * Adds a new contact to user.
     */
    public UserContactResponse addUserContact(AddContactRequest request, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        UserId userId = UserId.of(userPrincipal.getId());
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.addContact(request.getContactType(), request.getContactValue(), userPrincipal.getUsername());
        user = userRepository.save(user);

        // Find the newly added contact
        UserContact newContact = user.getContacts().stream()
            .filter(contact -> contact.getValue().equals(request.getContactValue()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Contact not found after adding"));

        // Initiate verification
        var verificationToken = user.initiateContactVerification(request.getContactValue());
        userRepository.save(user);

        // Send verification notification
        if (request.getContactType() == ContactType.EMAIL) {
            notificationService.sendVerificationEmail(request.getContactValue(), verificationToken);
        } else {
            notificationService.sendVerificationSms(request.getContactValue(), verificationToken);
        }

        log.info("Contact added for user: {} - {}", user.getUsername(), request.getContactValue());
        return mapToContactResponse(newContact);
    }

    /**
     * Removes a contact from user.
     */
    public void removeUserContact(String contactId, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        UserId userId = UserId.of(userPrincipal.getId());
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        ContactId contactIdObj = ContactId.of(contactId);
        user.removeContact(contactIdObj, userPrincipal.getUsername());
        userRepository.save(user);

        log.info("Contact removed for user: {} - {}", user.getUsername(), contactId);
    }

    /**
     * Sets primary contact.
     */
    public void setPrimaryContact(String contactId, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        UserId userId = UserId.of(userPrincipal.getId());
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        ContactId contactIdObj = ContactId.of(contactId);
        user.setPrimaryContact(contactIdObj);
        userRepository.save(user);

        log.info("Primary contact set for user: {} - {}", user.getUsername(), contactId);
    }

    /**
     * Enables two-factor authentication.
     */
    public String enableTwoFactor(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        UserId userId = UserId.of(userPrincipal.getId());
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String secret = user.enableTwoFactor();
        userRepository.save(user);

        log.info("Two-factor authentication enabled for user: {}", user.getUsername());
        return secret;
    }

    /**
     * Disables two-factor authentication.
     */
    public void disableTwoFactor(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        UserId userId = UserId.of(userPrincipal.getId());
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.disableTwoFactor();
        userRepository.save(user);

        log.info("Two-factor authentication disabled for user: {}", user.getUsername());
    }

    /**
     * Maps User domain model to UserProfileResponse.
     */
    private UserProfileResponse mapToProfileResponse(User user) {
        return UserProfileResponse.builder()
            .id(user.getId().getValue().toString())
            .username(user.getUsername())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .email(user.getPrimaryEmail())
            .phone(user.getContacts().stream()
                .filter(contact -> contact.getType() == ContactType.PHONE && contact.isPrimary())
                .map(UserContact::getValue)
                .findFirst()
                .orElse(null))
            .role(user.getRole().name())
            .status(user.getStatus().name())
            .twoFactorEnabled(user.isTwoFactorEnabled())
            .lastLoginAt(user.getLastLoginAt())
            .createdAt(user.getCreatedAt())
            .build();
    }

    /**
     * Maps UserContact domain model to UserContactResponse.
     */
    private UserContactResponse mapToContactResponse(UserContact contact) {
        return UserContactResponse.builder()
            .id(contact.getId().getValue().toString())
            .contactType(contact.getType())
            .contactValue(contact.getValue())
            .status(contact.getStatus())
            .isPrimary(contact.isPrimary())
            .verifiedAt(contact.getVerifiedAt())
            .createdAt(contact.getCreatedAt())
            .build();
    }
}
