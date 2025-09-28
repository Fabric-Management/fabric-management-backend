package com.fabricmanagement.identity.application.dto.user;

import com.fabricmanagement.identity.domain.valueobject.ContactType;
import com.fabricmanagement.identity.domain.valueobject.ContactStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for user contact information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContactResponse {

    private String id;
    private ContactType contactType;
    private String contactValue;
    private ContactStatus status;
    private boolean isPrimary;
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;
}
