package com.fabricmanagement.contact.domain.entity;

import com.fabricmanagement.shared.domain.base.BaseEntity;
import com.fabricmanagement.contact.domain.aggregate.Contact;
import com.fabricmanagement.contact.domain.valueobject.AddressType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Address Entity (Anemic Domain Model)
 * 
 * Stores complex address data for Contact.ADDRESS type
 * Pure data holder - business logic in Service layer
 * 
 * Relationship: Address 1:1 Contact (via contact_id)
 */
@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Address extends BaseEntity {

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;  // Denormalized for fast queries

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 50)
    private Contact.OwnerType ownerType;

    // Address fields
    @Column(name = "address_line1", nullable = false, length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    // Google Places integration (optional)
    @Column(name = "google_place_id", length = 255)
    private String googlePlaceId;

    @Column(name = "formatted_address", columnDefinition = "TEXT")
    private String formattedAddress;

    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    // Metadata
    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", length = 50)
    private AddressType addressType;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // NO BUSINESS METHODS!
    // Business logic → Service layer
    // Validation → Service/DTO validation
    // Computed properties → Mapper layer
}

