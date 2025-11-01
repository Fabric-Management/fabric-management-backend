package com.fabricmanagement.common.platform.communication.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseJunctionEntity;
import com.fabricmanagement.common.platform.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * UserContact junction entity - Links User to Contact.
 *
 * <p>Represents the relationship between a User and their Contact information.
 * Supports multiple contacts per user (personal email, work email, phone, etc.).</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>✅ Multiple contacts per user</li>
 *   <li>✅ Default contact for notifications</li>
 *   <li>✅ Authentication contact flag</li>
 *   <li>✅ Personal vs. company-provided distinction</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Link user to personal email
 * UserContact userContact = UserContact.builder()
 *     .user(user)
 *     .contact(personalEmailContact)
 *     .isDefault(true)
 *     .isForAuthentication(true)
 *     .build();
 * }</pre>
 */
@Entity
@Table(name = "common_user_contact", schema = "common_communication",
    indexes = {
        @Index(name = "idx_user_contact_user", columnList = "user_id"),
        @Index(name = "idx_user_contact_contact", columnList = "contact_id"),
        @Index(name = "idx_user_contact_tenant", columnList = "tenant_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(UserContactId.class)
public class UserContact extends BaseJunctionEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", insertable = false, updatable = false)
    private Contact contact;

    /**
     * Default contact for notifications
     * <p>true = use this contact as default for sending notifications</p>
     */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    /**
     * Authentication contact flag
     * <p>true = this contact can be used for login/authentication</p>
     * <p>CRITICAL: Only verified EMAIL or PHONE contacts should have this = true</p>
     */
    @Column(name = "is_for_authentication", nullable = false)
    @Builder.Default
    private Boolean isForAuthentication = false;

    /**
     * Set as default contact for notifications
     */
    public void setAsDefault() {
        this.isDefault = true;
    }

    /**
     * Enable for authentication
     */
    public void enableForAuthentication() {
        this.isForAuthentication = true;
    }

    /**
     * Disable for authentication
     */
    public void disableForAuthentication() {
        this.isForAuthentication = false;
    }

    @Override
    protected String getModuleCode() {
        return "UCON";
    }
}
