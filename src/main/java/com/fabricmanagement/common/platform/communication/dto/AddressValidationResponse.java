package com.fabricmanagement.common.platform.communication.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for address validation.
 *
 * <p>Contains normalized, verified address data from Google Maps Platform.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressValidationResponse {

    /**
     * Verification status
     */
    private VerificationStatus verificationStatus;

    /**
     * Normalized address data
     */
    private AddressDto address;

    /**
     * Google Places ID
     */
    private String placeId;

    /**
     * Formatted address (Google's canonical format)
     */
    private String formattedAddress;

    /**
     * Street address
     */
    private String streetAddress;

    /**
     * City
     */
    private String city;

    /**
     * State/Province
     */
    private String state;

    /**
     * District/County
     */
    private String district;

    /**
     * Postal/ZIP code
     */
    private String postalCode;

    /**
     * Country name
     */
    private String country;

    /**
     * ISO 3166-1 alpha-2 country code
     */
    private String countryCode;

    /**
     * Latitude coordinate
     */
    private Double latitude;

    /**
     * Longitude coordinate
     */
    private Double longitude;

    /**
     * Error message (if validation failed)
     */
    private String errorMessage;

    /**
     * Verification status enumeration
     */
    public enum VerificationStatus {
        VERIFIED,    // Address successfully validated and normalized
        PARTIAL,     // Address validated but some components missing
        FAILED       // Validation failed
    }
}

