package com.fabricmanagement.identity.domain.model;

import com.fabricmanagement.identity.domain.valueobject.ContactId;
import com.fabricmanagement.identity.domain.valueobject.ContactType;
import com.fabricmanagement.identity.domain.valueobject.ContactStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * UserContact value object for managing user contact information.
 * Part of the User aggregate root.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class UserContact {
    
    private ContactId id;
    private ContactType type;
    private String value;
    private ContactStatus status;
    private boolean primary;
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;
    
    /**
     * Creates a new contact.
     */
    public static UserContact create(ContactId id, ContactType type, String value) {
        return UserContact.builder()
            .id(id)
            .type(type)
            .value(value)
            .status(ContactStatus.PENDING)
            .primary(false)
            .createdAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Marks the contact as verified.
     */
    public void verify() {
        this.status = ContactStatus.VERIFIED;
        this.verifiedAt = LocalDateTime.now();
    }
    
    /**
     * Marks the contact as primary.
     */
    public void markAsPrimary() {
        this.primary = true;
    }
    
    /**
     * Unmarks the contact as primary.
     */
    public void unmarkAsPrimary() {
        this.primary = false;
    }
    
    /**
     * Checks if the contact is verified.
     */
    public boolean isVerified() {
        return status == ContactStatus.VERIFIED;
    }
    
    /**
     * Checks if the contact is primary.
     */
    public boolean isPrimary() {
        return primary;
    }
    
    /**
     * Checks if the contact is pending verification.
     */
    public boolean isPending() {
        return status == ContactStatus.PENDING;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserContact that = (UserContact) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return String.format("UserContact{id=%s, type=%s, value='%s', status=%s, primary=%s}", 
            id, type, value, status, primary);
    }
}