package com.fabricmanagement.user.infrastructure.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * 
 * @JsonIgnoreProperties - Production best practice for Kafka envelope pattern
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompanyUpdatedEvent {
    
    private UUID companyId;
    private UUID tenantId;
    private String name;
    private String status;
    private LocalDateTime timestamp;
}

