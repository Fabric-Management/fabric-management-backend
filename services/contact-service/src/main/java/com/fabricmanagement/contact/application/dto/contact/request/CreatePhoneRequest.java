package com.fabricmanagement.contact.application.dto.contact.request;

import jakarta.validation.constraints.*;
import lombok.Builder;

/**
 * Request DTO for creating a phone as a record.
 */
@Builder
public record CreatePhoneRequest(
    @Size(max = 5, message = "Country code cannot exceed 5 characters")
    String countryCode,

    @Size(max = 5, message = "Area code cannot exceed 5 characters")
    String areaCode,

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9\\-().\\s]{3,20}$", message = "Phone number must be valid")
    String number,

    @Size(max = 10, message = "Extension cannot exceed 10 characters")
    String extension,

    @Size(max = 50, message = "Type cannot exceed 50 characters")
    String type,

    boolean isPrimary,

    @Size(max = 200, message = "Description cannot exceed 200 characters")
    String description
) {}