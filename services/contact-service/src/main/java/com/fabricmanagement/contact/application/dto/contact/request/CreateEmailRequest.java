package com.fabricmanagement.contact.application.dto.contact.request;

import jakarta.validation.constraints.*;
import lombok.Builder;

/**
 * Request DTO for creating an email as a record.
 */
@Builder
public record CreateEmailRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    String email,

    @Size(max = 50, message = "Type cannot exceed 50 characters")
    String type,

    boolean isPrimary,

    @Size(max = 200, message = "Description cannot exceed 200 characters")
    String description
) {}