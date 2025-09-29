package com.fabricmanagement.user.application.dto.contact.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for user contact information.
 */
public record UserContactResponse(
    UUID id,
    UUID userId,
    String contactType,
    String contactValue,
    String status,
    boolean isPrimary,
    LocalDateTime verifiedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    
    /**
     * Creates a UserContactResponse from domain model.
     */
    public static UserContactResponse from(UserContact userContact) {
        return new UserContactResponse(
            userContact.getId(),
            userContact.getUserId(),
            userContact.getType().name(),
            userContact.getValue(),
            userContact.getStatus().name(),
            userContact.isPrimary(),
            userContact.getVerifiedAt(),
            userContact.getCreatedAt(),
            userContact.getUpdatedAt()
        );
    }
}
