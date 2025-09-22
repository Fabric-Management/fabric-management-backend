package com.fabricmanagement.user.application.dto.user.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for user information as a record.
 * Used for list views and basic user information.
 */
@Builder
public record UserResponse(
    UUID id,
    String firstName,
    String lastName,
    String displayName,
    String fullName,
    String jobTitle,
    String department,
    String status,
    boolean isActive,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

