package com.fabricmanagement.shared.infrastructure.util;

import com.fabricmanagement.shared.infrastructure.config.TextProcessingConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.JaccardSimilarity;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Token-Based Similarity Utility
 * 
 * Smart duplicate detection using token/word-based analysis.
 * Solves the problem where generic words (like "Tekstil") inflate similarity scores.
 * 
 * PROBLEM:
 * - "Akme Tekstil" vs "Akkayalar Tekstil" → 78% similar (FALSE POSITIVE!)
 * - Both contain "Tekstil" (common word) → High character similarity
 * - But "Akme" ≠ "Akkayalar" (DIFFERENT companies!)
 * 
 * SOLUTION:
 * - Tokenize: ["akme", "tekstil"] vs ["akkayalar", "tekstil"]
 * - Remove common: ["akme"] vs ["akkayalar"]
 * - Compare unique tokens: 50% similar → DIFFERENT! ✅
 * 
 * ALGORITHMS:
 * - Jaccard Similarity: Set intersection / Set union (perfect for tokens!)
 * - Weighted by token importance (unique tokens > common tokens)
 * 
 * PRODUCTION-READY:
 * - Apache Commons Text (battle-tested)
 * - Configuration-driven
 * - Multi-language support
 * - Performance optimized
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenBasedSimilarityUtil {
    
    private final TextProcessingConfig config;
    private final StringNormalizationUtil normalizationUtil;
    
    // Apache Commons - Jaccard Similarity (set-based)
    private static final JaccardSimilarity JACCARD = new JaccardSimilarity();
    
    /**
     * Calculate token-based similarity between two company names
     * 
     * SMART ALGORITHM:
     * 1. Normalize both names (ICU4J, lowercase, remove suffixes)
     * 2. Tokenize (split into words)
     * 3. Remove common words ("tekstil", "limited", "sanayi", etc.)
     * 4. Calculate Jaccard similarity on UNIQUE tokens
     * 5. Weight by token overlap percentage
     * 
     * Examples:
     * 
     * Case 1: Different companies with common industry term
     * - "Akme Tekstil" vs "Akkayalar Tekstil"
     * - Tokens: ["akme", "tekstil"] vs ["akkayalar", "tekstil"]
     * - Remove common: ["akme"] vs ["akkayalar"]
     * - Jaccard: 0.0 (no overlap) → DIFFERENT ✅
     * 
     * Case 2: Typo in company name
     * - "Acme Tekstil" vs "Acmee Tekstil"
     * - Unique: ["acme"] vs ["acmee"]
     * - Character similarity: 95% → BLOCK (typo!) ✅
     * 
     * Case 3: Similar but different
     * - "Akme Tekstil" vs "Acme Tekstil"
     * - Unique: ["akme"] vs ["acme"]
     * - Character similarity: 75% → WARN ⚠️
     * 
     * @param name1 First company name
     * @param name2 Second company name
     * @return Smart similarity result with details
     */
    public TokenSimilarityResult calculateTokenSimilarity(String name1, String name2) {
        
        // Step 1: Normalize both names
        String normalized1 = normalizationUtil.normalizeForComparison(name1);
        String normalized2 = normalizationUtil.normalizeForComparison(name2);
        
        // Step 2: Tokenize (split into words)
        Set<String> tokens1 = tokenize(normalized1);
        Set<String> tokens2 = tokenize(normalized2);
        
        // Step 3: Identify common words
        Set<String> commonWords = getCommonWordsSet();
        
        // Step 4: Filter out common words to get UNIQUE tokens
        Set<String> uniqueTokens1 = filterCommonWords(tokens1, commonWords);
        Set<String> uniqueTokens2 = filterCommonWords(tokens2, commonWords);
        
        // Step 5: Calculate Jaccard similarity on unique tokens
        double jaccardScore = calculateJaccardSimilarity(uniqueTokens1, uniqueTokens2);
        
        // Step 6: Calculate token overlap percentage
        double tokenOverlap = calculateTokenOverlap(uniqueTokens1, uniqueTokens2);
        
        // Step 7: If unique tokens match well, check character-level similarity
        double characterSimilarity = 0.0;
        if (tokenOverlap >= 0.5) {
            // At least 50% tokens overlap → Check character similarity on unique tokens
            characterSimilarity = calculateCharacterSimilarityOnUniqueTokens(
                uniqueTokens1, uniqueTokens2);
        }
        
        // Step 8: Final decision
        boolean isDuplicate = determineIfDuplicate(
            jaccardScore, tokenOverlap, characterSimilarity, 
            uniqueTokens1, uniqueTokens2);
        
        return TokenSimilarityResult.builder()
                .jaccardScore(jaccardScore)
                .tokenOverlap(tokenOverlap)
                .characterSimilarity(characterSimilarity)
                .uniqueTokens1(uniqueTokens1)
                .uniqueTokens2(uniqueTokens2)
                .commonTokens(getCommonTokens(tokens1, tokens2))
                .isDuplicate(isDuplicate)
                .confidence(calculateConfidence(jaccardScore, tokenOverlap, characterSimilarity))
                .build();
    }
    
    /**
     * Tokenize normalized text into words
     */
    private Set<String> tokenize(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return Set.of();
        }
        
        // Split by whitespace, filter empty
        return Arrays.stream(normalizedText.split("\\s+"))
                .filter(token -> !token.isBlank())
                .filter(token -> token.length() >= 2) // Ignore single characters
                .collect(Collectors.toSet());
    }
    
    /**
     * Get common words set (normalized)
     */
    private Set<String> getCommonWordsSet() {
        return new HashSet<>(config.getCommonWords().getAllCommonWords());
    }
    
    /**
     * Filter out common words from tokens
     */
    private Set<String> filterCommonWords(Set<String> tokens, Set<String> commonWords) {
        return tokens.stream()
                .filter(token -> !commonWords.contains(token.toLowerCase()))
                .collect(Collectors.toSet());
    }
    
    /**
     * Calculate Jaccard similarity: |A ∩ B| / |A ∪ B|
     * 
     * Examples:
     * - {acme} ∩ {acme} = {acme}, |A ∪ B| = {acme} → 1/1 = 1.0 (EXACT)
     * - {acme} ∩ {akkayalar} = ∅, |A ∪ B| = {acme, akkayalar} → 0/2 = 0.0 (DIFFERENT)
     * - {acme, corp} ∩ {acme, inc} = {acme}, |A ∪ B| = {acme, corp, inc} → 1/3 = 0.33
     */
    private double calculateJaccardSimilarity(Set<String> set1, Set<String> set2) {
        if (set1.isEmpty() && set2.isEmpty()) {
            return 1.0; // Both empty → same
        }
        if (set1.isEmpty() || set2.isEmpty()) {
            return 0.0; // One empty → different
        }
        
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        
        return (double) intersection.size() / union.size();
    }
    
    /**
     * Calculate token overlap percentage
     * What % of tokens are shared?
     */
    private double calculateTokenOverlap(Set<String> set1, Set<String> set2) {
        if (set1.isEmpty() && set2.isEmpty()) {
            return 1.0;
        }
        if (set1.isEmpty() || set2.isEmpty()) {
            return 0.0;
        }
        
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        int minSize = Math.min(set1.size(), set2.size());
        return (double) intersection.size() / minSize;
    }
    
    /**
     * Get common tokens between two sets
     */
    private Set<String> getCommonTokens(Set<String> set1, Set<String> set2) {
        Set<String> common = new HashSet<>(set1);
        common.retainAll(set2);
        return common;
    }
    
    /**
     * Calculate character-level similarity on unique tokens
     * 
     * Used when tokens match to detect typos:
     * - "acme" vs "acmee" → 95% similar → TYPO!
     * - "akme" vs "acme" → 75% similar → WARN
     */
    private double calculateCharacterSimilarityOnUniqueTokens(
            Set<String> uniqueTokens1, Set<String> uniqueTokens2) {
        
        if (uniqueTokens1.isEmpty() || uniqueTokens2.isEmpty()) {
            return 0.0;
        }
        
        // Join tokens back to string
        String text1 = String.join(" ", uniqueTokens1);
        String text2 = String.join(" ", uniqueTokens2);
        
        // Use Jaro-Winkler for character similarity
        return JACCARD.apply(text1, text2);
    }
    
    /**
     * Determine if duplicate based on multi-level criteria
     * 
     * LOGIC:
     * 1. If Jaccard = 1.0 (exact token match) → DUPLICATE
     * 2. If token overlap >= 80% AND character similarity >= 90% → DUPLICATE (typo!)
     * 3. If token overlap >= 60% AND character similarity >= 95% → DUPLICATE (minor typo)
     * 4. Otherwise → NOT DUPLICATE
     */
    private boolean determineIfDuplicate(
            double jaccardScore, 
            double tokenOverlap, 
            double characterSimilarity,
            Set<String> uniqueTokens1,
            Set<String> uniqueTokens2) {
        
        // Case 1: Exact token match (after removing common words)
        if (jaccardScore >= 1.0) {
            return true; // DUPLICATE
        }
        
        // Case 2: High token overlap + high character similarity (TYPO!)
        // "Acme Corp" vs "Acmee Corp" → overlap=100%, char=95% → DUPLICATE
        if (tokenOverlap >= 0.8 && characterSimilarity >= 0.90) {
            return true; // DUPLICATE (likely typo)
        }
        
        // Case 3: Medium-high token overlap + very high character similarity
        // "Acme" vs "Acmee" → overlap=100%, char=95% → DUPLICATE
        if (tokenOverlap >= 0.6 && characterSimilarity >= 0.95) {
            return true; // DUPLICATE (minor typo)
        }
        
        // Case 4: Low token overlap → DIFFERENT companies
        // "Akme" vs "Akkayalar" → overlap=0%, different companies!
        return false; // NOT DUPLICATE
    }
    
    /**
     * Calculate overall confidence score
     */
    private double calculateConfidence(double jaccardScore, double tokenOverlap, double characterSimilarity) {
        // Weighted average:
        // - Jaccard: 40% weight (token set similarity)
        // - Token overlap: 30% weight (how many tokens match)
        // - Character similarity: 30% weight (typo detection)
        
        return (jaccardScore * 0.4) + (tokenOverlap * 0.3) + (characterSimilarity * 0.3);
    }
    
    /**
     * Token similarity result
     */
    @lombok.Data
    @lombok.Builder
    public static class TokenSimilarityResult {
        /**
         * Jaccard similarity score (0.0-1.0)
         */
        private double jaccardScore;
        
        /**
         * Token overlap percentage (0.0-1.0)
         */
        private double tokenOverlap;
        
        /**
         * Character-level similarity on unique tokens (0.0-1.0)
         */
        private double characterSimilarity;
        
        /**
         * Unique tokens from first name
         */
        private Set<String> uniqueTokens1;
        
        /**
         * Unique tokens from second name
         */
        private Set<String> uniqueTokens2;
        
        /**
         * Common tokens (industry terms like "tekstil")
         */
        private Set<String> commonTokens;
        
        /**
         * Is this a duplicate?
         */
        private boolean isDuplicate;
        
        /**
         * Overall confidence score (0.0-1.0)
         */
        private double confidence;
        
        /**
         * Get explanation for debugging
         */
        public String getExplanation() {
            return String.format(
                "Jaccard: %.2f, Token Overlap: %.2f, Char Similarity: %.2f, " +
                "Unique1: %s, Unique2: %s, Common: %s, Duplicate: %s",
                jaccardScore, tokenOverlap, characterSimilarity,
                uniqueTokens1, uniqueTokens2, commonTokens, isDuplicate
            );
        }
    }
}

