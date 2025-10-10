package com.fabricmanagement.company.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for duplicate check
 * 
 * Provides information about potential duplicate companies
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckDuplicateResponse {
    
    /**
     * Whether a potential duplicate was found
     */
    private boolean isDuplicate;
    
    /**
     * Type of match found
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
     * 1.0 = exact match, 0.8+ = very similar, 0.5-0.8 = possibly similar
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
    
    public static CheckDuplicateResponse noDuplicate() {
        return CheckDuplicateResponse.builder()
            .isDuplicate(false)
            .matchType("NONE")
            .confidence(0.0)
            .message("No duplicate found")
            .recommendation("You can proceed with creating this company")
            .build();
    }
}

