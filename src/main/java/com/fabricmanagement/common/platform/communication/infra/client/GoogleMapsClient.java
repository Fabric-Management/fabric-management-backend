package com.fabricmanagement.common.platform.communication.infra.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fabricmanagement.common.platform.communication.config.GoogleMapsProperties;
import com.fabricmanagement.common.platform.communication.dto.AddressValidationResponse;
import com.fabricmanagement.common.platform.communication.dto.AutocompleteResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Google Maps Platform Client.
 *
 * <p>Handles communication with Google Places API and Geocoding API.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>✅ Places Autocomplete API (New)</li>
 *   <li>✅ Geocoding API (address validation)</li>
 *   <li>✅ Region bias (Europe, Turkey, UK)</li>
 *   <li>✅ Error handling & logging</li>
 * </ul>
 */
@Component
@Slf4j
public class GoogleMapsClient {

    private static final String PLACES_AUTOCOMPLETE_URL = "https://places.googleapis.com/v1/places:autocomplete";
    private static final String GEOCODING_API_URL = "https://maps.googleapis.com/maps/api/geocode/json";

    private final GoogleMapsProperties properties;
    private final RestTemplate restTemplate;

    public GoogleMapsClient(GoogleMapsProperties properties) {
        this.properties = properties;
        this.restTemplate = createRestTemplate();
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeout());
        factory.setReadTimeout(properties.getTimeout());
        return new RestTemplate(factory);
    }

    /**
     * Get autocomplete suggestions from Google Places API.
     */
    public AutocompleteResponse autocomplete(String input, String country) {
        if (!properties.getEnabled()) {
            log.warn("Google Maps features are disabled");
            return AutocompleteResponse.builder()
                .predictions(new ArrayList<>())
                .build();
        }

        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("Google Maps API key is not configured");
            return AutocompleteResponse.builder()
                .predictions(new ArrayList<>())
                .build();
        }

        try {
            log.debug("Requesting autocomplete: input={}, country={}", input, country);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Goog-Api-Key", properties.getApiKey());
            headers.set("X-Goog-FieldMask", "suggestions.placePrediction.placeId,suggestions.placePrediction.text");

            Map<String, Object> requestBody = new java.util.HashMap<>();
            requestBody.put("input", input);

            // Region bias
            if (properties.getRegionBias() != null && !properties.getRegionBias().isBlank()) {
                requestBody.put("includedRegionCodes", List.of(properties.getRegionBias().split(",")));
            }

            // Component restrictions
            if (country != null && !country.isBlank()) {
                Map<String, List<String>> components = new java.util.HashMap<>();
                components.put("country", List.of(country.toUpperCase()));
                requestBody.put("includedPrimaryTypes", List.of("street_address", "premise", "subpremise"));
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<PlacesAutocompleteResponse> response = restTemplate.exchange(
                PLACES_AUTOCOMPLETE_URL,
                HttpMethod.POST,
                request,
                PlacesAutocompleteResponse.class
            );

            PlacesAutocompleteResponse body = response.getBody();
            if (body != null && body.getSuggestions() != null) {
                List<AutocompleteResponse.AutocompletePrediction> predictions = body
                    .getSuggestions()
                    .stream()
                    .filter(s -> s.getPlacePrediction() != null)
                    .map(s -> {
                        PlacePrediction pred = s.getPlacePrediction();
                        String fullText = pred.getText() != null ? pred.getText().getFullText() : "";
                        String[] parts = fullText.split(",", 2);
                        return AutocompleteResponse.AutocompletePrediction.builder()
                            .placeId(pred.getPlaceId())
                            .description(fullText)
                            .mainText(parts.length > 0 ? parts[0].trim() : fullText)
                            .secondaryText(parts.length > 1 ? parts[1].trim() : "")
                            .build();
                    })
                    .toList();

                log.debug("Autocomplete returned {} suggestions", predictions.size());
                return AutocompleteResponse.builder()
                    .predictions(predictions)
                    .build();
            }

            return AutocompleteResponse.builder()
                .predictions(new ArrayList<>())
                .build();

        } catch (Exception e) {
            log.error("Error calling Google Places Autocomplete API: {}", e.getMessage(), e);
            // Re-throw IllegalStateException for API configuration errors (REQUEST_DENIED, etc.)
            if (e instanceof IllegalStateException) {
                throw e;
            }
            return AutocompleteResponse.builder()
                .predictions(new ArrayList<>())
                .build();
        }
    }

    /**
     * Validate address using Google Geocoding API by placeId (recommended).
     */
    public AddressValidationResponse validateByPlaceId(String placeId) {
        if (!properties.getEnabled()) {
            throw new IllegalStateException("Google Maps features are disabled");
        }

        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("Google Maps API key is not configured");
        }

        try {
            log.debug("Validating address by placeId: placeId={}", placeId);

            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(GEOCODING_API_URL)
                .queryParam("place_id", placeId)
                .queryParam("key", properties.getApiKey());

            ResponseEntity<GeocodingResponse> response = restTemplate.getForEntity(
                uriBuilder.toUriString(),
                GeocodingResponse.class
            );

            GeocodingResponse body = response.getBody();
            if (body != null && "OK".equals(body.getStatus()) && body.getResults() != null && !body.getResults().isEmpty()) {
                GeocodingResult result = body.getResults().get(0);
                return mapToValidationResponse(result);
            }

            return AddressValidationResponse.builder()
                .verificationStatus(AddressValidationResponse.VerificationStatus.FAILED)
                .errorMessage("Geocoding failed: " + (body != null ? body.getStatus() : "Unknown error"))
                .build();

        } catch (Exception e) {
            log.error("Error calling Google Geocoding API: {}", e.getMessage(), e);
            return AddressValidationResponse.builder()
                .verificationStatus(AddressValidationResponse.VerificationStatus.FAILED)
                .errorMessage("Validation error: " + e.getMessage())
                .build();
        }
    }

    /**
     * Search addresses by postcode using Google Geocoding API (Global).
     * 
     * <p>Returns all addresses matching the postcode globally or in specified country.</p>
     * <p><b>Global Search:</b> If country is not provided, searches globally across all countries.</p>
     * 
     * @param postcode Postal/ZIP code (required)
     * @param country Optional country code (ISO 3166-1 alpha-2, e.g., "TR", "GB", "US"). 
     *                If null, searches globally.
     * @return List of addresses matching the postcode
     */
    public List<AddressValidationResponse> searchByPostcode(String postcode, String country) {
        if (!properties.getEnabled()) {
            log.warn("Google Maps features are disabled");
            return new ArrayList<>();
        }

        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("Google Maps API key is not configured");
            return new ArrayList<>();
        }

        try {
            log.debug("Searching addresses by postcode (global): postcode={}, country={}", postcode, country);

            // Normalize postcode: trim whitespace, preserve UK format (with space)
            String normalizedPostcode = postcode != null ? postcode.trim() : "";
            if (normalizedPostcode.isBlank()) {
                log.warn("Postcode is blank, returning empty list");
                return new ArrayList<>();
            }

            // Normalize country: accept ISO code (e.g., "GB", "TR") or country name (e.g., "United Kingdom")
            String normalizedCountry = country != null ? country.trim() : null;
            String countryCode = null;
            
            if (normalizedCountry != null && !normalizedCountry.isBlank()) {
                String upper = normalizedCountry.toUpperCase();
                
                // If it's a 2-letter code (ISO 3166-1 alpha-2), use it as country code
                if (upper.length() == 2 && upper.matches("[A-Z]{2}")) {
                    countryCode = upper;
                } else {
                    // Map common country names to ISO codes for better Google API accuracy
                    countryCode = mapCountryNameToIsoCode(upper);
                }
            }

            // Build address query: postcode only (country handled via components parameter for better accuracy)
            // If we have ISO code, use components parameter instead of adding country to address query
            String addressQuery = normalizedPostcode;
            // Only add country to address query if we don't have ISO code (fallback)
            if (normalizedCountry != null && !normalizedCountry.isBlank() && countryCode == null) {
                // Fallback: use country name in address query if ISO code mapping failed
                addressQuery = normalizedPostcode + ", " + normalizedCountry;
            }

            log.debug("Google Geocoding query: address={}, country={}", addressQuery, normalizedCountry);

            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(GEOCODING_API_URL)
                .queryParam("address", addressQuery)
                .queryParam("key", properties.getApiKey());

            // Add region bias for better results (use ISO code if available, otherwise skip)
            if (countryCode != null && countryCode.length() == 2) {
                uriBuilder.queryParam("region", countryCode.toLowerCase());
            }
            
            // Add components restriction for better accuracy (only if we have ISO code)
            if (countryCode != null && countryCode.length() == 2) {
                uriBuilder.queryParam("components", "country:" + countryCode);
            }

            String finalUrl = uriBuilder.toUriString();
            log.debug("Google Geocoding API URL: {}", finalUrl.replace(properties.getApiKey(), "***"));

            ResponseEntity<GeocodingResponse> response = restTemplate.getForEntity(
                finalUrl,
                GeocodingResponse.class
            );

            GeocodingResponse body = response.getBody();
            
            if (body != null) {
                log.debug("Google Geocoding API response status: {}", body.getStatus());
                
                if ("OK".equals(body.getStatus()) && body.getResults() != null && !body.getResults().isEmpty()) {
                    List<AddressValidationResponse> results = body.getResults().stream()
                        .map(this::mapToValidationResponse)
                        .filter(r -> r.getVerificationStatus() != AddressValidationResponse.VerificationStatus.FAILED)
                        .toList();

                    log.info("Postcode search returned {} addresses for postcode: {}, country: {}", 
                        results.size(), normalizedPostcode, countryCode);
                    return results;
                } else if ("ZERO_RESULTS".equals(body.getStatus())) {
                    log.warn("No addresses found for postcode: {}, country: {} (ZERO_RESULTS)", 
                        normalizedPostcode, countryCode);
                } else if ("REQUEST_DENIED".equals(body.getStatus())) {
                    String errorMsg = "Google Maps API request denied. Please check API key configuration, enabled APIs (Geocoding API, Places API), and billing account.";
                    log.error("❌ Google Geocoding API REQUEST_DENIED for postcode: {}, country: {}. {}", 
                        normalizedPostcode, countryCode, errorMsg);
                    throw new IllegalStateException(errorMsg);
                } else if ("OVER_QUERY_LIMIT".equals(body.getStatus())) {
                    String errorMsg = "Google Maps API quota exceeded. Please check billing account and quota limits.";
                    log.error("❌ Google Geocoding API OVER_QUERY_LIMIT for postcode: {}, country: {}. {}", 
                        normalizedPostcode, countryCode, errorMsg);
                    throw new IllegalStateException(errorMsg);
                } else if ("INVALID_REQUEST".equals(body.getStatus())) {
                    String errorMsg = "Invalid request to Google Maps API. Please check postcode format.";
                    log.warn("⚠️ Google Geocoding API INVALID_REQUEST for postcode: {}, country: {}", 
                        normalizedPostcode, countryCode);
                    // Don't throw, just return empty list (invalid postcode is not a system error)
                } else {
                    log.warn("Google Geocoding API returned status: {} for postcode: {}, country: {}", 
                        body.getStatus(), normalizedPostcode, countryCode);
                }
            }

            log.debug("No addresses found for postcode: {} (global search)", normalizedPostcode);
            return new ArrayList<>();

        } catch (Exception e) {
            log.error("Error calling Google Geocoding API for postcode search: postcode={}, country={}, error={}", 
                postcode, country, e.getMessage(), e);
            return new ArrayList<>();
        }
    }


    /**
     * Validate address using Google Geocoding API by address string (fallback).
     */
    public AddressValidationResponse validateByAddress(String address) {
        if (!properties.getEnabled()) {
            throw new IllegalStateException("Google Maps features are disabled");
        }

        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("Google Maps API key is not configured");
        }

        try {
            log.debug("Validating address by string: address={}", address);

            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(GEOCODING_API_URL)
                .queryParam("address", address)
                .queryParam("key", properties.getApiKey());

            // Region bias
            if (properties.getRegionBias() != null && !properties.getRegionBias().isBlank()) {
                uriBuilder.queryParam("region", properties.getRegionBias().split(",")[0]);
            }

            ResponseEntity<GeocodingResponse> response = restTemplate.getForEntity(
                uriBuilder.toUriString(),
                GeocodingResponse.class
            );

            GeocodingResponse body = response.getBody();
            if (body != null && "OK".equals(body.getStatus()) && body.getResults() != null && !body.getResults().isEmpty()) {
                GeocodingResult result = body.getResults().get(0);
                return mapToValidationResponse(result);
            }

            return AddressValidationResponse.builder()
                .verificationStatus(AddressValidationResponse.VerificationStatus.FAILED)
                .errorMessage("Geocoding failed: " + (body != null ? body.getStatus() : "Unknown error"))
                .build();

        } catch (Exception e) {
            log.error("Error calling Google Geocoding API: {}", e.getMessage(), e);
            return AddressValidationResponse.builder()
                .verificationStatus(AddressValidationResponse.VerificationStatus.FAILED)
                .errorMessage("Validation error: " + e.getMessage())
                .build();
        }
    }

    private AddressValidationResponse mapToValidationResponse(GeocodingResult result) {
        AddressComponents components = extractAddressComponents(result);

        AddressValidationResponse.VerificationStatus status = determineVerificationStatus(components);

        return AddressValidationResponse.builder()
            .verificationStatus(status)
            .placeId(result.getPlaceId())
            .formattedAddress(result.getFormattedAddress())
            .streetAddress(components.getStreetAddress())
            .city(components.getCity())
            .state(components.getState())
            .district(components.getDistrict())
            .postalCode(components.getPostalCode())
            .country(components.getCountry())
            .countryCode(components.getCountryCode())
            .latitude(result.getGeometry().getLocation().getLat())
            .longitude(result.getGeometry().getLocation().getLng())
            .build();
    }

    private AddressComponents extractAddressComponents(GeocodingResult result) {
        AddressComponents components = new AddressComponents();

        for (AddressComponent comp : result.getAddressComponents()) {
            List<String> types = comp.getTypes();

            if (types.contains("street_number")) {
                String streetNumber = components.getStreetAddress() != null ? components.getStreetAddress() : "";
                components.setStreetAddress((streetNumber + " " + comp.getLongName()).trim());
            } else if (types.contains("route")) {
                String streetAddress = components.getStreetAddress() != null ? components.getStreetAddress() : "";
                components.setStreetAddress((streetAddress + " " + comp.getLongName()).trim());
            } else if (types.contains("locality")) {
                components.setCity(comp.getLongName());
            } else if (types.contains("administrative_area_level_1")) {
                components.setState(comp.getShortName());
            } else if (types.contains("administrative_area_level_2")) {
                components.setDistrict(comp.getLongName());
            } else if (types.contains("postal_code")) {
                components.setPostalCode(comp.getLongName());
            } else if (types.contains("country")) {
                components.setCountry(comp.getLongName());
                components.setCountryCode(comp.getShortName());
            }
        }

        return components;
    }

    private AddressValidationResponse.VerificationStatus determineVerificationStatus(AddressComponents components) {
        boolean hasStreet = components.getStreetAddress() != null && !components.getStreetAddress().isBlank();
        boolean hasCity = components.getCity() != null && !components.getCity().isBlank();
        boolean hasCountry = components.getCountryCode() != null && !components.getCountryCode().isBlank();

        if (hasStreet && hasCity && hasCountry) {
            return AddressValidationResponse.VerificationStatus.VERIFIED;
        } else if (hasCity && hasCountry) {
            return AddressValidationResponse.VerificationStatus.PARTIAL;
        } else {
            return AddressValidationResponse.VerificationStatus.FAILED;
        }
    }

    // Google API Response DTOs

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class PlacesAutocompleteResponse {
        @JsonProperty("suggestions")
        private List<Suggestion> suggestions;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Suggestion {
        @JsonProperty("placePrediction")
        private PlacePrediction placePrediction;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class PlacePrediction {
        @JsonProperty("placeId")
        private String placeId;

        @JsonProperty("text")
        private Text text;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Text {
        @JsonProperty("fullText")
        private String fullText;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GeocodingResponse {
        @JsonProperty("status")
        private String status;

        @JsonProperty("results")
        private List<GeocodingResult> results;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GeocodingResult {
        @JsonProperty("place_id")
        private String placeId;

        @JsonProperty("formatted_address")
        private String formattedAddress;

        @JsonProperty("address_components")
        private List<AddressComponent> addressComponents;

        @JsonProperty("geometry")
        private Geometry geometry;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AddressComponent {
        @JsonProperty("long_name")
        private String longName;

        @JsonProperty("short_name")
        private String shortName;

        @JsonProperty("types")
        private List<String> types;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Geometry {
        @JsonProperty("location")
        private Location location;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Location {
        @JsonProperty("lat")
        private Double lat;

        @JsonProperty("lng")
        private Double lng;
    }

    /**
     * Map common country names to ISO 3166-1 alpha-2 codes.
     * 
     * <p>Improves Google Geocoding API accuracy by using components parameter
     * instead of country name in address query.</p>
     * 
     * @param countryName Country name (case-insensitive, will be uppercased)
     * @return ISO 3166-1 alpha-2 code or null if not found
     */
    private String mapCountryNameToIsoCode(String countryName) {
        if (countryName == null || countryName.isBlank()) {
            return null;
        }
        
        String upper = countryName.toUpperCase();
        
        // Common country name mappings (most frequently used in address forms)
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
            default -> null; // Unknown country name, will use as-is in address query
        };
    }

    @Data
    static class AddressComponents {
        private String streetAddress;
        private String city;
        private String state;
        private String district;
        private String postalCode;
        private String country;
        private String countryCode;
    }
}

