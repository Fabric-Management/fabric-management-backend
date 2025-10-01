package com.fabricmanagement.user.domain.valueobject;

import com.fabricmanagement.shared.domain.base.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * User Contact Information Entity
 * 
 * Represents a verified contact method for user authentication
 */
@Entity
@Table(name = "user_contacts")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class UserContact extends BaseEntity {
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "contact_value", nullable = false, unique = true)
    private String contactValue;        // email or phone number
    
    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false)
    private ContactType contactType;    // EMAIL or PHONE
    
    @Column(name = "is_verified", nullable = false)
    private boolean isVerified;         // verification status
    
    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;          // primary contact for login
    
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
    
    public enum ContactType {
        EMAIL,
        PHONE
    }
    
    public static UserContact email(String userId, String email, boolean isVerified, boolean isPrimary) {
        return UserContact.builder()
            .userId(userId)
            .contactValue(email)
            .contactType(ContactType.EMAIL)
            .isVerified(isVerified)
            .isPrimary(isPrimary)
            .verifiedAt(isVerified ? LocalDateTime.now() : null)
            .build();
    }
    
    public static UserContact phone(String userId, String phone, boolean isVerified, boolean isPrimary) {
        return UserContact.builder()
            .userId(userId)
            .contactValue(phone)
            .contactType(ContactType.PHONE)
            .isVerified(isVerified)
            .isPrimary(isPrimary)
            .verifiedAt(isVerified ? LocalDateTime.now() : null)
            .build();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserContact)) return false;
        
        UserContact that = (UserContact) o;
        
        // Compare key fields for equality (excluding verifiedAt which can vary by milliseconds)
        return isVerified == that.isVerified &&
               isPrimary == that.isPrimary &&
               (userId != null ? userId.equals(that.userId) : that.userId == null) &&
               (contactValue != null ? contactValue.equals(that.contactValue) : that.contactValue == null) &&
               contactType == that.contactType;
        // Note: verifiedAt is excluded from equals comparison as it can vary by milliseconds
    }
    
    @Override
    public int hashCode() {
        int result = userId != null ? userId.hashCode() : 0;
        result = 31 * result + (contactValue != null ? contactValue.hashCode() : 0);
        result = 31 * result + (contactType != null ? contactType.hashCode() : 0);
        result = 31 * result + (isVerified ? 1 : 0);
        result = 31 * result + (isPrimary ? 1 : 0);
        // Note: verifiedAt is excluded from hashCode to match equals() implementation
        return result;
    }
}
