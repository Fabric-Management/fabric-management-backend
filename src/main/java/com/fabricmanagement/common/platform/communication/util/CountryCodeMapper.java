package com.fabricmanagement.common.platform.communication.util;

import lombok.experimental.UtilityClass;

/**
 * Country Code Mapper Utility - Maps common country names to ISO 3166-1 alpha-2 codes.
 * 
 * <p><b>USAGE:</b> Only used in Geocoding fallback methods (searchByPostcode, validateByAddress).
 * Not used in autocomplete - Google's smart relevance handles country detection automatically.</p>
 * 
 * <p>Improves Google Geocoding API accuracy by using components parameter
 * instead of country name in address query.</p>
 */
@UtilityClass
public class CountryCodeMapper {

    /**
     * Map common country names to ISO 3166-1 alpha-2 codes.
     * 
     * @param countryName Country name (case-insensitive, will be uppercased)
     * @return ISO 3166-1 alpha-2 code or null if not found
     */
    public String mapToIsoCode(String countryName) {
        if (countryName == null || countryName.isBlank()) {
            return null;
        }

        String upper = countryName.toUpperCase();

        return switch (upper) {
            case "UNITED KINGDOM", "UK", "GREAT BRITAIN", "BRITAIN" -> "GB";
            case "UNITED STATES", "USA", "US", "AMERICA" -> "US";
            case "TURKEY", "TÜRKIYE", "TURKIYE" -> "TR";
            case "GERMANY", "DEUTSCHLAND" -> "DE";
            case "FRANCE" -> "FR";
            case "ITALY", "ITALIA" -> "IT";
            case "SPAIN", "ESPANA" -> "ES";
            case "NETHERLANDS", "HOLLAND" -> "NL";
            case "BELGIUM" -> "BE";
            case "SWITZERLAND", "SUISSE", "SCHWEIZ" -> "CH";
            case "AUSTRIA", "OSTERREICH" -> "AT";
            case "POLAND", "POLSKA" -> "PL";
            case "CZECH REPUBLIC", "CZECHIA" -> "CZ";
            case "SWEDEN", "SVERIGE" -> "SE";
            case "NORWAY", "NORGE" -> "NO";
            case "DENMARK", "DANMARK" -> "DK";
            case "FINLAND", "SUOMI" -> "FI";
            case "PORTUGAL" -> "PT";
            case "GREECE", "HELLAS" -> "GR";
            case "ROMANIA" -> "RO";
            case "BULGARIA" -> "BG";
            case "HUNGARY", "MAGYARORSZAG" -> "HU";
            case "CROATIA", "HRVATSKA" -> "HR";
            case "SLOVAKIA", "SLOVENSKO" -> "SK";
            case "SLOVENIA", "SLOVENIJA" -> "SI";
            case "IRELAND", "EIRE" -> "IE";
            case "AUSTRALIA" -> "AU";
            case "CANADA" -> "CA";
            case "NEW ZEALAND" -> "NZ";
            case "SOUTH AFRICA" -> "ZA";
            case "JAPAN", "NIHON" -> "JP";
            case "CHINA", "PEOPLE'S REPUBLIC OF CHINA" -> "CN";
            case "INDIA" -> "IN";
            case "BRAZIL", "BRASIL" -> "BR";
            case "MEXICO" -> "MX";
            case "ARGENTINA" -> "AR";
            case "CHILE" -> "CL";
            case "COLOMBIA" -> "CO";
            case "PERU" -> "PE";
            case "RUSSIA", "RUSSIAN FEDERATION" -> "RU";
            case "SOUTH KOREA", "KOREA", "REPUBLIC OF KOREA" -> "KR";
            case "SINGAPORE" -> "SG";
            case "MALAYSIA" -> "MY";
            case "THAILAND" -> "TH";
            case "INDONESIA" -> "ID";
            case "PHILIPPINES" -> "PH";
            case "VIETNAM", "VIET NAM" -> "VN";
            case "ISRAEL" -> "IL";
            case "SAUDI ARABIA" -> "SA";
            case "UNITED ARAB EMIRATES", "UAE" -> "AE";
            case "EGYPT" -> "EG";
            default -> null;
        };
    }
}

