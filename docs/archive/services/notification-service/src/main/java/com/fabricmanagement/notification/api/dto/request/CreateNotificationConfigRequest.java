package com.fabricmanagement.notification.api.dto.request;

import com.fabricmanagement.notification.domain.valueobject.NotificationChannel;
import com.fabricmanagement.notification.domain.valueobject.NotificationProvider;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Create Notification Config Request
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateNotificationConfigRequest {
    
    @NotNull(message = "Channel is required")
    private NotificationChannel channel;
    
    @NotNull(message = "Provider is required")
    private NotificationProvider provider;
    
    @Builder.Default
    private Boolean isEnabled = true;
    
    // SMTP fields
    @Size(max = 255, message = "SMTP host must not exceed 255 characters")
    private String smtpHost;
    
    private Integer smtpPort;
    
    @Size(max = 255, message = "SMTP username must not exceed 255 characters")
    private String smtpUsername;
    
    @Size(max = 500, message = "SMTP password must not exceed 500 characters")
    private String smtpPassword;
    
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "From email must not exceed 255 characters")
    private String fromEmail;
    
    @Size(max = 255, message = "From name must not exceed 255 characters")
    private String fromName;
    
    // SMS/WhatsApp fields
    @Size(max = 500, message = "API key must not exceed 500 characters")
    private String apiKey;
    
    @Size(max = 50, message = "From number must not exceed 50 characters")
    private String fromNumber;
    
    private Integer priority;
}

