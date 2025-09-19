package com.fabricmanagement.contact.infrastructure.persistence.entity;

import com.fabricmanagement.common.core.domain.base.BaseEntity;
import com.fabricmanagement.contact.domain.valueobject.AddressType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Entity representing a physical address associated with a contact.
 */
@Entity
@Table(name = "contact_addresses", indexes = {
    @Index(name = "idx_contact_address_contact_id", columnList = "contact_id"),
    @Index(name = "idx_contact_address_type", columnList = "address_type"),
    @Index(name = "idx_contact_address_primary", columnList = "is_primary"),
    @Index(name = "idx_contact_address_country", columnList = "country"),
    @Index(name = "idx_contact_address_postal_code", columnList = "postal_code")
})
@SQLDelete(sql = "UPDATE contact_addresses SET deleted = true WHERE id = ? AND version = ?")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(callSuper = true, exclude = "contact")
@EqualsAndHashCode(callSuper = true, exclude = "contact")
public class ContactAddressEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id", nullable = false)
    private ContactEntity contact;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 20)
    @Builder.Default
    private AddressType addressType = AddressType.WORK;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = Boolean.FALSE;

    @NotBlank(message = "Street address is required")
    @Size(max = 255, message = "Street address must not exceed 255 characters")
    @Column(name = "street_address_1", nullable = false, length = 255)
    private String streetAddress1;

    @Size(max = 255, message = "Street address 2 must not exceed 255 characters")
    @Column(name = "street_address_2", length = 255)
    private String streetAddress2;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must not exceed 100 characters")
    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Size(max = 100, message = "State/Province must not exceed 100 characters")
    @Column(name = "state_province", length = 100)
    private String stateProvince;

    @NotBlank(message = "Postal code is required")
    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @NotBlank(message = "Country is required")
    @Size(min = 2, max = 100, message = "Country must be between 2 and 100 characters")
    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "country_code", length = 3)
    private String countryCode;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "label", length = 50)
    private String label;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "is_validated", nullable = false)
    @Builder.Default
    private Boolean isValidated = Boolean.FALSE;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @Column(name = "validation_provider", length = 50)
    private String validationProvider;

    /**
     * Marks this address as primary and ensures only one primary address per contact.
     */
    public void markAsPrimary() {
        if (contact != null) {
            contact.getAddresses().forEach(a -> a.setIsPrimary(Boolean.FALSE));
        }
        this.isPrimary = Boolean.TRUE;
    }

    /**
     * Marks this address as validated.
     */
    public void markAsValidated(String provider) {
        this.isValidated = Boolean.TRUE;
        this.validatedAt = LocalDateTime.now();
        this.validationProvider = provider;
    }

    /**
     * Gets the full formatted address.
     */
    @Transient
    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        sb.append(streetAddress1);
        if (streetAddress2 != null && !streetAddress2.isEmpty()) {
            sb.append(", ").append(streetAddress2);
        }
        sb.append(", ").append(city);
        if (stateProvince != null && !stateProvince.isEmpty()) {
            sb.append(", ").append(stateProvince);
        }
        sb.append(" ").append(postalCode);
        sb.append(", ").append(country);
        return sb.toString();
    }

    /**
     * Gets a single-line address format.
     */
    @Transient
    public String getSingleLineAddress() {
        return getFullAddress();
    }

    /**
     * Checks if this address has geographic coordinates.
     */
    @Transient
    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    @PrePersist
    private void prePersist() {
        if (addressType == null) {
            addressType = AddressType.WORK;
        }
        if (isPrimary == null) {
            isPrimary = Boolean.FALSE;
        }
        if (isValidated == null) {
            isValidated = Boolean.FALSE;
        }
    }
}