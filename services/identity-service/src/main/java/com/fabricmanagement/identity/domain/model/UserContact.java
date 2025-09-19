package com.fabricmanagement.identity.domain.model;

import com.fabricmanagement.identity.domain.valueobject.ContactId;
import com.fabricmanagement.identity.domain.valueobject.ContactType;
import com.fabricmanagement.identity.domain.valueobject.UserId;
import lombok.*;

import java.time.LocalDateTime;

/**
 * UserContact entity - part of the User aggregate.
 * Represents a single contact method (email or phone) for a user.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContact {

    private ContactId id;
    private UserId userId;
    private ContactType type;
    private String value;
    private boolean verified;
    private LocalDateTime verifiedAt;
    private boolean isPrimary;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;

    /**
     * Creates a new unverified contact.
     */
    public static UserContact create(ContactId id, ContactType type, String value) {
        return new UserContact(id, type, value);
    }

    private UserContact(ContactId id, ContactType type, String value) {
        this.id = id;
        this.type = type;
        this.value = value;
        this.verified = false;
        this.isPrimary = false;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Marks this contact as verified.
     */
    public void verify() {
        this.verified = true;
        this.verifiedAt = LocalDateTime.now();
    }

    /**
     * Marks this contact as primary.
     */
    public void markAsPrimary() {
        this.isPrimary = true;
    }

    /**
     * Unmarks this contact as primary.
     */
    public void unmarkAsPrimary() {
        this.isPrimary = false;
    }

    /**
     * Records that this contact was used for authentication.
     */
    public void recordUsage() {
        this.lastUsedAt = LocalDateTime.now();
    }

    /**
     * Checks if this contact matches the given value (case-insensitive).
     */
    public boolean matches(String value) {
        return this.value.equalsIgnoreCase(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserContact that = (UserContact) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("%s:%s(%s)", type, value, verified ? "verified" : "unverified");
    }
}