package com.fabricmanagement.common.infrastructure.ai;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Normalizes user queries for AI search by translating Turkish fiber names to English. Provides
 * consistency across different domain adapters.
 */
@Component
public class AIQueryNormalizer {

  /**
   * Normalize fiber query by translating Turkish fiber names to English.
   *
   * <p><b>Purpose:</b> AI searches often use Turkish fiber names (e.g., "pamuk", "viskoz"), but
   * database stores English names (e.g., "cotton", "viscose"). This method translates Turkish
   * queries to English for better matching.
   *
   * @param query Original query (may contain Turkish fiber names)
   * @return Normalized query (Turkish names replaced with English)
   */
  public String normalizeFiberQuery(String query) {
    if (query == null || query.isBlank()) {
      return query;
    }

    String lowerQuery = query.toLowerCase().trim();

    // Turkish-to-English fiber name mapping
    // Using HashMap to avoid Map.of() 10-pair limit
    Map<String, String> translations = new HashMap<>();

    // Natural fibers
    translations.put("pamuk", "cotton");
    translations.put("cotton", "cotton");
    translations.put("yün", "wool");
    translations.put("wool", "wool");
    translations.put("keten", "linen");
    translations.put("linen", "linen");
    translations.put("ipek", "silk");
    translations.put("silk", "silk");
    translations.put("kenevir", "hemp");
    translations.put("hemp", "hemp");
    translations.put("bambu", "bamboo");
    translations.put("bamboo", "bamboo");
    translations.put("jüt", "jute");
    translations.put("jute", "jute");

    // Synthetic fibers
    translations.put("polyester", "polyester");
    translations.put("poliester", "polyester");
    translations.put("naylon", "nylon");
    translations.put("nylon", "nylon");
    translations.put("viscose", "viscose");
    translations.put("viskoz", "viscose");
    translations.put("viskon", "viscose");
    translations.put("rayon", "rayon");
    translations.put("modal", "modal");
    translations.put("lyocell", "lyocell");
    translations.put("tencel", "lyocell");
    translations.put("elastan", "elastane");
    translations.put("spandeks", "elastane");
    translations.put("elastane", "elastane");
    translations.put("polypropilen", "polypropylene");
    translations.put("polypropylene", "polypropylene");
    translations.put("polyetilen", "polyethylene");
    translations.put("polyethylene", "polyethylene");
    translations.put("akrilik", "acrylic");
    translations.put("acrylic", "acrylic");
    translations.put("polyamid", "polyamide");
    translations.put("polyamide", "polyamide");

    // Generic terms
    translations.put("materyal", "product");
    translations.put("materyali", "product");
    translations.put("elyaf", "fiber");
    translations.put("fiber", "fiber");

    // Check for exact matches first (longest match wins)
    String result = lowerQuery;
    for (Map.Entry<String, String> entry : translations.entrySet()) {
      if (result.contains(entry.getKey())) {
        // Replace Turkish word with English, but keep other parts of query
        result = result.replace(entry.getKey(), entry.getValue());
      }
    }

    // If translation occurred, return normalized; otherwise return original
    return result.equals(lowerQuery) ? query : result;
  }

  /**
   * Normalize a yarn search query without interpreting any yarn designation.
   *
   * <p>This method exists only to improve read-side search recall. Yarn-specific phrases are
   * replaced before composing {@link #normalizeFiberQuery(String)} so words such as {@code
   * elastanli} are not partially consumed by the fiber dictionary. Numeric and symbolic yarn
   * designations are deliberately left untouched and this method must never be used on a write
   * path.
   *
   * @param query original yarn search query
   * @return lower-case query with Turkish yarn and fiber words normalized to English
   */
  public String normalizeYarnQuery(String query) {
    if (query == null || query.isBlank()) {
      return query;
    }

    String normalized = query.toLowerCase().trim();
    Map<String, String> translations = new LinkedHashMap<>();

    // Longest and most specific phrases must be replaced before their component words.
    translations.put("hava jetli", "air jet");
    translations.put("open-end", "rotor");
    translations.put("tek kat", "single");
    translations.put("elastanlı", "core spun");
    translations.put("puntalı", "intermingled");
    translations.put("penye", "combed");
    translations.put("karde", "carded");
    translations.put("büküm", "twist");
    translations.put("katlı", "plied");
    translations.put("numara", "count");
    translations.put("kalın", "coarse");
    translations.put("ince", "fine");
    translations.put("iplik", "yarn");

    for (Map.Entry<String, String> entry : translations.entrySet()) {
      normalized = normalized.replace(entry.getKey(), entry.getValue());
    }

    return normalizeFiberQuery(normalized);
  }
}
