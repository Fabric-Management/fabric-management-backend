package com.fabricmanagement.user.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of company duplicate check
 * Maps to CompanyService's CheckDuplicateResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDuplicateCheckResult {
    
    /**
     * Whether a potential duplicate was found
     */
    private boolean isDuplicate;
    
    /**
     * Type of match found (TAX_ID, REGISTRATION_NUMBER, NAME_EXACT, NAME_SIMILAR, etc.)
     */
    private String matchType;
    
    /**
     * ID of the matched company (if found)
     */
    private String matchedCompanyId;
    
    /**
     * Name of the matched company (if found)
     */
    private String matchedCompanyName;
    
    /**
     * Tax ID of the matched company (if available)
     */
    private String matchedTaxId;
    
    /**
     * Confidence score (0.0 to 1.0)
     */
    private double confidence;
    
    /**
     * User-friendly message explaining the match
     */
    private String message;
    
    /**
     * Recommended action
     */
    private String recommendation;
}

