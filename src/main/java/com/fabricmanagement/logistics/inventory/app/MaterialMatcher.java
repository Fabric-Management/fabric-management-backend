package com.fabricmanagement.logistics.inventory.app;

import com.fabricmanagement.production.masterdata.fiber.api.facade.FiberFacade;
import com.fabricmanagement.production.masterdata.material.dto.MaterialDto;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Material Matcher - Intelligent material name matching with fuzzy search.
 *
 * <p>Handles multi-word queries, synonyms, and partial matches.</p>
 * <p>Uses related entities (Fiber, Yarn, Fabric) for name-based matching.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MaterialMatcher {

    private final FiberFacade fiberFacade;
    // TODO: Add YarnFacade and FabricFacade when available

    /**
     * Material name synonyms/translations (Turkish ↔ English)
     */
    private static final Map<String, List<String>> MATERIAL_SYNONYMS = Map.of(
        "cotton", List.of("pamuk", "cotton fiber", "cotton fibre"),
        "pamuk", List.of("cotton", "cotton fiber", "cotton fibre"),
        "polyester", List.of("polyester fiber", "polyester fibre"),
        "wool", List.of("yün", "wool fiber"),
        "yün", List.of("wool", "wool fiber"),
        "gabardin", List.of("gabardine", "gabardeen"),
        "gabardine", List.of("gabardin", "gabardeen")
    );

    /**
     * Technical textile term patterns (regex) to preserve during matching.
     * These patterns should not be normalized or split into words.
     */
    private static final java.util.List<java.util.regex.Pattern> TECHNICAL_TERM_PATTERNS = java.util.List.of(
        java.util.regex.Pattern.compile("\\d+/\\d+"),                      // 30/1, 40/2
        java.util.regex.Pattern.compile("(?i)ne\\s*\\d+/\\d+"),            // Ne 20/1, ne 30/1
        java.util.regex.Pattern.compile("\\d+x\\d+"),                      // 16x12
        java.util.regex.Pattern.compile("\\d+\\s*gsm", java.util.regex.Pattern.CASE_INSENSITIVE), // 190 GSM
        java.util.regex.Pattern.compile("%\\d+"),                          // %100
        java.util.regex.Pattern.compile("\\d+['']?a\\s*bir", java.util.regex.Pattern.CASE_INSENSITIVE), // 30'a bir
        java.util.regex.Pattern.compile("\\d+['']?ye\\s*iki", java.util.regex.Pattern.CASE_INSENSITIVE)  // 40'ye iki
    );

    /**
     * Find best matching material(s) for a query.
     *
     * @param query user query (e.g., "cotton fiber", "pamuk")
     * @param materials list of all materials to search
     * @return list of matching materials, sorted by relevance (best first)
     */
    public List<MaterialDto> findMatches(String query, List<MaterialDto> materials) {
        if (query == null || query.isBlank() || materials == null || materials.isEmpty()) {
            return Collections.emptyList();
        }

        // Preserve technical terms - normalize only non-technical parts
        String normalizedQuery = normalizeQueryPreservingTechnicalTerms(query);
        
        // Expand query with synonyms (but preserve technical terms)
        Set<String> searchTerms = expandQuery(normalizedQuery);
        
        List<MatchResult> matches = new ArrayList<>();
        
        for (MaterialDto material : materials) {
            if (material.getUid() == null) continue;
            
            String materialUid = material.getUid().toLowerCase();
            
            // Get material name from related entity (Fiber, Yarn, Fabric)
            String materialName = getMaterialName(material);
            
            // Calculate score using both UID and material name
            int score = calculateMatchScore(normalizedQuery, searchTerms, materialUid, materialName);
            
            if (score > 0) {
                log.debug("Material match: uid={}, name={}, score={}, query={}", 
                    materialUid, materialName, score, normalizedQuery);
                matches.add(new MatchResult(material, score));
            }
        }
        
        log.debug("MaterialMatcher found {} matches for query '{}'", matches.size(), normalizedQuery);
        
        // Sort by score (highest first)
        matches.sort((a, b) -> Integer.compare(b.score, a.score));
        
        return matches.stream()
            .map(m -> m.material)
            .collect(Collectors.toList());
    }

    /**
     * Normalize query while preserving technical textile terms.
     * Converts to lowercase but keeps technical patterns intact.
     */
    private String normalizeQueryPreservingTechnicalTerms(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }

        // Check if query contains technical terms
        boolean hasTechnicalTerms = TECHNICAL_TERM_PATTERNS.stream()
            .anyMatch(pattern -> pattern.matcher(query).find());

        if (!hasTechnicalTerms) {
            // No technical terms - standard normalization
            return query.toLowerCase().trim();
        }

        // Has technical terms - preserve them during normalization
        // Strategy: Extract technical terms, normalize rest, recombine
        
        // Find first technical term match
        java.util.regex.Matcher techMatcher = null;
        int techStart = -1;
        int techEnd = -1;
        
        for (java.util.regex.Pattern pattern : TECHNICAL_TERM_PATTERNS) {
            java.util.regex.Matcher m = pattern.matcher(query);
            if (m.find()) {
                if (techMatcher == null || m.start() < techStart) {
                    techMatcher = m;
                    techStart = m.start();
                    techEnd = m.end();
                }
            }
        }
        
        if (techMatcher != null) {
            // Extract and preserve technical term
            String before = query.substring(0, techStart).toLowerCase().trim();
            String techTerm = query.substring(techStart, techEnd); // Preserve original case and formatting
            String after = query.substring(techEnd).toLowerCase().trim();
            
            StringBuilder normalized = new StringBuilder();
            if (!before.isEmpty()) {
                normalized.append(before);
                if (!techTerm.startsWith(" ")) {
                    normalized.append(" ");
                }
            }
            normalized.append(techTerm);
            if (!after.isEmpty() && !techTerm.endsWith(" ")) {
                normalized.append(" ");
            }
            normalized.append(after);
            
            return normalized.toString().trim();
        }
        
        // Fallback: standard normalization
        return query.toLowerCase().trim();
    }

    /**
     * Expand query with synonyms and word variations.
     * Technical terms are preserved and not expanded.
     */
    private Set<String> expandQuery(String query) {
        Set<String> terms = new HashSet<>();
        terms.add(query);
        
        // Check if word is a technical term (should not be expanded)
        java.util.function.Predicate<String> isTechnicalTerm = word -> 
            TECHNICAL_TERM_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(word).find());
        
        // Add individual words from query, preserving technical terms
        String[] words = query.split("\\s+");
        for (String word : words) {
            // Always add the word itself (preserves technical terms)
            terms.add(word);
            
            // Only expand synonyms for non-technical terms
            if (!isTechnicalTerm.test(word)) {
                // Add synonyms
                String wordLower = word.toLowerCase();
                if (MATERIAL_SYNONYMS.containsKey(wordLower)) {
                    terms.addAll(MATERIAL_SYNONYMS.get(wordLower));
                }
                
                // Reverse lookup (find synonyms containing this word)
                for (Map.Entry<String, List<String>> entry : MATERIAL_SYNONYMS.entrySet()) {
                    if (entry.getValue().contains(wordLower)) {
                        terms.add(entry.getKey());
                        terms.addAll(entry.getValue());
                    }
                }
            }
        }
        
        return terms;
    }

    /**
     * Get material name from related entity (Fiber, Yarn, Fabric).
     * Returns empty string if no related entity found.
     */
    private String getMaterialName(MaterialDto material) {
        if (material == null || material.getId() == null) {
            log.debug("Material or ID is null");
            return "";
        }

        // Check material type and fetch corresponding entity
        if (material.getMaterialType() == MaterialType.FIBER) {
            String fiberName = fiberFacade.findByMaterialId(material.getId())
                .map(fiber -> fiber.getFiberName() != null ? fiber.getFiberName().toLowerCase() : "")
                .orElse("");
            log.debug("Material {} (type={}): fiberName={}", material.getId(), material.getMaterialType(), fiberName);
            return fiberName;
        }
        
        // TODO: Add Yarn and Fabric support when available
        // if (material.getMaterialType() == MaterialType.YARN) {
        //     return yarnFacade.findByMaterialId(material.getId())
        //         .map(yarn -> yarn.getYarnName() != null ? yarn.getYarnName().toLowerCase() : "")
        //         .orElse("");
        // }
        // if (material.getMaterialType() == MaterialType.FABRIC) {
        //     return fabricFacade.findByMaterialId(material.getId())
        //         .map(fabric -> fabric.getFabricName() != null ? fabric.getFabricName().toLowerCase() : "")
        //         .orElse("");
        // }

        return "";
    }

    /**
     * Calculate match score for a material.
     * Higher score = better match
     *
     * @param query normalized user query
     * @param searchTerms expanded search terms (including synonyms)
     * @param materialUid material UID (lowercase)
     * @param materialName material name from related entity (lowercase, empty if not found)
     */
    private int calculateMatchScore(String query, Set<String> searchTerms, String materialUid, String materialName) {
        int score = 0;
        
        // Extract base name from UID (e.g., "MAT-001-COTTON" → "cotton")
        String baseName = extractBaseName(materialUid);
        
        // === Material Name Matching (Highest Priority) ===
        if (!materialName.isBlank()) {
            // Exact match with material name (e.g., "cotton" or "pamuk")
            if (materialName.equals(query)) {
                score += 200; // Highest priority
            }
            
            // Material name contains query
            if (materialName.contains(query)) {
                score += 150;
            }
            
            // Check each search term against material name
            for (String term : searchTerms) {
                if (materialName.equals(term)) {
                    score += 120;
                } else if (materialName.contains(term)) {
                    score += 80;
                }
            }
        }
        
        // === UID Matching (Fallback) ===
        // Exact match with material UID
        if (materialUid.equals(query)) {
            score += 100;
        }
        
        // Exact match with base name
        if (baseName.equals(query)) {
            score += 90;
        }
        
        // Contains exact query
        if (materialUid.contains(query)) {
            score += 50;
        }
        
        // Base name contains query
        if (baseName.contains(query)) {
            score += 45;
        }
        
        // Check each search term (including synonyms)
        for (String term : searchTerms) {
            // Exact match
            if (materialUid.equals(term)) {
                score += 40;
            } else if (baseName.equals(term)) {
                score += 38;
            }
            // Contains match
            else if (materialUid.contains(term)) {
                score += 20;
            } else if (baseName.contains(term)) {
                score += 18;
            }
        }
        
        // Check if all query words are present (for multi-word queries)
        String[] queryWords = query.split("\\s+");
        if (queryWords.length > 1) {
            boolean allWordsPresent = true;
            for (String word : queryWords) {
                boolean found = false;
                
                // Check material name first
                if (!materialName.isBlank()) {
                    if (materialName.contains(word)) {
                        found = true;
                    } else {
                        // Check synonyms in material name
                        Set<String> wordSynonyms = expandQuery(word);
                        for (String syn : wordSynonyms) {
                            if (materialName.contains(syn)) {
                                found = true;
                                break;
                            }
                        }
                    }
                }
                
                // Check UID/base name if not found in material name
                if (!found) {
                    if (materialUid.contains(word) || baseName.contains(word)) {
                        found = true;
                    } else {
                        // Check synonyms
                        Set<String> wordSynonyms = expandQuery(word);
                        for (String syn : wordSynonyms) {
                            if (materialUid.contains(syn) || baseName.contains(syn)) {
                                found = true;
                                break;
                            }
                        }
                    }
                }
                
                if (!found) {
                    allWordsPresent = false;
                    break;
                }
            }
            if (allWordsPresent) {
                score += 30;
            }
        }
        
        return score;
    }

    /**
     * Extract base name from material UID.
     * Examples:
     * - "MAT-001-COTTON" → "cotton"
     * - "COTTON" → "cotton"
     * - "cotton-fiber" → "cotton"
     */
    private String extractBaseName(String materialUid) {
        if (materialUid == null || materialUid.isBlank()) {
            return "";
        }
        
        // Remove common prefixes/suffixes (MAT-*, UID patterns)
        String base = materialUid.toLowerCase();
        
        // Remove UID pattern (e.g., "MAT-001-")
        base = base.replaceAll("^[a-z]+-\\d+-", "");
        base = base.replaceAll("^[a-z]+-", "");
        
        // Remove numeric suffixes
        base = base.replaceAll("-\\d+$", "");
        
        // Split by common separators and take meaningful parts
        String[] parts = base.split("[-_\\s]+");
        
        // Filter out common prefixes and short parts
        List<String> meaningfulParts = new ArrayList<>();
        for (String part : parts) {
            if (!part.matches("^[a-z]+$") || part.length() < 2) continue;
            // Skip common prefixes like "mat", "fiber", "fibre" and very short parts
            if (!part.equals("mat") && !part.equals("fiber") && !part.equals("fibre") && 
                part.length() >= 3) {
                meaningfulParts.add(part);
            }
        }
        
        // Return most meaningful part or joined parts
        if (meaningfulParts.isEmpty()) {
            return base;
        }
        
        // If single meaningful part, return it
        if (meaningfulParts.size() == 1) {
            return meaningfulParts.get(0);
        }
        
        // Return joined (e.g., "cotton fiber")
        return String.join(" ", meaningfulParts);
    }

    /**
     * Match result with score.
     */
    private static class MatchResult {
        final MaterialDto material;
        final int score;

        MatchResult(MaterialDto material, int score) {
            this.material = material;
            this.score = score;
        }
    }
}

