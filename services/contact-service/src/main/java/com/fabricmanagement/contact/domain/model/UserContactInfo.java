package com.fabricmanagement.contact.domain.model;

import com.fabricmanagement.common.core.domain.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * User Contact Information aggregate root.
 * Manages all contact information for a user including emails, phones, and addresses.
 *
 * This entity is responsible for:
 * - Managing multiple contact methods for a user
 * - Ensuring data consistency (e.g., only one primary email/phone)
 * - Contact verification status
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class UserContactInfo extends BaseEntity {

    private UUID userId; // Reference to User Service
    private UUID identityId; // Reference to Identity Service

    private List<ContactEmail> emails = new ArrayList<>();
    private List<ContactPhone> phones = new ArrayList<>();
    private List<ContactAddress> addresses = new ArrayList<>();

    // Business rules

    /**
     * Adds an email to the user's contact information.
     */
    public void addEmail(String email, EmailType type, boolean isPrimary) {
        if (isPrimary) {
            // Unmark current primary if exists
            emails.stream()
                .filter(ContactEmail::isPrimary)
                .forEach(e -> e.setPrimary(false));
        }

        ContactEmail contactEmail = ContactEmail.builder()
            .id(UUID.randomUUID())
            .email(email)
            .type(type)
            .isPrimary(isPrimary)
            .isVerified(false)
            .createdAt(LocalDateTime.now())
            .build();

        emails.add(contactEmail);
    }

    /**
     * Adds a phone to the user's contact information.
     */
    public void addPhone(String phoneNumber, PhoneType type, boolean isPrimary) {
        if (isPrimary) {
            // Unmark current primary if exists
            phones.stream()
                .filter(ContactPhone::isPrimary)
                .forEach(p -> p.setPrimary(false));
        }

        ContactPhone contactPhone = ContactPhone.builder()
            .id(UUID.randomUUID())
            .phoneNumber(phoneNumber)
            .type(type)
            .isPrimary(isPrimary)
            .isVerified(false)
            .createdAt(LocalDateTime.now())
            .build();

        phones.add(contactPhone);
    }

    /**
     * Adds an address to the user's contact information.
     */
    public void addAddress(String street, String city, String state,
                          String country, String postalCode, AddressType type) {
        ContactAddress address = ContactAddress.builder()
            .id(UUID.randomUUID())
            .street(street)
            .city(city)
            .state(state)
            .country(country)
            .postalCode(postalCode)
            .type(type)
            .createdAt(LocalDateTime.now())
            .build();

        addresses.add(address);
    }

    /**
     * Gets the primary email if exists.
     */
    public ContactEmail getPrimaryEmail() {
        return emails.stream()
            .filter(ContactEmail::isPrimary)
            .findFirst()
            .orElse(null);
    }

    /**
     * Gets the primary phone if exists.
     */
    public ContactPhone getPrimaryPhone() {
        return phones.stream()
            .filter(ContactPhone::isPrimary)
            .findFirst()
            .orElse(null);
    }

    /**
     * Verifies an email by ID.
     */
    public void verifyEmail(UUID emailId) {
        emails.stream()
            .filter(e -> e.getId().equals(emailId))
            .findFirst()
            .ifPresent(e -> {
                e.setVerified(true);
                e.setVerifiedAt(LocalDateTime.now());
            });
    }

    /**
     * Verifies a phone by ID.
     */
    public void verifyPhone(UUID phoneId) {
        phones.stream()
            .filter(p -> p.getId().equals(phoneId))
            .findFirst()
            .ifPresent(p -> {
                p.setVerified(true);
                p.setVerifiedAt(LocalDateTime.now());
            });
    }

    /**
     * Email type enumeration.
     */
    public enum EmailType {
        PERSONAL, WORK, OTHER
    }

    /**
     * Phone type enumeration.
     */
    public enum PhoneType {
        MOBILE, HOME, WORK, FAX, OTHER
    }

    /**
     * Address type enumeration.
     */
    public enum AddressType {
        HOME, WORK, BILLING, SHIPPING, OTHER
    }
}