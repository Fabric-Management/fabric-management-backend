package com.fabricmanagement.shared.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * Text Processing Configuration
 * 
 * Global configuration for text normalization, similarity matching, and masking.
 * Supports multi-language company name processing.
 * 
 * PRINCIPLES:
 * - Configuration-driven (no hardcoded values)
 * - Multi-language support (global market ready)
 * - Production-ready defaults
 */
@Configuration
@ConfigurationProperties(prefix = "text-processing")
@Data
public class TextProcessingConfig {
    
    /**
     * Normalization settings
     */
    private NormalizationSettings normalization = new NormalizationSettings();
    
    /**
     * Similarity matching settings
     */
    private SimilaritySettings similarity = new SimilaritySettings();
    
    /**
     * Masking/privacy settings
     */
    private MaskingSettings masking = new MaskingSettings();
    
    /**
     * Company suffix settings (multi-language)
     */
    private CompanySuffixSettings companySuffixes = new CompanySuffixSettings();
    
    @Data
    public static class NormalizationSettings {
        /**
         * Enable/disable normalization
         */
        private boolean enabled = true;
        
        /**
         * ICU4J locale for transliteration
         */
        private String locale = "en-US";
        
        /**
         * Remove diacritics (É→E, Ñ→N, İ→I)
         */
        private boolean removeDiacritics = true;
        
        /**
         * Convert to lowercase
         */
        private boolean lowercase = true;
        
        /**
         * Trim and normalize whitespace
         */
        private boolean trimWhitespace = true;
        
        /**
         * Remove company suffixes before comparison
         */
        private boolean removeCompanySuffixes = true;
    }
    
    @Data
    public static class SimilaritySettings {
        /**
         * Algorithm: JARO_WINKLER (recommended) or LEVENSHTEIN
         */
        private String algorithm = "JARO_WINKLER";
        
        /**
         * Similarity threshold to BLOCK creation (0.0-1.0)
         * >0.90 = Very similar → BLOCK
         */
        private double thresholdBlock = 0.90;
        
        /**
         * Similarity threshold to WARN (0.0-1.0)
         * 0.70-0.90 = Similar → WARN
         */
        private double thresholdWarn = 0.70;
        
        /**
         * Similarity threshold to SUGGEST (0.0-1.0)
         * 0.50-0.70 = Possibly similar → SUGGEST
         */
        private double thresholdSuggest = 0.50;
        
        /**
         * Block creation on exact match (after normalization)
         */
        private boolean blockOnExactMatch = true;
        
        /**
         * Block creation on high similarity (>thresholdBlock)
         */
        private boolean blockOnHighSimilarity = true;
        
        /**
         * Enable email domain matching
         */
        private boolean enableEmailDomainCheck = true;
        
        /**
         * Email domain match → WARN only (not block)
         */
        private boolean emailDomainWarningOnly = true;
    }
    
    @Data
    public static class MaskingSettings {
        /**
         * Number of visible characters in email username
         * "admin@example.com" → "a***n@example.com" (2 visible)
         */
        private int emailVisibleChars = 2;
        
        /**
         * Number of visible digits in phone number
         * "+905551234567" → "+90555***4567" (4 last digits)
         */
        private int phoneVisibleDigits = 4;
        
        /**
         * Masking character
         */
        private String maskingChar = "*";
        
        /**
         * Minimum masked characters (even for short strings)
         */
        private int minimumMaskedChars = 3;
    }
    
    @Data
    public static class CommonWordsSettings {
        /**
         * Common words to filter out during token-based matching
         * These are generic industry/business terms that shouldn't affect similarity
         */
        
        /**
         * Turkish common words (textile industry)
         */
        private List<String> turkish = Arrays.asList(
            "tekstil", "kumaş", "fabric", "sanayi", "ticaret", "pazarlama",
            "ithalat", "ihracat", "yapi", "insaat", "makina", "otomotiv",
            "gida", "tarim", "enerji", "teknoloji", "yazilim", "danismanlik",
            "limited", "anonim", "sirket", "şirket", "kollektif"
        );
        
