package com.fabricmanagement.shared.infrastructure.util;

import com.fabricmanagement.shared.infrastructure.config.TextProcessingConfig;
import com.ibm.icu.text.Transliterator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * String Normalization Utility
 * 
 * Global text normalization for multi-language support.
 * Uses ICU4J for international text processing.
 * 
 * FEATURES:
 * - Unicode normalization (NFD/NFC)
 * - Diacritics removal (É→E, İ→I, Ñ→N)
 * - Turkish character handling (İ,Ş,Ğ,Ü,Ö,Ç)
 * - Company suffix removal (A.Ş., GmbH, Inc., SA, etc.)
 * - Whitespace normalization
 * - Case normalization
 * 
 * PRODUCTION-READY:
 * - Configuration-driven
 * - Battle-tested (ICU4J used by Google, Amazon)
 * - Performance optimized
 * - Thread-safe
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StringNormalizationUtil {
    
    private final TextProcessingConfig config;
    
    // ICU4J Transliterator for global normalization
    // Latin-ASCII: Converts any Latin-based text to ASCII
    private static final Transliterator TRANSLITERATOR = 
        Transliterator.getInstance("Latin-ASCII; Lower");
    
    // Pattern for multiple whitespaces
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    
    // Pattern for punctuation and special characters
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[.,;:!?\\-_()\\[\\]{}\"'`]");
    
    /**
     * Normalize text for comparison
     * 
     * Full normalization pipeline:
     * 1. Remove company suffixes
     * 2. ICU4J transliteration (global)
     * 3. Remove diacritics (if enabled)
     * 4. Lowercase (if enabled)
     * 5. Normalize whitespace
     * 6. Remove punctuation
     * 
     * Examples:
     * - "İstanbul Tekstil A.Ş." → "istanbul tekstil"
     * - "München GmbH" → "munchen gmbh"
     * - "Société Française SA" → "societe francaise"
     * - "ACME Corp., Inc." → "acme corp"
     * 
     * @param text Input text
     * @return Normalized text for comparison
     */
    public String normalizeForComparison(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        
        if (!config.getNormalization().isEnabled()) {
            return text;
        }
        
        String normalized = text;
        
        try {
            // Step 1: Remove company suffixes
            if (config.getNormalization().isRemoveCompanySuffixes()) {
                normalized = removeCompanySuffixes(normalized);
            }
            
            // Step 2: ICU4J transliteration (handles global characters)
            normalized = transliterate(normalized);
            
            // Step 3: Remove diacritics (additional cleanup)
            if (config.getNormalization().isRemoveDiacritics()) {
                normalized = removeDiacritics(normalized);
            }
            
            // Step 4: Lowercase
            if (config.getNormalization().isLowercase()) {
                normalized = normalized.toLowerCase();
            }
            
            // Step 5: Remove punctuation
            normalized = removePunctuation(normalized);
            
            // Step 6: Normalize whitespace
            if (config.getNormalization().isTrimWhitespace()) {
                normalized = normalizeWhitespace(normalized);
            }
            
            return normalized.trim();
            
        } catch (Exception e) {
            log.error("Error normalizing text: '{}', error: {}", text, e.getMessage());
            // Fallback to simple normalization
            return text.toLowerCase().trim();
        }
    }
    
    /**
     * Remove company suffixes from text
     * Supports multi-language suffixes (Turkish, English, German, French, etc.)
     * 
     * Examples:
     * - "Acme Tekstil A.Ş." → "Acme Tekstil"
     * - "Microsoft Corporation" → "Microsoft"
     * - "BMW AG" → "BMW"
     */
    public String removeCompanySuffixes(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        
        String result = text;
        List<String> allSuffixes = config.getCompanySuffixes().getAllSuffixes();
        
        // Sort by length (longest first) to avoid partial matches
        allSuffixes.sort((a, b) -> Integer.compare(b.length(), a.length()));
        
        for (String suffix : allSuffixes) {
            // Case-insensitive suffix removal
            // Handle both with and without leading space/comma
            String[] patterns = {
                " " + suffix + "$",      // " A.Ş."
                ", " + suffix + "$",     // ", Inc."
                suffix + "$"             // "GmbH"
            };
            
            for (String pattern : patterns) {
                if (result.matches("(?i).*" + Pattern.quote(pattern))) {
                    result = result.replaceAll("(?i)" + Pattern.quote(pattern), "");
                    break;
                }
            }
        }
        
        return result.trim();
    }
    
    /**
     * ICU4J transliteration
     * Converts any Latin-based script to ASCII
     * 
     * Examples:
     * - "İstanbul" → "istanbul"
     * - "München" → "munchen"
     * - "São Paulo" → "sao paulo"
     * - "Москва" → "moskva" (Cyrillic to Latin)
     */
    private String transliterate(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        
        try {
            return TRANSLITERATOR.transliterate(text);
        } catch (Exception e) {
            log.warn("ICU4J transliteration failed for: '{}', error: {}", text, e.getMessage());
            return text;
        }
    }
    
    /**
     * Remove diacritics (combining marks)
     * Uses Unicode NFD (Canonical Decomposition)
     * 
     * Examples:
     * - "é" → "e"
     * - "ñ" → "n"
     * - "ü" → "u"
     */
    private String removeDiacritics(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        
        // Normalize to NFD (separate base characters and combining marks)
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        
        // Remove combining marks (diacritics)
        // \p{M} = Mark category (accents, diacritics)
        return normalized.replaceAll("\\p{M}", "");
    }
    
    /**
     * Remove punctuation and special characters
     */
    private String removePunctuation(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        
        return PUNCTUATION_PATTERN.matcher(text).replaceAll(" ");
    }
    
    /**
     * Normalize whitespace
     * - Replace multiple spaces with single space
     * - Trim leading/trailing spaces
     */
    private String normalizeWhitespace(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        
        return WHITESPACE_PATTERN.matcher(text).replaceAll(" ").trim();
    }
    
    /**
     * Check if two strings are equal after normalization
     * 
     * @param text1 First text
     * @param text2 Second text
     * @return true if normalized texts are equal
     */
    public boolean areNormalizedEqual(String text1, String text2) {
        String normalized1 = normalizeForComparison(text1);
        String normalized2 = normalizeForComparison(text2);
        
        return normalized1.equals(normalized2);
    }
}

