package com.fabricmanagement.platform.communication.util;

import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * Address Region Mapper - Maps country codes to address region groups.
 *
 * <p>Address structures vary by region. This mapper categorizes countries into 6 main groups based
 * on Google Maps API address component patterns and UPU (Universal Postal Union) standards.
 *
 * <p><b>Region Groups:</b>
 *
 * <ul>
 *   <li>WESTERN_EUROPE: UK, Ireland, France, Germany, etc.
 *   <li>EASTERN_EUROPE: Turkey, Romania, Bulgaria, Greece, etc.
 *   <li>NORTH_AMERICA: USA, Canada, Mexico
 *   <li>ASIA_PACIFIC: Japan, China, South Korea, India, etc.
 *   <li>LATIN_AMERICA: Brazil, Argentina, Chile, Colombia, etc.
 *   <li>MIDDLE_EAST_AFRICA: Saudi Arabia, UAE, Israel, Egypt, etc.
 * </ul>
 */
@UtilityClass
public class AddressRegionMapper {

  private static final Set<String> WESTERN_EUROPE =
      Set.of(
          "GB", "IE", "FR", "DE", "BE", "NL", "CH", "LU", "AT", "IT", "ES", "PT", "SE", "NO", "DK",
          "FI", "IS", "LI", "MC");

  private static final Set<String> EASTERN_EUROPE =
      Set.of(
          "TR", "RO", "BG", "GR", "RS", "HR", "BA", "ME", "AL", "MK", "SI", "SK", "CZ", "HU", "PL",
          "EE", "LV", "LT");

  private static final Set<String> NORTH_AMERICA = Set.of("US", "CA", "MX");

  private static final Set<String> ASIA_PACIFIC =
      Set.of(
          "JP", "CN", "KR", "SG", "TH", "ID", "IN", "MY", "PH", "VN", "TW", "HK", "AU", "NZ", "BD",
          "PK", "LK", "MM");

  private static final Set<String> LATIN_AMERICA =
      Set.of(
          "BR", "AR", "CL", "CO", "PE", "UY", "PY", "BO", "EC", "VE", "CR", "PA", "GT", "HN", "NI",
          "SV", "DO", "CU");

  private static final Set<String> MIDDLE_EAST_AFRICA =
      Set.of(
          "SA", "AE", "IL", "EG", "ZA", "NG", "MA", "DZ", "TN", "LY", "JO", "LB", "KW", "QA", "BH",
          "OM", "YE", "IQ");

  /**
   * Get address region group for a country code.
   *
   * @param countryCode ISO 3166-1 alpha-2 country code (e.g., "TR", "GB", "US")
   * @return Address region group, or null if not found
   */
  public static AddressRegion getRegionByCountryCode(String countryCode) {
    if (countryCode == null || countryCode.isBlank()) {
      return null;
    }

    String upper = countryCode.toUpperCase().trim();

    if (WESTERN_EUROPE.contains(upper)) {
      return AddressRegion.WESTERN_EUROPE;
    } else if (EASTERN_EUROPE.contains(upper)) {
      return AddressRegion.EASTERN_EUROPE;
    } else if (NORTH_AMERICA.contains(upper)) {
      return AddressRegion.NORTH_AMERICA;
    } else if (ASIA_PACIFIC.contains(upper)) {
      return AddressRegion.ASIA_PACIFIC;
    } else if (LATIN_AMERICA.contains(upper)) {
      return AddressRegion.LATIN_AMERICA;
    } else if (MIDDLE_EAST_AFRICA.contains(upper)) {
      return AddressRegion.MIDDLE_EAST_AFRICA;
    }

    return null;
  }

  /** Address region groups. */
  public enum AddressRegion {
    WESTERN_EUROPE,
    EASTERN_EUROPE,
    NORTH_AMERICA,
    ASIA_PACIFIC,
    LATIN_AMERICA,
    MIDDLE_EAST_AFRICA
  }
}
