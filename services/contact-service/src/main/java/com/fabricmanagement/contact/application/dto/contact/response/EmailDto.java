package com.fabricmanagement.contact.application.dto.contact.response;

import lombok.Builder;

import java.util.UUID;

/**
 * Email DTO as a record.
 */
@Builder
public record EmailDto(
    UUID id,
    String email,
    String type,
    boolean isPrimary,
    String description
) {
    public String getDisplayText() {
        return isPrimary ? email + " (Primary)" : email;
    }
}