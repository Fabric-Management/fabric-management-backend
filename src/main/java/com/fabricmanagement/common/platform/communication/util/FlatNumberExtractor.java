package com.fabricmanagement.common.platform.communication.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Flat Number Extractor - Extracts flat/apartment numbers from formatted addresses when Google Maps
 * API doesn't provide subpremise component.
 *
 * <p>Uses region-specific patterns to extract flat numbers from formatted address strings. This is
 * a fallback mechanism for countries where Google doesn't reliably return subpremise.
 */
@Slf4j
@UtilityClass
public class FlatNumberExtractor {

  private static final List<FlatNumberPattern> PATTERNS = new ArrayList<>();

  static {
    // GLOBAL PATTERN (all countries) - PRIORITY: Check for combined format first (20/34, 20 34,
    // 20-34)
    // This pattern is used in many countries: Turkey, Germany, France, Italy, Spain, etc.
    PATTERNS.add(
        new FlatNumberPattern(
            null, // null = applies to all countries
            Pattern.compile(
                "(?i)\\b(\\d+[A-Za-z]?)\\s*[/\\s-]+\\s*(\\d+[A-Za-z]?)\\b",
                Pattern.CASE_INSENSITIVE),
            "Global combined format (20/34, 20 34, 20-34) - street+flat"));

    // Turkish-specific patterns
    PATTERNS.add(
        new FlatNumberPattern(
            List.of("TR"),
            Pattern.compile(
                "(?i)\\b(?:Kat|K|K\\.)\\s*(\\d+)\\s*(?:Daire|D|Da)\\s*(\\d+[A-Za-z]?)\\b",
                Pattern.CASE_INSENSITIVE),
            "Turkish (Kat 3 Daire 5)"));
    PATTERNS.add(
        new FlatNumberPattern(
            List.of("TR"),
            Pattern.compile(
                "(?i)\\b(?:Daire|D|Da|D\\.)\\s*:?\\s*(\\d+[A-Za-z]?)\\b", Pattern.CASE_INSENSITIVE),
            "Turkish (Daire:3, Daire 5) - flat number only"));

    // UK/Ireland patterns
    PATTERNS.add(
        new FlatNumberPattern(
            List.of("GB", "IE"),
            Pattern.compile(
                "(?i)\\b(?:Flat|Fl\\.?|Apt|Apartment|Unit|U\\.?)\\s*(?:No\\.?\\s*)?([A-Z]?\\d+[A-Za-z]?)\\b",
                Pattern.CASE_INSENSITIVE),
            "UK/Ireland (Flat 3, Apt 4B, Unit 101)"));

    // US/Canada patterns
    PATTERNS.add(
        new FlatNumberPattern(
            List.of("US", "CA"),
            Pattern.compile(
                "(?i)\\b(?:Apt|Apartment|Unit|U\\.?|Suite|Ste\\.?|#)\\s*(?:No\\.?\\s*)?([A-Z]?\\d+[A-Za-z]?)\\b",
                Pattern.CASE_INSENSITIVE),
            "US/Canada (Apt 4B, Unit 101, Suite 200)"));

    // Australia/New Zealand patterns
    PATTERNS.add(
        new FlatNumberPattern(
            List.of("AU", "NZ"),
            Pattern.compile(
                "(?i)\\b(?:Unit|U\\.?|Flat|Apt|Apartment)\\s*(?:No\\.?\\s*)?(\\d+[A-Za-z]?)\\b",
                Pattern.CASE_INSENSITIVE),
            "Australia/NZ (Unit 7, Flat 2A)"));

    // Singapore/Hong Kong patterns
    PATTERNS.add(
        new FlatNumberPattern(
            List.of("SG", "HK"),
            Pattern.compile(
                "(?i)(?:#|Flat|Unit)\\s*(\\d+-\\d+|[A-Z]\\d+[A-Za-z]?)\\b",
                Pattern.CASE_INSENSITIVE),
            "Singapore/Hong Kong (#10-05, Flat B)"));

    // German patterns
    PATTERNS.add(
        new FlatNumberPattern(
            List.of("DE", "AT", "CH"),
            Pattern.compile(
                "(?i)\\b(?:Wohnung|Whg\\.?|Apt|App\\.?)\\s*(\\d+[A-Za-z]?)\\b",
                Pattern.CASE_INSENSITIVE),
            "German (Wohnung 3, Whg. 5)"));

    // French patterns
    PATTERNS.add(
        new FlatNumberPattern(
            List.of("FR", "BE", "LU"),
            Pattern.compile(
                "(?i)\\b(?:Appartement|Apt\\.?|App\\.?|Étage|Etg\\.?)\\s*(?:N°|No\\.?)?\\s*(\\d+[A-Za-z]?)\\b",
                Pattern.CASE_INSENSITIVE),
            "French (Appartement 5B, Apt. N°12)"));

    // Spanish/Italian patterns
    PATTERNS.add(
        new FlatNumberPattern(
            List.of("ES", "IT", "PT"),
            Pattern.compile(
                "(?i)\\b(?:Piso|Planta|Apt\\.?|App\\.?|Piano)\\s*(?:N°|No\\.?)?\\s*(\\d+[A-Za-z]?)\\b",
                Pattern.CASE_INSENSITIVE),
            "Spanish/Italian (Piso 3, Apt. N°5)"));

    // Generic fallback patterns (for any country) - lower priority
    PATTERNS.add(
        new FlatNumberPattern(
            null, // null means applies to all countries
            Pattern.compile(
                "(?i)\\b(?:Flat|Apt|Apartment|Unit|Suite|#)\\s*(?:No\\.?\\s*)?([A-Z]?\\d+[A-Za-z]?)\\b",
                Pattern.CASE_INSENSITIVE),
            "Generic fallback (Flat, Apt, Unit, Suite)"));
    // Note: "No:XX" pattern removed from generic fallback - it's too ambiguous
  }

