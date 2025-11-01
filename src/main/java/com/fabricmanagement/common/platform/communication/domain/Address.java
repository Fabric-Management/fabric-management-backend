package com.fabricmanagement.common.platform.communication.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Address entity - Generic address information for User and Company.
 *
 * <p>Represents physical addresses that can be associated with either
 * a User (HOME, WORK) or a Company (HEADQUARTERS, BRANCH, WAREHOUSE, etc.).</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>✅ Multiple address types (HOME, WORK, HEADQUARTERS, BRANCH, etc.)</li>
 *   <li>✅ Primary address flag</li>
 *   <li>✅ Label for categorization</li>
 *   <li>✅ Full address components (street, city, state, postal, country)</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // User's home address
 * Address homeAddress = Address.builder()
 *     .streetAddress("123 Main St, Apt 4B")
 *     .city("Istanbul")
 *     .state("Istanbul")
 *     .postalCode("34000")
 *     .country("Turkey")
 *     .addressType(AddressType.HOME)
 *     .isPrimary(true)
 *     .label("Home")
 *     .build();
 *
 * // Company headquarters
 * Address hqAddress = Address.builder()
 *     .streetAddress("456 Business Ave, Floor 10")
 *     .city("Istanbul")
 *     .state("Istanbul")
 *     .postalCode("34000")
 *     .country("Turkey")
 *     .addressType(AddressType.HEADQUARTERS)
 *     .isPrimary(true)
 *     .label("Main Headquarters")
 *     .build();
 * }</pre>
 */
@Entity
@Table(name = "common_address", schema = "common_communication",
    indexes = {
        @Index(name = "idx_address_type", columnList = "address_type"),
        @Index(name = "idx_address_city", columnList = "city"),
        @Index(name = "idx_address_country", columnList = "country"),
        @Index(name = "idx_address_tenant", columnList = "tenant_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address extends BaseEntity {

    /**
     * Street address (full street address with building/apartment number)
     * <p>Example: "123 Main St, Apt 4B" or "456 Business Ave, Floor 10"</p>
     */
    @Column(name = "street_address", nullable = false, length = 500)
    private String streetAddress;

    /**
     * City name
     */
    @Column(name = "city", nullable = false, length = 100)
    private String city;

    /**
     * State/Province/Region
     * <p>Examples: "Istanbul", "California", "Ontario"</p>
     */
    @Column(name = "state", length = 100)
    private String state;

    /**
     * Postal/ZIP code
     */
    @Column(name = "postal_code", length = 20)
    private String postalCode;

    /**
     * Country name
     * <p>Examples: "Turkey", "United States", "Canada"</p>
     */
    @Column(name = "country", nullable = false, length = 100)
    private String country;

    /**
     * ISO 3166-1 alpha-2 country code
     * <p>Examples: "TR", "GB", "DE", "FR", "US"</p>
     * <p>Required for address validation and standardization</p>
     */
    @Column(name = "country_code", length = 2)
    private String countryCode;

    /**
     * District/County/Sub-administrative area
     * <p>Examples: "Kadıköy", "Westminster", "Bavaria"</p>
     */
    @Column(name = "district", length = 100)
    private String district;

    /**
     * Latitude coordinate (from Google Geocoding)
     */
    @Column(name = "latitude")
    private Double latitude;

    /**
     * Longitude coordinate (from Google Geocoding)
     */
    @Column(name = "longitude")
    private Double longitude;

    /**
     * Google Places ID
     * <p>Unique identifier for the address in Google Places database</p>
     */
    @Column(name = "place_id", length = 255)
    private String placeId;

    /**
     * Address type (HOME, WORK, HEADQUARTERS, BRANCH, etc.)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 50)
    private AddressType addressType;

    /**
     * Primary address flag
     * <p>true = primary address for this owner (User or Company)</p>
     * <p>Multiple addresses can have isPrimary = true (one per type)</p>
     */
    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;

    /**
     * Label for categorization
     * <p>Examples: "Home", "Work", "Main Headquarters", "Warehouse A", "Shipping Address"</p>
     */
    @Column(name = "label", length = 100)
    private String label;

    /**
     * Google's formatted address (canonical format)
     * <p>Stored separately for UI display and consistency</p>
     */
    @Column(name = "formatted_address", length = 500)
    private String formattedAddress;

    /**
     * Mark address as primary
     */
    public void setAsPrimary() {
        this.isPrimary = true;
    }

    /**
     * Remove primary flag
     */
    public void removePrimary() {
        this.isPrimary = false;
    }

    /**
     * Get full address as formatted string
     * <p>Uses formattedAddress if available (from Google), otherwise constructs from components</p>
     */
    public String getFormattedAddress() {
        // If we have a stored formatted address (from Google), use it
        // Otherwise, construct from components
        StringBuilder sb = new StringBuilder();
        sb.append(streetAddress);
        if (district != null && !district.isBlank()) {
            sb.append(", ").append(district);
        }
        if (city != null && !city.isBlank()) {
            sb.append(", ").append(city);
        }
        if (state != null && !state.isBlank()) {
            sb.append(", ").append(state);
        }
        if (postalCode != null && !postalCode.isBlank()) {
            sb.append(" ").append(postalCode);
        }
        if (country != null && !country.isBlank()) {
            sb.append(", ").append(country);
        }
        return sb.toString();
    }

    /**
     * Check if address has geolocation data
     */
    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    /**
     * Check if address is verified (has placeId)
     */
    public boolean isVerified() {
        return placeId != null && !placeId.isBlank();
    }

    @Override
    protected String getModuleCode() {
        return "ADDR";
    }
}

