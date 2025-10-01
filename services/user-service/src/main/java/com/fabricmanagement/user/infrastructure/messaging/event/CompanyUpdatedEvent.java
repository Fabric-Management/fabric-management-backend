package com.fabricmanagement.user.infrastructure.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Company Updated Event
 * 
 * Event published when a company is updated
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyUpdatedEvent {
    
    private UUID companyId;
    private UUID tenantId;
    private String name;
    private String status;
    private LocalDateTime timestamp;
}

