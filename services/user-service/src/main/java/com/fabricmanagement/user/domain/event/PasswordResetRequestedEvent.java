package com.fabricmanagement.user.domain.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Password Reset Requested Event
 * 
 * Domain event when password reset is requested
 */
@Getter
@RequiredArgsConstructor
public class PasswordResetRequestedEvent {
    
    private final UUID userId;
    private final String contactValue;
    private final String resetMethod;
    private final String resetToken;
    private final LocalDateTime requestedAt;
    private final LocalDateTime expiresAt;
}
