package com.fabricmanagement.user.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Contact domain model for User Service.
 * Manages user contact information (email, phone).
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
public class UserContact {

    private UUID id;
    private UUID userId;
    private ContactType type;
    private String value;
    private ContactStatus status;
    private boolean isPrimary;
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Creates a new user contact.
     */
    public static UserContact create(UUID userId, ContactType type, String value) {
        return UserContact.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .type(type)
            .value(value)
            .status(ContactStatus.PENDING)
            .isPrimary(false)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Marks contact as verified.
     */
    public void verify() {
        this.status = ContactStatus.VERIFIED;
        this.verifiedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Marks contact as primary.
     */
    public void markAsPrimary() {
        this.isPrimary = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Unmarks contact as primary.
     */
    public void unmarkAsPrimary() {
        this.isPrimary = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Checks if contact is verified.
     */
    public boolean isVerified() {
        return ContactStatus.VERIFIED.equals(status);
    }

    /**
     * Checks if contact is primary.
     */
    public boolean isPrimary() {
        return isPrimary;
    }

    /**
     * Contact type enum.
     */
    public enum ContactType {
        EMAIL, PHONE
    }

    /**
     * Contact status enum.
     */
    public enum ContactStatus {
        PENDING, VERIFIED, FAILED
    }
}
