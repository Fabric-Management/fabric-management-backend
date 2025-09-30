package com.fabricmanagement.user.domain.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Password Reset Completed Event
 * 
 * Domain event when password reset is completed
 */
@Getter
@RequiredArgsConstructor
public class PasswordResetCompletedEvent {
    
    private final UUID userId;
    private final String contactValue;
    private final LocalDateTime resetAt;
    private final String resetMethod;
}
