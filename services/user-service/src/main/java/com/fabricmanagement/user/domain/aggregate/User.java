package com.fabricmanagement.user.domain.aggregate;

import com.fabricmanagement.shared.domain.base.BaseEntity;
import com.fabricmanagement.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.user.domain.event.UserUpdatedEvent;
import com.fabricmanagement.user.domain.event.UserDeletedEvent;
import com.fabricmanagement.user.domain.valueobject.Email;
import com.fabricmanagement.user.domain.valueobject.Username;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

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
@Getter
@NoArgsConstructor
@SuperBuilder
public class User extends BaseEntity {

    private UUID tenantId;
    private Username username;
    private Email email;
    private String firstName;
    private String lastName;
    private String displayName;
    private UserStatus status;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private Map<String, Object> preferences;
    private Map<String, Object> settings;
    
    // Domain events
    private final List<Object> domainEvents = new ArrayList<>();

    /**
     * Creates a new user with business validation
     */
    public static User create(UUID tenantId, Username username, Email email, 
                            String firstName, String lastName) {
        
        // Business validation
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
        if (username == null) {
            throw new IllegalArgumentException("Username cannot be null");
        }
        if (email == null) {
            throw new IllegalArgumentException("Email cannot be null");
        }

        User user = User.builder()
            .tenantId(tenantId)
            .username(username)
            .email(email)
            .firstName(firstName)
            .lastName(lastName)
            .displayName(firstName + " " + lastName)
            .status(UserStatus.ACTIVE)
            .build();

        // Add domain event
        user.addDomainEvent(new UserCreatedEvent(
            user.getId(),
            username.getValue(),
            email.getValue(),
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
            this.username.getValue(),
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
            this.username.getValue(),
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
            this.username.getValue(),
            this.email.getValue()
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
     * Gets and clears domain events
     */
    public List<Object> getAndClearDomainEvents() {
        List<Object> events = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return events;
    }
}
