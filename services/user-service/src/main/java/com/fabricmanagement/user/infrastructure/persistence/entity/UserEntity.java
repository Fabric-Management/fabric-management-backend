package com.fabricmanagement.user.infrastructure.persistence.entity;

import com.fabricmanagement.common.core.domain.base.BaseEntity;
import com.fabricmanagement.user.domain.model.UserStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Filter;

import java.util.UUID;

/**
 * User entity for user profile management (clean architecture).
 * Focused ONLY on user profile data - NO authentication/authorization data.
 * Authentication is handled by identity-service, authorization by auth-service.
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_user_status", columnList = "status"),
    @Index(name = "idx_user_department", columnList = "department"),
    @Index(name = "idx_user_deleted", columnList = "deleted"),
    @Index(name = "idx_user_first_name", columnList = "first_name"),
    @Index(name = "idx_user_last_name", columnList = "last_name")
})
@SQLDelete(sql = "UPDATE users SET deleted = true WHERE id = ? AND version = ?")
@Filter(name = "deletedFilter", condition = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class UserEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Size(max = 50, message = "Username cannot exceed 50 characters")
    @Column(name = "username", length = 50)
    private String username;

    @Size(max = 100, message = "Email cannot exceed 100 characters")
    @Column(name = "email", length = 100)
    private String email;

    // Basic user profile information
    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Size(max = 150, message = "Display name cannot exceed 150 characters")
    @Column(name = "display_name", length = 150)
    private String displayName;

    @Size(max = 100, message = "Job title cannot exceed 100 characters")
    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Size(max = 100, message = "Department cannot exceed 100 characters")
    @Column(name = "department", length = 100)
    private String department;

    // User preferences and settings
    @Size(max = 50, message = "Time zone cannot exceed 50 characters")
    @Column(name = "time_zone", length = 50)
    private String timeZone;

    @Size(max = 10, message = "Language preference cannot exceed 10 characters")
    @Column(name = "language_preference", length = 10)
    private String languagePreference;

    @Size(max = 500, message = "Profile image URL cannot exceed 500 characters")
    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    // User status
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * Gets the full name of the user.
     * @return the concatenated first and last name
     */
    @Transient
    public String getFullName() {
        if (firstName != null && lastName != null) {
            return (firstName + " " + lastName).trim();
        }
        return displayName != null ? displayName.trim() : "";
    }

    /**
     * Checks if the user is active.
     * @return true if the user status is ACTIVE
     */
    @Transient
    public boolean isActive() {
        return UserStatus.ACTIVE.equals(status);
    }

    /**
     * Checks if the user has job details.
     * @return true if job title or department is present
     */
    @Transient
    public boolean hasJobDetails() {
        return (jobTitle != null && !jobTitle.trim().isEmpty()) ||
               (department != null && !department.trim().isEmpty());
    }

    @PrePersist
    private void prePersist() {
        if (status == null) {
            status = UserStatus.ACTIVE;
        }
    }
}