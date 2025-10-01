package com.fabricmanagement.user.infrastructure.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Company Deleted Event
 * 
 * Event published when a company is deleted
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDeletedEvent {
    
    private UUID companyId;
    private UUID tenantId;
    private LocalDateTime timestamp;
}

