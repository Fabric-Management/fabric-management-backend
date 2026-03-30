package com.fabricmanagement.common.infrastructure.ai;

import java.util.HashMap;
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
    translations.put("materyal", "material");
    translations.put("materyali", "material");
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
}
