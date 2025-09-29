package com.fabricmanagement.contact.domain.model;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Contact Phone value object.
 * Represents a phone number with its properties.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ContactPhone {

    private UUID id;
    private String phoneNumber;
    private UserContactInfo.PhoneType type;
    private boolean isPrimary;
    private boolean isVerified;
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}