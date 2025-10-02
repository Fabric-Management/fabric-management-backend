package com.fabricmanagement.user.domain.aggregate;

import com.fabricmanagement.shared.domain.base.BaseEntity;
import com.fabricmanagement.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.user.domain.event.UserUpdatedEvent;
import com.fabricmanagement.user.domain.event.UserDeletedEvent;
import com.fabricmanagement.user.domain.valueobject.RegistrationType;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Type;

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
    
    // NOTE: Contacts are now managed by Contact Service
    // Use ContactServiceClient to retrieve user contacts
    
    @Column(name = "role")
    private String role;                       // User role in company
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    @Column(name = "last_login_ip")
    private String lastLoginIp;
    
    @Type(JsonBinaryType.class)
    @Column(name = "preferences", columnDefinition = "jsonb")
    private Map<String, Object> preferences;
    
    @Type(JsonBinaryType.class)
    @Column(name = "settings", columnDefinition = "jsonb")
    private Map<String, Object> settings;
    
    // Domain events (not persisted)
    @Transient
    private final List<Object> domainEvents = new ArrayList<>();

    /**
     * Creates a new user with business validation
     * 
     * NOTE: Contact information should be created separately using ContactServiceClient
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
            .build();

        // Add domain event
        user.addDomainEvent(UserCreatedEvent.builder()
            .userId(user.getId())
            .tenantId(tenantId.toString())
            .firstName(firstName)
            .lastName(lastName)
            .email(contactValue)
            .status(UserStatus.ACTIVE.name())
            .registrationType(RegistrationType.DIRECT_REGISTRATION.name())
            .timestamp(LocalDateTime.now())
            .build());

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
        addDomainEvent(UserUpdatedEvent.builder()
            .userId(this.getId())
            .tenantId(this.tenantId)
            .firstName(firstName)
            .lastName(lastName)
            .status(this.status.name())
            .timestamp(LocalDateTime.now())
            .build());
    }

    /**
     * Updates user preferences
     */
    public void updatePreferences(Map<String, Object> preferences) {
        this.preferences = preferences;
        
        addDomainEvent(UserUpdatedEvent.builder()
            .userId(this.getId())
            .tenantId(this.tenantId)
            .firstName(this.firstName)
            .lastName(this.lastName)
            .status(this.status.name())
            .timestamp(LocalDateTime.now())
            .build());
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
        
        addDomainEvent(UserDeletedEvent.builder()
            .userId(this.getId())
            .tenantId(this.tenantId)
            .timestamp(LocalDateTime.now())
            .build());
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
     * 
     * NOTE: Contact information should be created separately using ContactServiceClient
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
            .build();

        // Add domain event
        user.addDomainEvent(UserCreatedEvent.builder()
            .userId(user.getId())
            .tenantId("UNKNOWN") // Will be set later
            .firstName(firstName)
            .lastName(lastName)
            .email(contactValue)
            .status(UserStatus.PENDING_VERIFICATION.name())
            .registrationType(RegistrationType.DIRECT_REGISTRATION.name())
            .timestamp(LocalDateTime.now())
            .build());

        return user;
    }

    /**
     * Verifies contact and activates user
     * 
     * NOTE: Contact verification is now handled by Contact Service
     * This method only updates user status after receiving ContactVerifiedEvent
     */
    public void verifyContactAndActivate(String contactValue) {
        if (this.status != UserStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("User must be in PENDING_VERIFICATION status");
        }

        // Activate user
        this.status = UserStatus.ACTIVE;

        // Add domain event
        addDomainEvent(UserUpdatedEvent.builder()
            .userId(this.getId())
            .tenantId(this.tenantId)
            .firstName(this.firstName)
            .lastName(this.lastName)
            .status(UserStatus.ACTIVE.name())
            .timestamp(LocalDateTime.now())
            .build());
    }

    // NOTE: Contact management is now handled by Contact Service
    // Use ContactServiceClient to manage contacts
    
    /**
     * Gets primary contact value
     * 
     * @deprecated Use ContactServiceClient.getPrimaryContact(userId) instead
     * This method will be removed in the next major version.
     * Contact management is now fully handled by Contact Service.
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
    public String getPrimaryContact() {
        // Method kept for backward compatibility only
        // Will be removed in version 2.0.0
        return "unknown";
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
        addDomainEvent(UserUpdatedEvent.builder()
            .userId(this.getId())
            .tenantId(this.tenantId)
            .firstName(this.firstName)
            .lastName(this.lastName)
            .status(this.status.name())
            .timestamp(LocalDateTime.now())
            .build());
    }

    // NOTE: Contact management methods are now handled by Contact Service
    // Use ContactServiceClient for these operations

    /**
     * Gets and clears domain events
     */
    public List<Object> getAndClearDomainEvents() {
        List<Object> events = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return events;
    }
}
