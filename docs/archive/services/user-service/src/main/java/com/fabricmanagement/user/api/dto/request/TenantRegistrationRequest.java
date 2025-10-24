package com.fabricmanagement.user.api.dto.request;

import com.fabricmanagement.shared.infrastructure.constants.ValidationConstants;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantRegistrationRequest {
    
    @NotBlank(message = ValidationConstants.MSG_REQUIRED)
    @Size(min = 2, max = 100, message = "Company name must be between 2 and 100 characters")
    @Pattern(regexp = "^[\\p{L}\\p{N}\\s\\-&.,()'/]+$", message = "Company name contains invalid characters")
    private String companyName;
    
    @Size(max = 200, message = "Legal name must not exceed 200 characters")
    private String legalName;
    
    @NotBlank(message = ValidationConstants.MSG_REQUIRED)
    @Size(max = 50, message = "Tax ID must not exceed 50 characters")
    @JsonAlias("taxNumber") // Accept both taxId and taxNumber for backward compatibility
    private String taxId;
    
    @Size(max = 100, message = "Registration number must not exceed 100 characters")
    private String registrationNumber;
    
    @NotBlank(message = ValidationConstants.MSG_REQUIRED)
    private String companyType;
    
    @NotBlank(message = ValidationConstants.MSG_REQUIRED)
    private String industry;
    
    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;
    
    @Pattern(regexp = "^(https?://)?([\\da-z.-]+)\\.([a-z.]{2,6})[/\\w .-]*/?$", message = "Invalid website URL")
    private String website;
    
    @NotBlank(message = ValidationConstants.MSG_REQUIRED)
    @Size(max = 255, message = "Address line 1 must not exceed 255 characters")
    private String addressLine1;
    
    @Size(max = 255, message = "Address line 2 must not exceed 255 characters")
    private String addressLine2;
    
    @NotBlank(message = ValidationConstants.MSG_REQUIRED)
    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;
    
    @Size(max = 100, message = "District must not exceed 100 characters")
    private String district;
    
    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    private String postalCode;
    
    @Size(max = 100, message = "Country must not exceed 100 characters")
    private String country;
    
    @NotBlank(message = ValidationConstants.MSG_REQUIRED)
    @Size(min = 2, max = ValidationConstants.MAX_NAME_LENGTH, message = ValidationConstants.MSG_TOO_LONG)
    private String firstName;
    
    @NotBlank(message = ValidationConstants.MSG_REQUIRED)
    @Size(min = 2, max = ValidationConstants.MAX_NAME_LENGTH, message = ValidationConstants.MSG_TOO_LONG)
    private String lastName;
    
    @NotBlank(message = ValidationConstants.MSG_REQUIRED)
    @Email(message = ValidationConstants.MSG_INVALID_EMAIL)
    @Pattern(regexp = ValidationConstants.EMAIL_PATTERN, message = ValidationConstants.MSG_INVALID_EMAIL)
    @Size(max = ValidationConstants.MAX_EMAIL_LENGTH, message = ValidationConstants.MSG_TOO_LONG)
    private String email;
    
    @Pattern(regexp = ValidationConstants.PHONE_PATTERN, message = ValidationConstants.MSG_INVALID_PHONE)
    @Size(max = ValidationConstants.MAX_PHONE_LENGTH, message = ValidationConstants.MSG_TOO_LONG)
    private String phone;
    
    /**
     * Preferred notification channel for verification code
     * Options: "WHATSAPP" (mobile default), "EMAIL" (web default), "SMS" (optional)
     * If not provided, defaults to WHATSAPP
     */
    @Pattern(regexp = "^(WHATSAPP|EMAIL|SMS)$", message = "Preferred channel must be WHATSAPP, EMAIL, or SMS")
    private String preferredChannel;
}

