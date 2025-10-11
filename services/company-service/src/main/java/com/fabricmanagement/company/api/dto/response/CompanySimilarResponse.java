package com.fabricmanagement.company.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for similar company search using fuzzy matching
 * 
 * Returns companies with similar names based on PostgreSQL trigram similarity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanySimilarResponse {
    
    /**
     * List of similar companies with their similarity scores
     */
    private List<SimilarCompany> matches;
    
    /**
     * Total count of matches
     */
    private int totalCount;
    
    /**
     * Similarity threshold used for the search
     */
    private double threshold;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimilarCompany {
        private String id;
        private String name;
        private String legalName;
        private String taxId;
        private String type;
        private String industry;
        
        /**
         * Similarity score (0.0 to 1.0)
         * 1.0 = exact match, 0.8+ = very similar, 0.5-0.8 = possibly similar
         */
        private double similarityScore;
    }
}

