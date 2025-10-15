package com.fabricmanagement.notification.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Update Notification Config Request
 * 
 * All fields are optional (partial update).
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateNotificationConfigRequest {
    
    private Boolean isEnabled;
    
    // SMTP fields
    @Size(max = 255)
    private String smtpHost;
    
    private Integer smtpPort;
    
    @Size(max = 255)
    private String smtpUsername;
    
    @Size(max = 500)
    private String smtpPassword;
    
    @Email
    @Size(max = 255)
    private String fromEmail;
    
    @Size(max = 255)
    private String fromName;
    
    // SMS/WhatsApp fields
    @Size(max = 500)
    private String apiKey;
    
    @Size(max = 50)
    private String fromNumber;
    
    private Integer priority;
}

