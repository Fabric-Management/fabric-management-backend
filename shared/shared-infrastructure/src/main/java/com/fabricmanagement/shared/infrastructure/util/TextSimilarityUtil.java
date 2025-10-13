package com.fabricmanagement.shared.infrastructure.util;

import com.fabricmanagement.shared.infrastructure.config.TextProcessingConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Component;

/**
 * Text Similarity Utility
 * 
 * Wrapper for Apache Commons Text similarity algorithms.
 * Provides configurable string matching for duplicate detection.
 * 
 * ALGORITHMS:
 * - Jaro-Winkler: Best for name matching (gives weight to prefixes)
 * - Levenshtein: Edit distance (character changes needed)
 * 
 * WHY JARO-WINKLER FOR COMPANY NAMES:
 * - Prefix matters: "Acme Tekstil" vs "Akme Tekstil" → High similarity
 * - Transposition friendly: "Acme" vs "Amce" → Detected
 * - Returns 0.0-1.0 score (easy to threshold)
 * - Faster than Levenshtein
 * 
 * PRODUCTION-READY:
 * - Configuration-driven
 * - Battle-tested (Apache Commons)
 * - Thread-safe
 * - Performance optimized
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TextSimilarityUtil {
    
    private final TextProcessingConfig config;
    private final StringNormalizationUtil normalizationUtil;
    
    // Apache Commons Text - Production-ready implementations
    private static final JaroWinklerSimilarity JARO_WINKLER = new JaroWinklerSimilarity();
    private static final LevenshteinDistance LEVENSHTEIN = new LevenshteinDistance();
    
    /**
     * Calculate similarity score between two texts
     * 
     * Automatically normalizes texts before comparison.
     * Returns score based on configured algorithm.
     * 
     * @param text1 First text
     * @param text2 Second text
     * @return Similarity score (0.0 = completely different, 1.0 = identical)
     */
    public double calculateSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return 0.0;
        }
        
        if (text1.equals(text2)) {
            return 1.0;
        }
        
        // Normalize both texts
        String normalized1 = normalizationUtil.normalizeForComparison(text1);
        String normalized2 = normalizationUtil.normalizeForComparison(text2);
        
        // If normalized texts are identical → 1.0 (exact match)
        if (normalized1.equals(normalized2)) {
            return 1.0;
        }
        
        // Calculate similarity based on algorithm
        String algorithm = config.getSimilarity().getAlgorithm();
        
        try {
            if ("LEVENSHTEIN".equalsIgnoreCase(algorithm)) {
                return calculateLevenshteinSimilarity(normalized1, normalized2);
            } else {
                // Default: Jaro-Winkler (recommended for names)
                return calculateJaroWinklerSimilarity(normalized1, normalized2);
            }
        } catch (Exception e) {
            log.error("Error calculating similarity for '{}' vs '{}': {}", 
                text1, text2, e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Jaro-Winkler similarity
     * 
     * Best for name matching. Gives higher score to strings that:
     * - Match from the beginning (prefix matching)
     * - Have similar character order
     * 
     * Score: 0.0 (completely different) to 1.0 (identical)
     * 
     * Examples:
     * - "Acme Tekstil" vs "Acme Textil" → ~0.98 (high)
     * - "Acme Tekstil" vs "Akme Tekstil" → ~0.95 (high)
     * - "Acme Tekstil" vs "Tekstil Acme" → ~0.75 (medium)
     * - "Acme Tekstil" vs "XYZ Company" → ~0.30 (low)
     */
    private double calculateJaroWinklerSimilarity(String text1, String text2) {
        return JARO_WINKLER.apply(text1, text2);
    }
    
    /**
     * Levenshtein distance converted to similarity
     * 
     * Calculates minimum edit operations needed.
     * Converts distance to 0.0-1.0 score.
     * 
     * Score = 1 - (distance / max_length)
     */
    private double calculateLevenshteinSimilarity(String text1, String text2) {
        int distance = LEVENSHTEIN.apply(text1, text2);
        int maxLength = Math.max(text1.length(), text2.length());
        
        if (maxLength == 0) {
            return 1.0;
        }
        
        return 1.0 - ((double) distance / maxLength);
    }
    
    /**
     * Check if two texts are similar based on threshold
     * 
     * @param text1 First text
     * @param text2 Second text
     * @param threshold Minimum similarity (0.0-1.0)
     * @return true if similarity >= threshold
     */
    public boolean areSimilar(String text1, String text2, double threshold) {
        double similarity = calculateSimilarity(text1, text2);
        return similarity >= threshold;
    }
    
    /**
     * Get similarity level category
     * 
     * @param similarity Similarity score (0.0-1.0)
     * @return Category: EXACT, VERY_HIGH, HIGH, MEDIUM, LOW, NONE
     */
    public SimilarityLevel getSimilarityLevel(double similarity) {
        if (similarity >= 1.0) {
            return SimilarityLevel.EXACT;
        } else if (similarity >= config.getSimilarity().getThresholdBlock()) {
            return SimilarityLevel.VERY_HIGH; // >0.90 → BLOCK
        } else if (similarity >= config.getSimilarity().getThresholdWarn()) {
            return SimilarityLevel.HIGH; // 0.70-0.90 → WARN
        } else if (similarity >= config.getSimilarity().getThresholdSuggest()) {
            return SimilarityLevel.MEDIUM; // 0.50-0.70 → SUGGEST
        } else if (similarity >= 0.30) {
            return SimilarityLevel.LOW;
        } else {
            return SimilarityLevel.NONE;
        }
    }
    
    /**
     * Determine action based on similarity score
     * 
     * @param similarity Similarity score
     * @return Recommended action
     */
    public DuplicateAction getRecommendedAction(double similarity) {
        SimilarityLevel level = getSimilarityLevel(similarity);
        
        switch (level) {
            case EXACT:
            case VERY_HIGH:
                return config.getSimilarity().isBlockOnHighSimilarity() 
                    ? DuplicateAction.BLOCK 
                    : DuplicateAction.WARN;
            case HIGH:
                return DuplicateAction.WARN;
            case MEDIUM:
                return DuplicateAction.SUGGEST;
            default:
                return DuplicateAction.ALLOW;
        }
    }
    
    /**
     * Similarity level categories
     */
    public enum SimilarityLevel {
        EXACT,      // 1.0 (identical)
        VERY_HIGH,  // 0.90-0.99 (almost identical, likely duplicate)
        HIGH,       // 0.70-0.89 (similar, possible duplicate)
        MEDIUM,     // 0.50-0.69 (somewhat similar, check recommended)
        LOW,        // 0.30-0.49 (loosely similar)
        NONE        // <0.30 (different)
    }
    
    /**
     * Recommended action based on similarity
     */
    public enum DuplicateAction {
        BLOCK,      // Prevent creation
        WARN,       // Show warning, allow with confirmation
        SUGGEST,    // Show suggestion
        ALLOW       // No action needed
    }
}

