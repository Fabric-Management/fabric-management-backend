package com.fabricmanagement.company.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Duplicate Detection
 * 
 * Externalizes duplicate detection thresholds and settings
 * Follows PRINCIPLES.md - No magic numbers, configuration-driven
 */
@Configuration
@ConfigurationProperties(prefix = "company.duplicate-detection")
@Data
public class DuplicateDetectionConfig {
    
    /**
     * Similarity threshold for name matching (0.0 to 1.0)
     * Higher = more strict (fewer matches)
     * 
     * Recommended values:
     * - 0.90-1.00: Almost identical (typo detection)
     * - 0.80-0.90: Very similar (duplicate prevention)
     * - 0.50-0.80: Possibly similar (warnings)
     * - 0.30-0.50: Loosely similar (suggestions)
     */
    private double nameSimilarityThreshold = 0.80;
    
    /**
     * Minimum similarity for database trigram search
     * Used in PostgreSQL pg_trgm queries
     */
    private double databaseSearchThreshold = 0.30;
    
    /**
     * Maximum results for autocomplete
     */
    private int autocompleteMaxResults = 10;
    
    /**
     * Minimum query length for autocomplete
     */
    private int autocompleteMinLength = 2;
    
    /**
     * Minimum query length for fuzzy search
     */
    private int fuzzySearchMinLength = 3;
    
    /**
     * Whether to enable full-text search (can be disabled for performance)
     */
    private boolean enableFullTextSearch = true;
    
    /**
     * Whether to block creation on exact tax ID match
     */
    private boolean blockOnTaxIdMatch = true;
    
    /**
     * Whether to block creation on exact registration number match
     */
    private boolean blockOnRegistrationMatch = true;
}

