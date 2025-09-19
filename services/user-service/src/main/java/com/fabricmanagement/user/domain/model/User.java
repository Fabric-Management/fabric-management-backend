package com.fabricmanagement.user.domain.model;

import com.fabricmanagement.user.domain.valueobject.Role;
import com.fabricmanagement.user.domain.valueobject.UserId;
import com.fabricmanagement.user.domain.valueobject.UserStatus;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * @deprecated Use identity-service User model instead.
 * This service is being phased out in favor of consolidated identity-service.
 */
@Deprecated
public class User {

    private final UserId id;
    private final UUID tenantId;
    private final String username;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final Role role;
    private final UserStatus status;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    private User(Builder builder) {
        this.id = builder.id;
        this.tenantId = builder.tenantId;
        this.username = builder.username;
        this.email = builder.email;
        this.firstName = builder.firstName;
        this.lastName = builder.lastName;
        this.role = builder.role;
        this.status = builder.status;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public UserId getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public Role getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UserId id;
        private UUID tenantId;
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private Role role = Role.USER;
        private UserStatus status = UserStatus.ACTIVE;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        public Builder id(UserId id) { this.id = id; return this; }
        public Builder tenantId(UUID tenantId) { this.tenantId = tenantId; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder firstName(String firstName) { this.firstName = firstName; return this; }
        public Builder lastName(String lastName) { this.lastName = lastName; return this; }
        public Builder role(Role role) { this.role = role; return this; }
        public Builder status(UserStatus status) { this.status = status; return this; }
        public Builder createdAt(OffsetDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public User build() {
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(username, "username must not be null");
            Objects.requireNonNull(email, "email must not be null");
            Objects.requireNonNull(firstName, "firstName must not be null");
            Objects.requireNonNull(lastName, "lastName must not be null");
            return new User(this);
        }
    }
}