        /**
         * English common words
         */
        private List<String> english = Arrays.asList(
            "textile", "fabric", "manufacturing", "industry", "trade", "trading",
            "import", "export", "limited", "corporation", "company", "enterprises",
            "international", "global", "group", "holdings", "partners",
            "solutions", "services", "systems", "technologies", "software"
        );
        
        /**
         * German common words
         */
        private List<String> german = Arrays.asList(
            "textil", "gewebe", "industrie", "handel", "handels",
            "gesellschaft", "unternehmen", "holding", "gruppe"
        );
        
        /**
         * French common words
         */
        private List<String> french = Arrays.asList(
            "textile", "tissu", "industrie", "commerce", "international",
            "societe", "société", "entreprise", "groupe", "holding"
        );
        
        /**
         * Spanish common words
         */
        private List<String> spanish = Arrays.asList(
            "textil", "tejido", "industria", "comercio", "internacional",
            "empresa", "compania", "compañia", "grupo", "holding"
        );
        
        /**
         * Italian common words
         */
        private List<String> italian = Arrays.asList(
            "tessile", "tessuto", "industria", "commercio", "internazionale",
            "societa", "società", "azienda", "gruppo", "holding"
        );
        
        /**
         * Get all common words (merged and normalized)
         */
        public List<String> getAllCommonWords() {
            List<String> all = new java.util.ArrayList<>();
            all.addAll(turkish);
            all.addAll(english);
            all.addAll(german);
            all.addAll(french);
            all.addAll(spanish);
            all.addAll(italian);
            // Normalize to lowercase
            return all.stream()
                    .map(String::toLowerCase)
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());
        }
    }
    
    /**
     * Common words configuration
     */
    private CommonWordsSettings commonWords = new CommonWordsSettings();
    
    @Data
    public static class CompanySuffixSettings {
        /**
         * Turkish company suffixes
         */
        private List<String> turkish = Arrays.asList(
            "A.Ş.", "A.S.", "AŞ", "AS",
            "Ltd.", "LTD.",
            "Şti.", "Sti.", "ŞTİ.", "STİ.",
            "Ltd. Şti.", "Ltd Şti", "LTD. ŞTİ.", "LTD ŞTİ"
        );
        
        /**
         * English company suffixes
         */
        private List<String> english = Arrays.asList(
            "Inc.", "INC.", "Inc",
            "LLC", "L.L.C.", "L.L.C",
            "Ltd.", "LTD.", "Ltd",
            "Corp.", "CORP.", "Corp",
            "Corporation", "CORPORATION",
            "Co.", "CO.", "Co"
        );
        
        /**
         * German company suffixes
         */
        private List<String> german = Arrays.asList(
            "GmbH", "GMBH",
            "AG",
            "KG",
            "UG"
        );
        
        /**
         * French company suffixes
         */
        private List<String> french = Arrays.asList(
            "SA", "S.A.",
            "SARL", "S.A.R.L.",
            "SAS", "S.A.S.",
            "SNC", "S.N.C."
        );
        
        /**
         * Spanish company suffixes
         */
        private List<String> spanish = Arrays.asList(
            "S.A.", "SA",
            "S.L.", "SL",
            "S.C.", "SC"
        );
        
        /**
         * Italian company suffixes
         */
        private List<String> italian = Arrays.asList(
            "S.p.A.", "SpA", "SPA",
            "S.r.l.", "Srl", "SRL"
        );
        
        /**
         * Get all suffixes (merged)
         */
        public List<String> getAllSuffixes() {
            List<String> all = new java.util.ArrayList<>();
            all.addAll(turkish);
            all.addAll(english);
            all.addAll(german);
            all.addAll(french);
            all.addAll(spanish);
            all.addAll(italian);
            return all;
        }
    }
}

