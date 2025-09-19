package com.fabricmanagement.identity.domain.model;

import com.fabricmanagement.identity.domain.valueobject.ContactType;
import com.fabricmanagement.identity.domain.valueobject.ContactStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * UserContact entity for managing user contact information.
 * Part of the User aggregate root.
 */
@Entity
@Table(name = "user_contacts", indexes = {
    @Index(name = "idx_user_contact_user_id", columnList = "user_id"),
    @Index(name = "idx_user_contact_tenant_user", columnList = "tenant_id, user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class UserContact extends BaseEntity {
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "job_title", length = 100)
    private String jobTitle;
    
    @Column(name = "department", length = 100)
    private String department;
    
    @Column(name = "time_zone", length = 50)
    private String timeZone;
    
    @Column(name = "language_preference", length = 10)
    private String languagePreference;
    
    @Column(name = "preferred_contact_method", length = 20)
    private String preferredContactMethod;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false)
    private ContactType contactType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ContactStatus status;
    
    @Column(name = "primary_email", length = 255)
    private String primaryEmail;
    
    @Column(name = "primary_phone", length = 50)
    private String primaryPhone;
    
    @Column(name = "primary_address", length = 500)
    private String primaryAddress;
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}