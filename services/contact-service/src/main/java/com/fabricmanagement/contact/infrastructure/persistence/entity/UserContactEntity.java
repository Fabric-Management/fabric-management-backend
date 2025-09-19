package com.fabricmanagement.contact.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Contact entity specifically for users.
 * Links contact information to a user in the user-service.
 */
@Entity
@Table(name = "user_contacts", indexes = {
    @Index(name = "idx_user_contact_user_id", columnList = "user_id", unique = true),
    @Index(name = "idx_user_contact_tenant_user", columnList = "tenant_id, user_id")
})
@DiscriminatorValue("USER")
@PrimaryKeyJoinColumn(name = "contact_id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class UserContactEntity extends ContactEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "linkedin_url", length = 500)
    private String linkedinUrl;

    @Column(name = "twitter_handle", length = 50)
    private String twitterHandle;

    @Column(name = "emergency_contact_name", length = 200)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 50)
    private String emergencyContactPhone;

    @Column(name = "emergency_contact_relationship", length = 100)
    private String emergencyContactRelationship;

    @Column(name = "preferred_contact_method", length = 20)
    private String preferredContactMethod;

    @Column(name = "time_zone", length = 50)
    private String timeZone;

    @Column(name = "language_preference", length = 10)
    private String languagePreference;

    /**
     * Creates a new UserContactEntity with the given user ID and tenant ID.
     */
    public static UserContactEntity createForUser(UUID userId, UUID tenantId) {
        UserContactEntity entity = new UserContactEntity();
        entity.setUserId(userId);
        entity.setTenantId(tenantId);
        return entity;
    }

    /**
     * Checks if this contact belongs to the specified user.
     */
    @Transient
    public boolean belongsToUser(UUID userId) {
        return this.userId != null && this.userId.equals(userId);
    }
}