package com.fabricmanagement.user.infrastructure.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * 
 * @JsonIgnoreProperties - Production best practice for event evolution
 * Ignores unknown fields from envelope pattern or future schema changes
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompanyCreatedEvent {
    
    private UUID companyId;
    private UUID tenantId;
    private String companyName;
    private String companyType;
    private String industry;
    private String status;
    private LocalDateTime timestamp;
}

