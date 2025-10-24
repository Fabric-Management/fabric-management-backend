package com.fabricmanagement.contact.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAddressRequest {

    @NotBlank(message = "Owner ID is required")
    private String ownerId;

    @NotBlank(message = "Owner type is required")
    private String ownerType; // USER or COMPANY

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 255, message = "Address line 1 is too long")
    private String addressLine1;

    @Size(max = 255, message = "Address line 2 is too long")
    private String addressLine2;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City is too long")
    private String city;

    @Size(max = 100, message = "State is too long")
    private String state;

    @Size(max = 100, message = "District is too long")
    private String district;

    @Size(max = 20, message = "Postal code is too long")
    private String postalCode;

    @NotBlank(message = "Country is required")
    @Size(max = 100, message = "Country is too long")
    private String country;

    @Size(max = 50, message = "Address type is too long")
    private String addressType; // HOME, WORK, BILLING, SHIPPING

    @NotNull(message = "Primary flag is required")
    private Boolean isPrimary;

    // Google Places (optional)
    @Size(max = 255, message = "Google place ID is too long")
    private String googlePlaceId;
}

