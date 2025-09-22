package com.fabricmanagement.contact.application.dto.contact.request;

import jakarta.validation.constraints.*;
import lombok.Builder;

/**
 * Request DTO for creating an address as a record.
 */
@Builder
public record CreateAddressRequest(
    @NotBlank(message = "Street address is required")
    @Size(max = 200, message = "Street 1 cannot exceed 200 characters")
    String street1,

    @Size(max = 200, message = "Street 2 cannot exceed 200 characters")
    String street2,

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City cannot exceed 100 characters")
    String city,

    @Size(max = 100, message = "State cannot exceed 100 characters")
    String state,

    @Size(max = 20, message = "Zip code cannot exceed 20 characters")
    String zipCode,

    @NotBlank(message = "Country is required")
    @Size(max = 100, message = "Country cannot exceed 100 characters")
    String country,

    @Size(max = 50, message = "Type cannot exceed 50 characters")
    String type,

    boolean isPrimary,

    @Size(max = 200, message = "Description cannot exceed 200 characters")
    String description
) {}