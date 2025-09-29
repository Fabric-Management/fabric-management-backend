package com.fabricmanagement.contact.domain.model;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Contact Email value object.
 * Represents an email address with its properties.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ContactEmail {

    private UUID id;
    private String email;
    private UserContactInfo.EmailType type;
    private boolean isPrimary;
    private boolean isVerified;
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}