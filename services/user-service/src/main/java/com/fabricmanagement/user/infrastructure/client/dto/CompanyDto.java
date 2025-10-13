package com.fabricmanagement.user.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDto {
    
    private UUID id;
    private UUID tenantId;
    private String name;
    private String legalName;
    private String taxId;
    private String registrationNumber;
    private String type;
    private String industry;
    private String status;
    private boolean isActive;
    private int maxUsers;
    private int currentUsers;
    private boolean isPlatform;
    private LocalDateTime createdAt;
}

