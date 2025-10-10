package com.fabricmanagement.company.domain.service;

import com.fabricmanagement.company.domain.aggregate.Company;
import com.fabricmanagement.company.infrastructure.config.DuplicateDetectionConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Domain Service for Duplicate Detection
 * 
 * Prevents duplicate companies using multiple strategies:
 * 1. Exact match on tax ID and registration number
 * 2. Fuzzy matching on company names (Jaro-Winkler similarity)
 * 
 * Best Practice: Domain service for complex business logic that doesn't belong in entity
 * Configuration-driven: Thresholds are externalized in application.yml (NO MAGIC NUMBERS!)
 */
@Service
@RequiredArgsConstructor
public class DuplicateCheckService {

    private final DuplicateDetectionConfig config;
    
    // ✅ USE INDUSTRY STANDARD LIBRARY (Apache Commons Text)
    // NO NEED TO IMPLEMENT ALGORITHMS OURSELVES!
    private final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();

    /**
     * Check for potential duplicates
     * 
     * @param tenantId Tenant context
     * @param name Company name
     * @param taxId Tax identification number
     * @param registrationNumber Registration number
     * @param existingCompanies List of companies to check against
     * @return Result with duplicate information
     */
    public DuplicateCheckResult checkForDuplicates(
            UUID tenantId,
            String name,
            String taxId,
            String registrationNumber,
            List<Company> existingCompanies) {

        if (existingCompanies == null || existingCompanies.isEmpty()) {
            return DuplicateCheckResult.noDuplicate();
        }

        DuplicateCheckResult.DuplicateCheckResultBuilder resultBuilder = 
            DuplicateCheckResult.builder()
                .isDuplicate(false);

        // 1. Check exact match on Tax ID (highest priority)
        if (taxId != null && !taxId.trim().isEmpty()) {
            for (Company company : existingCompanies) {
                if (taxId.equalsIgnoreCase(company.getTaxId())) {
                    return resultBuilder
                        .isDuplicate(true)
                        .matchType(MatchType.EXACT_TAX_ID)
                        .matchedCompanyId(company.getId())
                        .matchedCompanyName(company.getName().getValue())
                        .confidence(1.0)
                        .message("Company with same Tax ID already exists")
                        .build();
                }
            }
        }

        // 2. Check exact match on Registration Number
        if (registrationNumber != null && !registrationNumber.trim().isEmpty()) {
            for (Company company : existingCompanies) {
                if (registrationNumber.equalsIgnoreCase(company.getRegistrationNumber())) {
                    return resultBuilder
                        .isDuplicate(true)
                        .matchType(MatchType.EXACT_REGISTRATION)
                        .matchedCompanyId(company.getId())
                        .matchedCompanyName(company.getName().getValue())
                        .confidence(1.0)
                        .message("Company with same Registration Number already exists")
                        .build();
                }
            }
        }

        // 3. Check fuzzy match on company name
        String normalizedInputName = normalizeName(name);
        double highestSimilarity = 0.0;
        Company mostSimilarCompany = null;

        for (Company company : existingCompanies) {
            String companyName = normalizeName(company.getName().getValue());
            double similarity = calculateSimilarity(normalizedInputName, companyName);

            if (similarity > highestSimilarity) {
                highestSimilarity = similarity;
                mostSimilarCompany = company;
            }

            // Also check legal name if available
            if (company.getLegalName() != null) {
                String legalName = normalizeName(company.getLegalName());
                double legalNameSimilarity = calculateSimilarity(normalizedInputName, legalName);
                
                if (legalNameSimilarity > highestSimilarity) {
                    highestSimilarity = legalNameSimilarity;
                    mostSimilarCompany = company;
                }
            }
        }

        // If high similarity found, flag as potential duplicate
        // Threshold is configured in application.yml (NO MAGIC NUMBERS!)
        double threshold = config.getNameSimilarityThreshold();
        
        if (highestSimilarity >= threshold && mostSimilarCompany != null) {
            return resultBuilder
                .isDuplicate(true)
                .matchType(MatchType.FUZZY_NAME)
                .matchedCompanyId(mostSimilarCompany.getId())
                .matchedCompanyName(mostSimilarCompany.getName().getValue())
                .confidence(highestSimilarity)
                .message(String.format("Similar company name found (%.0f%% match)", highestSimilarity * 100))
                .build();
        }

        return DuplicateCheckResult.noDuplicate();
    }

    /**
     * Calculate similarity between two strings using Apache Commons Jaro-Winkler
     * Returns value between 0.0 (completely different) and 1.0 (identical)
     * 
     * ✅ BEST PRACTICE: Use industry-standard library instead of custom implementation
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }

        if (s1.equals(s2)) {
            return 1.0;
        }

        // Apache Commons Text - Industry Standard!
        return jaroWinkler.apply(s1, s2);
    }

    /**
     * Normalize company name for comparison
     * - Convert to lowercase
     * - Remove extra whitespace
     * - Remove common suffixes (Ltd, Inc, A.Ş., etc.)
     * - Remove special characters
     */
    private String normalizeName(String name) {
        if (name == null) {
            return "";
        }

        String normalized = name.toLowerCase().trim();

        // Remove common company suffixes for better matching
        String[] suffixes = {
            " a.ş.", " a.s.", " ltd.", " ltd", " inc.", " inc", 
            " llc", " corporation", " corp.", " corp", " co.", " co",
            " limited", " san. ve tic.", " sanayi", " ticaret"
        };

        for (String suffix : suffixes) {
            if (normalized.endsWith(suffix)) {
                normalized = normalized.substring(0, normalized.length() - suffix.length()).trim();
            }
        }

        // Remove extra whitespace
        normalized = normalized.replaceAll("\\s+", " ");

        // Remove special characters but keep letters, numbers, and spaces
        normalized = normalized.replaceAll("[^\\p{L}\\p{N}\\s]", "");

        return normalized;
    }

    /**
     * Result of duplicate check
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DuplicateCheckResult {
        private boolean isDuplicate;
        private MatchType matchType;
        private UUID matchedCompanyId;
        private String matchedCompanyName;
        private double confidence; // 0.0 to 1.0
        private String message;

        public static DuplicateCheckResult noDuplicate() {
            return DuplicateCheckResult.builder()
                .isDuplicate(false)
                .confidence(0.0)
                .build();
        }
    }

    /**
     * Type of duplicate match found
     */
    public enum MatchType {
        EXACT_TAX_ID,           // Exact match on tax ID (highest priority)
        EXACT_REGISTRATION,     // Exact match on registration number
        FUZZY_NAME,            // Fuzzy match on company name
        NONE                   // No match
    }
}

