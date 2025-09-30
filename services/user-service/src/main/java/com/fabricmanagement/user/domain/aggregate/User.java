package com.fabricmanagement.user.domain.aggregate;

import com.fabricmanagement.shared.domain.base.BaseEntity;
import com.fabricmanagement.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.user.domain.event.UserUpdatedEvent;
import com.fabricmanagement.user.domain.event.UserDeletedEvent;
import com.fabricmanagement.user.domain.valueobject.UserContact;
import com.fabricmanagement.user.domain.valueobject.RegistrationType;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import com.fabricmanagement.user.domain.valueobject.PasswordResetToken;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * User Aggregate Root
 * 
 * Represents a user in the system with all business rules and invariants.
 * Follows Domain-Driven Design principles with event sourcing.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class User extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;
    
    @Column(name = "first_name", nullable = false)
    private String firstName;
    
    @Column(name = "last_name", nullable = false)
    private String lastName;
    
    @Column(name = "display_name")
    private String displayName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "registration_type", nullable = false)
    private RegistrationType registrationType;
    
    @Column(name = "invitation_token")
    private String invitationToken;           // For invitation-based registration
    
    @Column(name = "password_hash")
    private String passwordHash;               // Encrypted password
    
    @OneToMany(mappedBy = "userId", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserContact> contacts;        // Multiple contact methods
    
    @Column(name = "role")
    private String role;                       // User role in company
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    @Column(name = "last_login_ip")
    private String lastLoginIp;
    
    @ElementCollection
    @CollectionTable(name = "user_preferences", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "preference_key")
    @Column(name = "preference_value")
    private Map<String, Object> preferences;
    
    @ElementCollection
    @CollectionTable(name = "user_settings", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "setting_key")
    @Column(name = "setting_value")
    private Map<String, Object> settings;
    
    // Domain events (not persisted)
    @Transient
    private final List<Object> domainEvents = new ArrayList<>();

    /**
     * Creates a new user with business validation (Legacy method - use createWithContactVerification instead)
     */
    public static User create(UUID tenantId, String contactValue, String contactType,
                            String firstName, String lastName) {
        
        // Business validation
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
        if (contactValue == null || contactValue.trim().isEmpty()) {
            throw new IllegalArgumentException("Contact value cannot be null or empty");
        }

        User user = User.builder()
            .tenantId(tenantId.toString())
            .firstName(firstName)
            .lastName(lastName)
            .displayName(firstName + " " + lastName)
            .status(UserStatus.ACTIVE)
            .contacts(new ArrayList<>())
            .build();

        // Add contact
        UserContact.ContactType type = UserContact.ContactType.valueOf(contactType.toUpperCase());
        UserContact contact = UserContact.builder()
            .userId(user.getId().toString())
            .contactValue(contactValue)
            .contactType(type)
            .isVerified(true)
            .isPrimary(true)
            .verifiedAt(LocalDateTime.now())
            .build();
        user.contacts.add(contact);

        // Add domain event
        user.addDomainEvent(new UserCreatedEvent(
            user.getId(),
            contactValue,
            firstName,
            lastName
        ));

        return user;
    }

    /**
     * Updates user profile information
     */
    public void updateProfile(String firstName, String lastName, String displayName) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.displayName = displayName != null ? displayName : firstName + " " + lastName;

        // Add domain event
        addDomainEvent(new UserUpdatedEvent(
            this.getId(),
            this.getPrimaryContact(),
            firstName,
            lastName,
            this.displayName
        ));
    }

    /**
     * Updates user preferences
     */
    public void updatePreferences(Map<String, Object> preferences) {
        this.preferences = preferences;
        
        addDomainEvent(new UserUpdatedEvent(
            this.getId(),
            this.getPrimaryContact(),
            this.firstName,
            this.lastName,
            this.displayName
        ));
    }

    /**
     * Records successful login
     */
    public void recordLogin(String ipAddress) {
        this.lastLoginAt = LocalDateTime.now();
        this.lastLoginIp = ipAddress;
    }

    /**
     * Activates the user account
     */
    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    /**
     * Deactivates the user account
     */
    public void deactivate() {
        this.status = UserStatus.INACTIVE;
    }

    /**
     * Soft deletes the user
     */
    @Override
    public void markAsDeleted() {
        super.markAsDeleted();
        this.status = UserStatus.DELETED;
        
        addDomainEvent(new UserDeletedEvent(
            this.getId(),
            this.getPrimaryContact(),
            this.getPrimaryContact()
        ));
    }

    /**
     * Checks if user is active
     */
    public boolean isActive() {
        return UserStatus.ACTIVE.equals(this.status) && !isDeleted();
    }

    /**
     * Gets full name
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /**
     * Adds domain event
     */
    private void addDomainEvent(Object event) {
        this.domainEvents.add(event);
    }

    /**
     * Creates a new user with contact verification
     */
    public static User createWithContactVerification(String contactValue, String contactType,
                                                    String firstName, String lastName, 
                                                    String passwordHash, String userType) {
        
        // Business validation
        if (contactValue == null || contactValue.trim().isEmpty()) {
            throw new IllegalArgumentException("Contact value cannot be null or empty");
        }
        if (firstName == null || firstName.trim().isEmpty()) {
            throw new IllegalArgumentException("First name cannot be null or empty");
        }
        if (lastName == null || lastName.trim().isEmpty()) {
            throw new IllegalArgumentException("Last name cannot be null or empty");
        }
        if (passwordHash == null || passwordHash.trim().isEmpty()) {
            throw new IllegalArgumentException("Password hash cannot be null or empty");
        }

        User user = User.builder()
            .firstName(firstName)
            .lastName(lastName)
            .displayName(firstName + " " + lastName)
            .passwordHash(passwordHash)
            .status(UserStatus.PENDING_VERIFICATION)
            .registrationType(RegistrationType.DIRECT_REGISTRATION)
            .contacts(new ArrayList<>())
            .build();

        // Add contact
        UserContact.ContactType type = UserContact.ContactType.valueOf(contactType.toUpperCase());
        UserContact contact = UserContact.builder()
            .userId(user.getId().toString())
            .contactValue(contactValue)
            .contactType(type)
            .isVerified(false)
            .isPrimary(true)
            .build();
        user.contacts.add(contact);

        // Add domain event
        user.addDomainEvent(new UserCreatedEvent(
            user.getId(),
            contactValue,
            firstName,
            lastName
        ));

        return user;
    }

    /**
     * Verifies contact and activates user
     */
    public void verifyContactAndActivate(String contactValue) {
        if (this.status != UserStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("User must be in PENDING_VERIFICATION status");
        }

        // Find and verify the contact
        boolean contactFound = false;
        for (int i = 0; i < this.contacts.size(); i++) {
            UserContact contact = this.contacts.get(i);
            if (contact.getContactValue().equals(contactValue)) {
                // Mark contact as verified
                UserContact verifiedContact = UserContact.builder()
                    .id(contact.getId())
                    .userId(contact.getUserId())
                    .contactValue(contact.getContactValue())
                    .contactType(contact.getContactType())
                    .isVerified(true)
                    .isPrimary(contact.isPrimary())
                    .verifiedAt(LocalDateTime.now())
                    .build();
                this.contacts.set(i, verifiedContact);
                contactFound = true;
                break;
            }
        }
        
        if (!contactFound) {
            throw new IllegalArgumentException("Contact not found");
        }

        // Activate user
        this.status = UserStatus.ACTIVE;

        // Add domain event
        addDomainEvent(new UserUpdatedEvent(
            this.getId(),
            contactValue,
            contactValue,
            this.firstName,
            this.lastName
        ));
    }

    /**
     * Adds new contact method
     */
    public void addContact(String contactValue, UserContact.ContactType contactType) {
        if (this.status != UserStatus.ACTIVE) {
            throw new IllegalStateException("User must be active to add contacts");
        }

        // Check if contact already exists
        boolean exists = this.contacts.stream()
            .anyMatch(contact -> contact.getContactValue().equals(contactValue));
        
        if (exists) {
            throw new IllegalArgumentException("Contact already exists");
        }

        UserContact newContact = UserContact.builder()
            .userId(this.getId().toString())
            .contactValue(contactValue)
            .contactType(contactType)
            .isVerified(false)
            .isPrimary(false)
            .build();
        this.contacts.add(newContact);

        // Add domain event
        addDomainEvent(new UserUpdatedEvent(
            this.getId(),
            contactValue,
            contactValue,
            this.firstName,
            this.lastName
        ));
    }

    /**
     * Verifies additional contact method
     */
    public void verifyContact(String contactValue) {
        for (int i = 0; i < this.contacts.size(); i++) {
            UserContact contact = this.contacts.get(i);
            if (contact.getContactValue().equals(contactValue)) {
                UserContact verifiedContact = UserContact.builder()
                    .id(contact.getId())
                    .userId(contact.getUserId())
                    .contactValue(contact.getContactValue())
                    .contactType(contact.getContactType())
                    .isVerified(true)
                    .isPrimary(contact.isPrimary())
                    .verifiedAt(LocalDateTime.now())
                    .build();
                this.contacts.set(i, verifiedContact);
                return;
            }
        }
        throw new IllegalArgumentException("Contact not found");
    }

    /**
     * Gets primary contact value
     */
    public String getPrimaryContact() {
        return this.contacts.stream()
            .filter(UserContact::isPrimary)
            .map(UserContact::getContactValue)
            .findFirst()
            .orElse("unknown");
    }

    /**
     * Resets password with new password hash
     * All previous passwords are invalidated
     */
    public void resetPassword(String newPasswordHash) {
        if (this.status != UserStatus.ACTIVE) {
            throw new IllegalStateException("User must be active to reset password");
        }
        
        // Invalidate all previous passwords (security requirement)
        this.passwordHash = newPasswordHash;
        
        // Add domain event
        addDomainEvent(new UserUpdatedEvent(
            this.getId(),
            this.getPrimaryContact(),
            this.firstName,
            this.lastName,
            this.displayName
        ));
    }

    /**
     * Checks if user has contact method
     */
    public boolean hasContact(String contactValue) {
        return this.contacts.stream()
            .anyMatch(contact -> contact.getContactValue().equals(contactValue));
    }

    /**
     * Gets verified contacts only
     */
    public List<UserContact> getVerifiedContacts() {
        return this.contacts.stream()
            .filter(UserContact::isVerified)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Gets and clears domain events
     */
    public List<Object> getAndClearDomainEvents() {
        List<Object> events = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return events;
    }
}
