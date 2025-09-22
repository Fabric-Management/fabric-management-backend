package com.fabricmanagement.user.application.dto.user.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Detailed response DTO for user information as a record.
 * Used for detailed views including all user profile data.
 */
@Builder
public record UserDetailResponse(
    UUID id,
    UUID tenantId,
    String firstName,
    String lastName,
    String displayName,
    String fullName,
    String jobTitle,
    String department,
    String timeZone,
    String languagePreference,
    String profileImageUrl,
    String status,
    boolean isActive,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String createdBy,
    String updatedBy,
    Long version
) {}

