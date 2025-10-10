package com.fabricmanagement.contact.domain.aggregate;

import com.fabricmanagement.shared.domain.base.BaseEntity;
import com.fabricmanagement.contact.domain.valueobject.ContactType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "contacts")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Contact extends BaseEntity {
    
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
    
    @Column(name = "owner_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private OwnerType ownerType;
    
    @Column(name = "contact_value", nullable = false)
    private String contactValue;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false)
    private ContactType contactType;
    
    @Column(name = "is_verified", nullable = false)
    private boolean isVerified;
    
    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;
    
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
    
    @Column(name = "verification_code")
    private String verificationCode;
    
    @Column(name = "verification_expires_at")
    private LocalDateTime verificationExpiresAt;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    public enum OwnerType {
        USER,
        COMPANY
    }
    
    public static Contact create(
        UUID ownerId,
        OwnerType ownerType,
        String contactValue,
        ContactType contactType,
        boolean isPrimary
    ) {
        return Contact.builder()
                .ownerId(ownerId)
                .ownerType(ownerType)
                .contactValue(contactValue)
                .contactType(contactType)
                .isVerified(false)
                .isPrimary(isPrimary)
                .build();
    }
    
    public void verify(String code) {
        if (this.isVerified) {
            throw new IllegalStateException("Contact is already verified");
        }
        
        if (!code.equals(this.verificationCode)) {
            throw new IllegalArgumentException("Invalid verification code");
        }
        
        if (LocalDateTime.now().isAfter(this.verificationExpiresAt)) {
            throw new IllegalArgumentException("Verification code has expired");
        }
        
        this.isVerified = true;
        this.verifiedAt = LocalDateTime.now();
        this.verificationCode = null;
        this.verificationExpiresAt = null;
    }
    
    public String generateVerificationCode() {
        this.verificationCode = generateRandomCode();
        this.verificationExpiresAt = LocalDateTime.now().plusMinutes(15);
        return this.verificationCode;
    }
    
    public void makePrimary() {
        if (!this.isVerified) {
            throw new IllegalStateException("Cannot make unverified contact primary");
        }
        
        this.isPrimary = true;
    }
    
    public void removePrimary() {
        this.isPrimary = false;
    }
    
    @Override
    public void markAsDeleted() {
        super.markAsDeleted();
        this.deletedAt = LocalDateTime.now();
    }
    
    private String generateRandomCode() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        int code = random.nextInt(1000000);
        return String.format("%06d", code);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Contact)) return false;
        
        Contact contact = (Contact) o;
        
        return isVerified == contact.isVerified &&
               isPrimary == contact.isPrimary &&
               (ownerId != null ? ownerId.equals(contact.ownerId) : contact.ownerId == null) &&
               ownerType == contact.ownerType &&
               (contactValue != null ? contactValue.equals(contact.contactValue) : contact.contactValue == null) &&
               contactType == contact.contactType;
    }
    
    @Override
    public int hashCode() {
        int result = ownerId != null ? ownerId.hashCode() : 0;
        result = 31 * result + (ownerType != null ? ownerType.hashCode() : 0);
        result = 31 * result + (contactValue != null ? contactValue.hashCode() : 0);
        result = 31 * result + (contactType != null ? contactType.hashCode() : 0);
        result = 31 * result + (isVerified ? 1 : 0);
        result = 31 * result + (isPrimary ? 1 : 0);
        return result;
    }
}
