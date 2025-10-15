package com.fabricmanagement.notification.api.dto.response;

import com.fabricmanagement.notification.domain.valueobject.NotificationChannel;
import com.fabricmanagement.notification.domain.valueobject.NotificationProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Notification Config Response
 * 
 * Security: Passwords and API keys are masked (never returned to client)
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationConfigResponse {
    
    private UUID id;
    private UUID tenantId;
    private NotificationChannel channel;
    private NotificationProvider provider;
    private Boolean isEnabled;
    
    // SMTP fields
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    private String fromEmail;
    private String fromName;
    
    // SMS/WhatsApp fields
    private String fromNumber;
    
    // Metadata
    private Integer priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Masked fields (for security)
    @Builder.Default
    private String smtpPassword = "***MASKED***";
    @Builder.Default
    private String apiKey = "***MASKED***";
}

