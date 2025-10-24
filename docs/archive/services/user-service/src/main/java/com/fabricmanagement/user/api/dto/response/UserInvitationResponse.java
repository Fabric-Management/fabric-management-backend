package com.fabricmanagement.user.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInvitationResponse {

    private UUID userId;
    private UUID emailContactId;
    private UUID phoneContactId;
    private boolean verificationSent;
    private String message;
}