  /**
   * Extract flat number from formatted address string.
   *
   * @param formattedAddress Google's formatted address string
   * @param countryCode ISO 3166-1 alpha-2 country code (e.g., "TR", "GB", "US")
   * @return Extracted flat number, or null if not found
   */
  public static String extractFlatNumber(String formattedAddress, String countryCode) {
    if (formattedAddress == null || formattedAddress.isBlank()) {
      return null;
    }

    if (countryCode == null || countryCode.isBlank()) {
      countryCode = "";
    } else {
      countryCode = countryCode.toUpperCase().trim();
    }

    // Try country-specific patterns first
    for (FlatNumberPattern pattern : PATTERNS) {
      boolean matchesCountry =
          pattern.countryCodes == null
              || (countryCode != null
                  && !countryCode.isBlank()
                  && pattern.countryCodes.contains(countryCode));

      if (matchesCountry) {
        Matcher matcher = pattern.pattern.matcher(formattedAddress);
        if (matcher.find()) {
          String extracted = null;

          // Handle different group patterns
          if (matcher.groupCount() >= 2
              && matcher.group(2) != null
              && !matcher.group(2).isBlank()) {
            // Pattern with 2 groups: combined format (20/34) or Kat/Daire pattern
            String patternDesc = pattern.description.toLowerCase();
            if (patternDesc.contains("combined format")
                || patternDesc.contains("street+flat")
                || patternDesc.contains("20/34")
                || patternDesc.contains("global")) {
              // Combined format (20/34, 20 34, 20-34): second number is flat number
              // IMPORTANT: Only extract if it's a true combined format, not just any two numbers
              String firstNum = matcher.group(1);
              String secondNum = matcher.group(2);

              // Verify this is a valid flat number pattern:
              // - Both should be numbers (not postal codes or years)
              // - Second number should be reasonable (not too large, typically 1-999)
              if (isValidFlatNumber(firstNum, secondNum)) {
                extracted = secondNum;
              } else {
                log.debug(
                    "⚠️ Skipped invalid flat number pattern: '{}'/'{}' (country: {})",
                    firstNum,
                    secondNum,
                    countryCode);
                continue; // Try next pattern
              }
            } else {
              // Kat/Daire pattern: combine both
              extracted = matcher.group(1) + "/" + matcher.group(2);
            }
          } else if (matcher.groupCount() > 0) {
            extracted = matcher.group(1);
          } else {
            extracted = matcher.group(0);
          }

          if (extracted != null && !extracted.isBlank()) {
            log.debug(
                "✅ Extracted flat number '{}' from formatted address using pattern: {} (country: {})",
                extracted,
                pattern.description,
                countryCode);
            return extracted.trim();
          }
        }
      }
    }

    log.debug("⚠️ Could not extract flat number from formatted address: {}", formattedAddress);
    return null;
  }

  /**
   * Validate if two numbers form a valid street number + flat number combination.
   *
   * @param firstNum First number (street number)
   * @param secondNum Second number (potential flat number)
   * @return true if valid flat number pattern
   */
  private static boolean isValidFlatNumber(String firstNum, String secondNum) {
    try {
      // Remove any letter suffixes (e.g., "20A" -> 20)
      String firstClean = firstNum.replaceAll("[A-Za-z]", "");
      String secondClean = secondNum.replaceAll("[A-Za-z]", "");

      int first = Integer.parseInt(firstClean);
      int second = Integer.parseInt(secondClean);

      // Valid flat numbers are typically:
      // - Between 1 and 9999 (reasonable apartment/flat numbers - some buildings have 4-digit flat
      // numbers)
      // - Not equal to street number (would be ambiguous)
      // - Not too large (likely a postal code or year if > 9999)
      // - First number should be reasonable (not > 100000)
      return second >= 1 && second <= 9999 && first != second && first <= 100000 && second <= 9999;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /** Flat number pattern with country codes and description. */
  private static class FlatNumberPattern {
    final List<String> countryCodes;
    final Pattern pattern;
    final String description;

    FlatNumberPattern(List<String> countryCodes, Pattern pattern, String description) {
      this.countryCodes = countryCodes;
      this.pattern = pattern;
      this.description = description;
    }
  }
}
