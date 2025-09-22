package com.fabricmanagement.contact.infrastructure.persistence.entity;

import com.fabricmanagement.common.core.domain.base.BaseEntity;
import com.fabricmanagement.contact.domain.valueobject.EmailType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * Entity representing an email address associated with a contact.
 */
@Entity
@Table(name = "contact_emails", indexes = {
    @Index(name = "idx_contact_email_contact_id", columnList = "contact_id"),
    @Index(name = "idx_contact_email_email", columnList = "email"),
    @Index(name = "idx_contact_email_type", columnList = "email_type"),
    @Index(name = "idx_contact_email_primary", columnList = "is_primary")
})
@SQLDelete(sql = "UPDATE contact_emails SET deleted = true WHERE id = ? AND version = ?")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(callSuper = true, exclude = "contact")
@EqualsAndHashCode(callSuper = true, exclude = "contact")
public class ContactEmailEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id", nullable = false)
    private ContactEntity contact;

    @NotBlank(message = "Email address is required")
    @Email(message = "Email should be valid")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_type", nullable = false, length = 20)
    private EmailType emailType = EmailType.PERSONAL;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = Boolean.FALSE;

    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = Boolean.FALSE;

    @Column(name = "verification_token", length = 100)
    private String verificationToken;

    @Column(name = "verification_sent_at")
    private LocalDateTime verificationSentAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "label", length = 50)
    private String label;

    @Column(name = "notes", length = 500)
    private String notes;

    /**
     * Marks this email as primary and ensures only one primary email per contact.
     */
    public void markAsPrimary() {
        if (contact != null) {
            contact.getEmails().forEach(e -> e.setIsPrimary(Boolean.FALSE));
        }
        this.isPrimary = Boolean.TRUE;
    }

    /**
     * Marks this email as verified.
     */
    public void markAsVerified() {
        this.isVerified = Boolean.TRUE;
        this.verifiedAt = LocalDateTime.now();
        this.verificationToken = null;
    }

    @PrePersist
    private void prePersist() {
        if (emailType == null) {
            emailType = EmailType.PERSONAL;
        }
        if (isPrimary == null) {
            isPrimary = Boolean.FALSE;
        }
        if (isVerified == null) {
            isVerified = Boolean.FALSE;
        }
    }
}