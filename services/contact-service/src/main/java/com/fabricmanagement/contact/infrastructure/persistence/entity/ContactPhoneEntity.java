package com.fabricmanagement.contact.infrastructure.persistence.entity;

import com.fabricmanagement.common.core.domain.base.BaseEntity;
import com.fabricmanagement.contact.domain.valueobject.PhoneType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * Entity representing a phone number associated with a contact.
 */
@Entity
@Table(name = "contact_phones", indexes = {
    @Index(name = "idx_contact_phone_contact_id", columnList = "contact_id"),
    @Index(name = "idx_contact_phone_number", columnList = "phone_number"),
    @Index(name = "idx_contact_phone_type", columnList = "phone_type"),
    @Index(name = "idx_contact_phone_primary", columnList = "is_primary")
})
@SQLDelete(sql = "UPDATE contact_phones SET deleted = true WHERE id = ? AND version = ?")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(callSuper = true, exclude = "contact")
@EqualsAndHashCode(callSuper = true, exclude = "contact")
public class ContactPhoneEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id", nullable = false)
    private ContactEntity contact;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[+]?[0-9\\s\\-().]+$", message = "Invalid phone number format")
    @Size(min = 3, max = 50, message = "Phone number must be between 3 and 50 characters")
    @Column(name = "phone_number", nullable = false, length = 50)
    private String phoneNumber;

    @Column(name = "country_code", length = 5)
    private String countryCode;

    @Column(name = "extension", length = 10)
    private String extension;

    @Enumerated(EnumType.STRING)
    @Column(name = "phone_type", nullable = false, length = 20)
    @Builder.Default
    private PhoneType phoneType = PhoneType.MOBILE;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = Boolean.FALSE;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = Boolean.FALSE;

    @Column(name = "verification_code", length = 10)
    private String verificationCode;

    @Column(name = "verification_sent_at")
    private LocalDateTime verificationSentAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "can_receive_sms", nullable = false)
    @Builder.Default
    private Boolean canReceiveSms = Boolean.TRUE;

    @Column(name = "can_receive_calls", nullable = false)
    @Builder.Default
    private Boolean canReceiveCalls = Boolean.TRUE;

    @Column(name = "label", length = 50)
    private String label;

    @Column(name = "notes", length = 500)
    private String notes;

    /**
     * Marks this phone as primary and ensures only one primary phone per contact.
     */
    public void markAsPrimary() {
        if (contact != null) {
            contact.getPhones().forEach(p -> p.setIsPrimary(Boolean.FALSE));
        }
        this.isPrimary = Boolean.TRUE;
    }

    /**
     * Marks this phone as verified.
     */
    public void markAsVerified() {
        this.isVerified = Boolean.TRUE;
        this.verifiedAt = LocalDateTime.now();
        this.verificationCode = null;
    }

    /**
     * Gets the full phone number with country code and extension if available.
     */
    @Transient
    public String getFullPhoneNumber() {
        StringBuilder sb = new StringBuilder();
        if (countryCode != null && !countryCode.isEmpty()) {
            sb.append(countryCode).append(" ");
        }
        sb.append(phoneNumber);
        if (extension != null && !extension.isEmpty()) {
            sb.append(" x").append(extension);
        }
        return sb.toString();
    }

    @PrePersist
    private void prePersist() {
        if (phoneType == null) {
            phoneType = PhoneType.MOBILE;
        }
        if (isPrimary == null) {
            isPrimary = Boolean.FALSE;
        }
        if (isVerified == null) {
            isVerified = Boolean.FALSE;
        }
        if (canReceiveSms == null) {
            canReceiveSms = Boolean.TRUE;
        }
        if (canReceiveCalls == null) {
            canReceiveCalls = Boolean.TRUE;
        }
    }
}