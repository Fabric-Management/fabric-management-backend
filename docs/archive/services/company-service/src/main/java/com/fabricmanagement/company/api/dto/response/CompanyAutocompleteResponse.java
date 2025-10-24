package com.fabricmanagement.company.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for company autocomplete/search-as-you-type
 * 
 * Used in frontend for showing suggestions while user types
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyAutocompleteResponse {
    
    /**
     * List of matching companies
     */
    private List<CompanySuggestion> suggestions;
    
    /**
     * Total count of matches
     */
    private int totalCount;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanySuggestion {
        private String id;
        private String name;
        private String legalName;
        private String taxId;
        private String type;
        private String industry;
    }
}

