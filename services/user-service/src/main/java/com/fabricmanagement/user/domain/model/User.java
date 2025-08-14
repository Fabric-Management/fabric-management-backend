package com.fabricmanagement.user.domain.model;

import com.fabricmanagement.common.core.domain.AggregateRoot;
import com.fabricmanagement.common.core.exception.DomainException;
import com.fabricmanagement.user.domain.event.UserActivatedEvent;
import com.fabricmanagement.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.user.domain.event.UserDeactivatedEvent;
import com.fabricmanagement.user.domain.event.UserSuspendedEvent;
import com.fabricmanagement.user.domain.exception.UserErrorCode;
import com.fabricmanagement.user.domain.valueobject.TenantId;
import com.fabricmanagement.user.domain.valueobject.UserId;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class User extends AggregateRoot {

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    // Factory method
    public static User create(String firstName, String lastName, String username, TenantId tenantId) {
        User user = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .username(username)
                .tenantId(tenantId.getValue())
                .status(UserStatus.PENDING)
                .deleted(false)
                .build();

        // Register domain event for user creation
        user.registerEvent(new UserCreatedEvent(user.getId()));
        return user;
    }

    // Business methods
    public void activate(String passwordHash) {
        if (this.status != UserStatus.PENDING) {
            throw new DomainException(UserErrorCode.USER_CANNOT_BE_ACTIVATED);
        }
        this.passwordHash = passwordHash;
        this.status = UserStatus.ACTIVE;
        // UserActivatedEvent expects userId
        this.registerEvent(new UserActivatedEvent(this.getId()));
    }

    public void deactivate() {
        if (this.status != UserStatus.ACTIVE) {
            throw new DomainException(UserErrorCode.USER_CANNOT_BE_DEACTIVATED);
        }
        this.status = UserStatus.INACTIVE;
        // UserDeactivatedEvent expects userId
        this.registerEvent(new UserDeactivatedEvent(this.getId()));
    }

    public void suspend() {
        if (this.status != UserStatus.SUSPENDED) {
            this.status = UserStatus.SUSPENDED;
            // UserSuspendedEvent expects userId
            this.registerEvent(new UserSuspendedEvent(this.getId()));
        }
    }

    public void updateProfile(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public String getFullName() {
        return String.format("%s %s", firstName, lastName).trim();
    }

    public UserId getUserId() {
        return UserId.of(this.getId());
    }

    public TenantId getTenantIdAsValueObject() {
        return TenantId.of(this.tenantId);
    }
}