package com.fabricmanagement.identity.domain.model;

import com.fabricmanagement.identity.domain.event.*;
import com.fabricmanagement.identity.domain.exception.IdentityDomainException;
import com.fabricmanagement.identity.domain.valueobject.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * User Aggregate Root
 * Manages user identity, credentials, and contact information as a cohesive unit.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private UserId id;
    private UUID tenantId;
    private String username;
    private String firstName;
    private String lastName;
    private UserRole role;
    private UserStatus status;

    // Credentials
    private Credentials credentials;
    private boolean passwordMustChange;

    // Contact Information (part of aggregate)
    @Builder.Default
    private List<UserContact> contacts = new ArrayList<>();
    private ContactId primaryContactId;

    // Verification & Security
    @Builder.Default
    private Map<ContactId, VerificationToken> pendingVerifications = new HashMap<>();
    private int failedLoginAttempts;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;

    // Two-Factor Authentication
    private boolean twoFactorEnabled;
    private String twoFactorSecret;

    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    // Domain Events
    @Builder.Default
    private transient List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * Creates a new user (for admin-controlled creation).
     * User initially has no password.
     */
    public static User create(
        UUID tenantId,
        String username,
        String firstName,
        String lastName,
        UserRole role,
        String createdBy
    ) {
        User user = new User(
            UserId.generate(),
            tenantId,
            username,
            firstName,
            lastName,
            role,
            createdBy
        );

        user.addDomainEvent(new UserCreatedEvent(user.id, tenantId, username));
        return user;
    }

    private User(
        UserId id,
        UUID tenantId,
        String username,
        String firstName,
        String lastName,
        UserRole role,
        String createdBy
    ) {
        validateUsername(username);
        validateName(firstName, lastName);

        this.id = id;
        this.tenantId = tenantId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role != null ? role : UserRole.USER;
        this.status = UserStatus.PENDING_ACTIVATION;
        this.contacts = new ArrayList<>();
        this.pendingVerifications = new HashMap<>();
        this.failedLoginAttempts = 0;
        this.passwordMustChange = false;
        this.twoFactorEnabled = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
        this.domainEvents = new ArrayList<>();
    }

    /**
     * Adds a contact to the user.
     * First contact automatically becomes primary.
     */
    public void addContact(ContactType type, String value, String addedBy) {
        validateContactValue(type, value);

        // Check for duplicate
        if (hasContact(value)) {
            throw new IdentityDomainException("Contact already exists: " + value);
        }

        ContactId contactId = ContactId.generate();
        UserContact contact = UserContact.create(contactId, type, value);

        contacts.add(contact);

        // First contact becomes primary
        if (contacts.size() == 1) {
            primaryContactId = contactId;
            contact.markAsPrimary();
        }

        this.updatedAt = LocalDateTime.now();
        this.updatedBy = addedBy;

        addDomainEvent(new UserContactAddedEvent(id, contactId, type, value));
    }

    /**
     * Initiates contact verification.
     * Generates a verification token for the specified contact.
     */
    public VerificationToken initiateContactVerification(String contactValue) {
        UserContact contact = findContactByValue(contactValue)
            .orElseThrow(() -> new IdentityDomainException("Contact not found: " + contactValue));

        if (contact.isVerified()) {
            throw new IdentityDomainException("Contact already verified: " + contactValue);
        }

        VerificationToken token = VerificationToken.generate(contact.getType());
        pendingVerifications.put(contact.getId(), token);

        this.updatedAt = LocalDateTime.now();

        addDomainEvent(new ContactVerificationInitiatedEvent(id, contact.getId(), contactValue));

        return token;
    }

    /**
     * Verifies a contact with the provided token.
     * If user has no password, allows password creation after first verification.
     */
    public boolean verifyContact(String contactValue, String tokenValue) {
        UserContact contact = findContactByValue(contactValue)
            .orElseThrow(() -> new IdentityDomainException("Contact not found: " + contactValue));

        VerificationToken token = pendingVerifications.get(contact.getId());
        if (token == null) {
            throw new IdentityDomainException("No pending verification for contact: " + contactValue);
        }

        if (!token.isValid(tokenValue)) {
            throw new IdentityDomainException("Invalid or expired verification token");
        }

        // Mark contact as verified
        contact.verify();
        pendingVerifications.remove(contact.getId());

        // Activate user on first verification
        if (status == UserStatus.PENDING_ACTIVATION) {
            status = UserStatus.ACTIVE;
            addDomainEvent(new UserActivatedEvent(id));
        }

        this.updatedAt = LocalDateTime.now();

        addDomainEvent(new ContactVerifiedEvent(id, contact.getId(), contactValue));

        return true;
    }

    /**
     * Creates initial password after contact verification.
     * Only allowed if user has at least one verified contact and no existing password.
     */
    public void createInitialPassword(String password) {
        if (credentials != null && credentials.hasPassword()) {
            throw new IdentityDomainException("Password already exists");
        }

        if (!hasAnyVerifiedContact()) {
            throw new IdentityDomainException("Must verify at least one contact before creating password");
        }

        this.credentials = Credentials.create(password);
        this.passwordMustChange = false;
        this.updatedAt = LocalDateTime.now();

        addDomainEvent(new PasswordCreatedEvent(id));
    }

    /**
     * Authenticates user with any verified contact and password.
     */
    public AuthenticationResult authenticate(String contactValue, String password, String ipAddress) {
        // Check if account is locked
        if (isAccountLocked()) {
            return AuthenticationResult.accountLocked(lockedUntil);
        }

        // Find and verify contact
        UserContact contact = findContactByValue(contactValue)
            .orElseThrow(() -> new IdentityDomainException("Invalid credentials"));

        if (!contact.isVerified()) {
            return AuthenticationResult.contactNotVerified();
        }

        // Check password
        if (credentials == null || !credentials.matches(password)) {
            incrementFailedAttempts();
            return AuthenticationResult.invalidCredentials();
        }

        // Successful authentication
        resetFailedAttempts();
        recordSuccessfulLogin(ipAddress);

        if (passwordMustChange) {
            return AuthenticationResult.passwordChangeRequired();
        }

        return AuthenticationResult.success();
    }

    /**
     * Checks if user can authenticate with the given contact.
     */
    public boolean canAuthenticateWith(String contactValue) {
        return findContactByValue(contactValue)
            .map(UserContact::isVerified)
            .orElse(false);
    }

    /**
     * Gets all verified contacts that can be used for authentication.
     */
    public List<UserContact> getVerifiedContacts() {
        return contacts.stream()
            .filter(UserContact::isVerified)
            .collect(Collectors.toList());
    }

    /**
     * Checks if user has any verified contact.
     */
    public boolean hasAnyVerifiedContact() {
        return contacts.stream().anyMatch(UserContact::isVerified);
    }

    /**
     * Changes user password.
     */
    public void changePassword(String currentPassword, String newPassword) {
        if (credentials == null || !credentials.matches(currentPassword)) {
            throw new IdentityDomainException("Current password is incorrect");
        }

        credentials = credentials.change(newPassword);
        passwordMustChange = false;
        this.updatedAt = LocalDateTime.now();

        addDomainEvent(new PasswordChangedEvent(id));
    }

    /**
     * Resets password (admin action or forgot password).
     */
    public void resetPassword(String newPassword) {
        credentials = Credentials.create(newPassword);
        passwordMustChange = false;
        failedLoginAttempts = 0;
        lockedUntil = null;
        this.updatedAt = LocalDateTime.now();

        addDomainEvent(new PasswordResetEvent(id));
    }

    /**
     * Sets primary contact.
     */
    public void setPrimaryContact(ContactId contactId) {
        UserContact contact = findContactById(contactId)
            .orElseThrow(() -> new IdentityDomainException("Contact not found"));

        if (!contact.isVerified()) {
            throw new IdentityDomainException("Cannot set unverified contact as primary");
        }

        // Remove primary flag from current primary
        contacts.forEach(c -> c.unmarkAsPrimary());

        // Set new primary
        contact.markAsPrimary();
        this.primaryContactId = contactId;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Removes a contact (must not be the only verified contact).
     */
    public void removeContact(ContactId contactId, String removedBy) {
        UserContact contact = findContactById(contactId)
            .orElseThrow(() -> new IdentityDomainException("Contact not found"));

        // Ensure at least one verified contact remains
        if (contact.isVerified() && getVerifiedContacts().size() == 1) {
            throw new IdentityDomainException("Cannot remove the only verified contact");
        }

        // Cannot remove primary contact
        if (contact.isPrimary()) {
            throw new IdentityDomainException("Cannot remove primary contact. Set a different primary first.");
        }

        contacts.remove(contact);
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = removedBy;

        addDomainEvent(new UserContactRemovedEvent(id, contactId));
    }

    /**
     * Enables two-factor authentication.
     */
    public String enableTwoFactor() {
        if (twoFactorEnabled) {
            throw new IdentityDomainException("Two-factor authentication already enabled");
        }

        this.twoFactorSecret = TwoFactorSecret.generate();
        this.twoFactorEnabled = true;
        this.updatedAt = LocalDateTime.now();

        addDomainEvent(new TwoFactorEnabledEvent(id));

        return twoFactorSecret;
    }

    /**
     * Disables two-factor authentication.
     */
    public void disableTwoFactor() {
        this.twoFactorEnabled = false;
        this.twoFactorSecret = null;
        this.updatedAt = LocalDateTime.now();

        addDomainEvent(new TwoFactorDisabledEvent(id));
    }

    /**
     * Updates user profile.
     */
    public void updateProfile(String firstName, String lastName, String updatedBy) {
        validateName(firstName, lastName);

        this.firstName = firstName;
        this.lastName = lastName;
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = updatedBy;

        addDomainEvent(new UserProfileUpdatedEvent(id));
    }

    /**
     * Changes user role (admin action).
     */
    public void changeRole(UserRole newRole, String changedBy) {
        if (this.role == newRole) {
            return;
        }

        UserRole oldRole = this.role;
        this.role = newRole;
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = changedBy;

        addDomainEvent(new UserRoleChangedEvent(id, oldRole, newRole));
    }

    /**
     * Suspends the user account.
     */
    public void suspend(String reason, String suspendedBy) {
        if (status == UserStatus.SUSPENDED) {
            throw new IdentityDomainException("User already suspended");
        }

        this.status = UserStatus.SUSPENDED;
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = suspendedBy;

        addDomainEvent(new UserSuspendedEvent(id, reason));
    }

    /**
     * Reactivates a suspended user account.
     */
    public void reactivate(String reactivatedBy) {
        if (status != UserStatus.SUSPENDED) {
            throw new IdentityDomainException("User is not suspended");
        }

        this.status = UserStatus.ACTIVE;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = reactivatedBy;

        addDomainEvent(new UserReactivatedEvent(id));
    }

    // Helper methods

    private Optional<UserContact> findContactByValue(String value) {
        return contacts.stream()
            .filter(c -> c.getValue().equalsIgnoreCase(value))
            .findFirst();
    }

    private Optional<UserContact> findContactById(ContactId id) {
        return contacts.stream()
            .filter(c -> c.getId().equals(id))
            .findFirst();
    }

    private boolean hasContact(String value) {
        return contacts.stream()
            .anyMatch(c -> c.getValue().equalsIgnoreCase(value));
    }

    private void incrementFailedAttempts() {
        failedLoginAttempts++;
        if (failedLoginAttempts >= 5) {
            lockedUntil = LocalDateTime.now().plusMinutes(30);
        }
        this.updatedAt = LocalDateTime.now();
    }

    private void resetFailedAttempts() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    private void recordSuccessfulLogin(String ipAddress) {
        lastLoginAt = LocalDateTime.now();
        lastLoginIp = ipAddress;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isAccountLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public String getFullName() {
        return String.format("%s %s", firstName, lastName).trim();
    }

    public String getPrimaryEmail() {
        if (primaryContactId == null) {
            return null;
        }
        return findContactById(primaryContactId)
            .filter(c -> c.getType() == ContactType.EMAIL)
            .map(UserContact::getValue)
            .orElse(null);
    }

    // Validation methods

    private void validateUsername(String username) {
        if (username == null || username.trim().length() < 3) {
            throw new IdentityDomainException("Username must be at least 3 characters");
        }
    }

    private void validateName(String firstName, String lastName) {
        if (firstName == null || firstName.trim().isEmpty()) {
            throw new IdentityDomainException("First name is required");
        }
        if (lastName == null || lastName.trim().isEmpty()) {
            throw new IdentityDomainException("Last name is required");
        }
    }

    private void validateContactValue(ContactType type, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IdentityDomainException("Contact value is required");
        }

        if (type == ContactType.EMAIL && !EmailValidator.isValid(value)) {
            throw new IdentityDomainException("Invalid email address: " + value);
        }

        if (type == ContactType.PHONE && !PhoneValidator.isValid(value)) {
            throw new IdentityDomainException("Invalid phone number: " + value);
        }
    }

    // Domain events

    private void addDomainEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }
}