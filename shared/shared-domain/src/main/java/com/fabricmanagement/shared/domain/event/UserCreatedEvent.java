package com.fabricmanagement.shared.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Created Event
 * 
 * Published when a new user is created (tenant onboarding or user invitation).
 * Triggers notification service to send verification email/SMS/WhatsApp.
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCreatedEvent {
    
    private String eventId;
    private UUID tenantId;
    private UUID userId;
    private String email;
    private String phone;
    private String firstName;
    private String lastName;
    private String companyName;
    private String verificationCode;
    
    /**
     * Preferred notification channel (from mobile/web frontend)
     * Options: WHATSAPP (default for mobile), EMAIL (default for web), SMS (optional)
     */
    private String preferredChannel; // "WHATSAPP", "EMAIL", "SMS"
    
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}

