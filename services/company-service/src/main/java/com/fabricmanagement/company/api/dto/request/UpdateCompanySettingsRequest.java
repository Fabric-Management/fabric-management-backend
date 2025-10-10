package com.fabricmanagement.company.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Update Company Settings Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCompanySettingsRequest {
    
    private Map<String, Object> settings;
    
    private Map<String, Object> preferences;
    
    private String timezone;
    
    private String language;
    
    private String currency;
}

