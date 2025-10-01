package com.fabricmanagement.user.infrastructure.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Company Created Event
 * 
 * Event published when a new company is created
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyCreatedEvent {
    
    private UUID companyId;
    private UUID tenantId;
    private String name;
    private String type;
    private String industry;
    private String status;
    private LocalDateTime timestamp;
}

