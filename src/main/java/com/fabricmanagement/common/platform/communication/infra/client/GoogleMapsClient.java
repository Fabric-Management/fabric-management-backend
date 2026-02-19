package com.fabricmanagement.common.platform.communication.infra.client;

import com.fabricmanagement.common.platform.communication.config.GoogleMapsProperties;
import com.fabricmanagement.common.platform.communication.dto.AddressValidationResponse;
import com.fabricmanagement.common.platform.communication.dto.AutocompleteResponse;
import com.fabricmanagement.common.platform.communication.infra.client.googlemaps.response.AddressComponents;
import com.fabricmanagement.common.platform.communication.infra.client.googlemaps.response.GeocodingResponse;
import com.fabricmanagement.common.platform.communication.infra.client.googlemaps.response.PlaceDetailsResponse;
import com.fabricmanagement.common.platform.communication.infra.client.googlemaps.response.PlacesAutocompleteResponse;
import com.fabricmanagement.common.platform.communication.util.AddressComponentMapper;
import com.fabricmanagement.common.platform.communication.util.CountryCodeMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

/**
 * Google Maps Platform Client.
 *
 * <p>Handles communication with Google Places API and Geocoding API.
 *
 * <p><b>Features:</b>
 *
 * <ul>
 *   <li>✅ Places Autocomplete API (New)
 *   <li>✅ Geocoding API (address validation)
 *   <li>✅ Region bias (Europe, Turkey, UK)
 *   <li>✅ Error handling & logging
 * </ul>
 */
@Component
@Slf4j
public class GoogleMapsClient {

  private static final String PLACES_AUTOCOMPLETE_URL =
      "https://places.googleapis.com/v1/places:autocomplete";
  private static final String PLACES_DETAILS_URL =
      "https://places.googleapis.com/v1/places/"; // {placeId}
  private static final String GEOCODING_API_URL =
      "https://maps.googleapis.com/maps/api/geocode/json";

  private final GoogleMapsProperties properties;
  private final RestTemplate restTemplate;
  private final AddressComponentMapper addressComponentMapper;

  public GoogleMapsClient(GoogleMapsProperties properties) {
    this.properties = properties;
    this.restTemplate = createRestTemplate();
    this.addressComponentMapper = new AddressComponentMapper();
  }

  private RestTemplate createRestTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.getTimeout());
    factory.setReadTimeout(properties.getTimeout());
    return new RestTemplate(factory);
  }

  /**
   * Get autocomplete suggestions from Google Places API.
   *
   * <p>User-friendly: No country filter - Google's smart relevance handles country detection
   * automatically. This allows users to search globally without restrictions (e.g., "Akkayalar
   * London" works from anywhere).
   *
   * @param input Address input text (required)
   * @param country Optional country code (deprecated - not used, kept for backward compatibility)
   */
  public AutocompleteResponse autocomplete(String input, String country) {
    if (!properties.getEnabled()) {
      log.warn("Google Maps features are disabled");
      return AutocompleteResponse.builder().predictions(new ArrayList<>()).build();
    }

    if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
      log.warn("Google Maps API key is not configured");
      return AutocompleteResponse.builder().predictions(new ArrayList<>()).build();
    }

    try {
      log.debug("Requesting autocomplete: input={}", input);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("X-Goog-Api-Key", properties.getApiKey());
      // FieldMask: Request only the fields we need (required for New API v1)
      headers.set(
          "X-Goog-FieldMask",
          "suggestions.placePrediction.placeId,suggestions.placePrediction.text");

      Map<String, Object> requestBody = new java.util.HashMap<>();
      requestBody.put("input", input);

      // Note: Google Places API (New) v1 doesn't support maxResultCount parameter
      // API returns results based on relevance (typically 5-10 suggestions)
      // For what3words-style behavior, we rely on API's default result set

      // Region bias (from properties) - soft preference, doesn't restrict results
      // Google's smart relevance will still return relevant results from other regions
      if (properties.getRegionBias() != null && !properties.getRegionBias().isBlank()) {
        requestBody.put("includedRegionCodes", List.of(properties.getRegionBias().split(",")));
      }

      // Note: Country filter removed - Google's smart relevance handles country detection
      // automatically
      // This allows users to search globally (e.g., "Akkayalar London" works from anywhere)

      // Include comprehensive address types: buildings, businesses, streets, landmarks
      // Google API limit: Maximum 5 included_primary_types
      // This captures: street addresses, building names, businesses, POIs, routes, establishments
      // While filtering out: countries, continents, overly general results
      requestBody.put(
          "includedPrimaryTypes",
          List.of(
              "street_address", // Normal addresses (e.g., 10 Downing St)
              "premise", // Building or apartment names (e.g., Akkayalar Plaza) - includes
              // subpremise
              "point_of_interest", // Known businesses or landmarks (e.g., Starbucks, Big Ben)
              "route", // Street or avenue names
              "establishment" // General business or building category
              ));

      HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

      ResponseEntity<String> rawResponse =
          restTemplate.exchange(PLACES_AUTOCOMPLETE_URL, HttpMethod.POST, request, String.class);

      // Parse JSON response
      PlacesAutocompleteResponse body = null;
      try {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper();
        body = mapper.readValue(rawResponse.getBody(), PlacesAutocompleteResponse.class);
      } catch (Exception e) {
        log.error("Failed to parse Google Places API response: {}", e.getMessage(), e);
        return AutocompleteResponse.builder().predictions(new ArrayList<>()).build();
      }

      if (body == null || body.getSuggestions() == null || body.getSuggestions().isEmpty()) {
        return AutocompleteResponse.builder().predictions(new ArrayList<>()).build();
      }

      if (!body.getSuggestions().isEmpty()) {
        List<AutocompleteResponse.AutocompletePrediction> predictions =
            body.getSuggestions().stream()
                .filter(s -> s.getPlacePrediction() != null)
                .filter(
                    s -> {
                      PlacesAutocompleteResponse.PlacePrediction pred = s.getPlacePrediction();
                      return pred.getText() != null && pred.getText().getFullText() != null;
                    })
                .map(
                    s -> {
                      PlacesAutocompleteResponse.PlacePrediction pred = s.getPlacePrediction();
                      String fullText = pred.getText().getFullText();
                      String[] parts = fullText.split(",", 2);
                      return AutocompleteResponse.AutocompletePrediction.builder()
                          .placeId(pred.getPlaceId())
                          .description(fullText)
                          .mainText(parts.length > 0 ? parts[0].trim() : fullText)
                          .secondaryText(parts.length > 1 ? parts[1].trim() : "")
                          .build();
                    })
                .toList();

        return AutocompleteResponse.builder().predictions(predictions).build();
      }

      return AutocompleteResponse.builder().predictions(new ArrayList<>()).build();

    } catch (Exception e) {
      log.error("Error calling Google Places Autocomplete API: {}", e.getMessage(), e);
      // Re-throw IllegalStateException for API configuration errors (REQUEST_DENIED, etc.)
      if (e instanceof IllegalStateException) {
        throw e;
      }
      return AutocompleteResponse.builder().predictions(new ArrayList<>()).build();
    }
  }

  /**
   * Validate address using Google Places API (New) v1 Place Details API by placeId (recommended).
   *
   * <p>Uses the new Places API (New) v1 which provides more detailed address information including
   * flat numbers, apartment names, and better structured address components.
   *
   * @param placeId Google Places ID
   * @param originalInput Original input from autocomplete (optional, used for flat number
   *     extraction)
   */
  public AddressValidationResponse validateByPlaceId(String placeId, String originalInput) {
    if (!properties.getEnabled()) {
      throw new IllegalStateException("Google Maps features are disabled");
    }

    if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
      throw new IllegalStateException("Google Maps API key is not configured");
    }

    try {
      log.debug("Validating address by placeId using Places API (New) v1: placeId={}", placeId);

      // New Places API (New) v1 Place Details endpoint
      String url = PLACES_DETAILS_URL + placeId;

      HttpHeaders headers = new HttpHeaders();
      headers.set("X-Goog-Api-Key", properties.getApiKey());
      // Request detailed address components, formatted address, location (lat/lng), and place ID
      // Note: Google Places API (New) v1 uses "location" directly (not "geometry.location")
      headers.set(
          "X-Goog-FieldMask",
          "id,formattedAddress,addressComponents,location.latitude,location.longitude");

      HttpEntity<Void> request = new HttpEntity<>(headers);

      ResponseEntity<String> rawResponse =
          restTemplate.exchange(url, HttpMethod.GET, request, String.class);

      // Parse JSON response
      PlaceDetailsResponse placeDetails = null;
      try {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper();
        placeDetails = mapper.readValue(rawResponse.getBody(), PlaceDetailsResponse.class);

        // Debug: Log raw Google API response for field mapping verification
        log.debug("🔍 Google Places API (New) v1 Raw Response:");
        log.debug("  - placeId: {}", placeDetails.getId());
        log.debug("  - formattedAddress: {}", placeDetails.getFormattedAddress());
        if (placeDetails.getAddressComponents() != null) {
          log.debug("  - addressComponents count: {}", placeDetails.getAddressComponents().size());
          for (PlaceDetailsResponse.PlaceAddressComponent comp :
              placeDetails.getAddressComponents()) {
            log.debug(
                "    → types: {}, longText: '{}', shortText: '{}'",
                comp.getTypes(),
                comp.getLongText(),
                comp.getShortText());
          }
        }
        if (placeDetails.getLocation() != null) {
          log.debug(
              "  - location: lat={}, lng={}",
              placeDetails.getLocation().getLatitude(),
              placeDetails.getLocation().getLongitude());
        }
      } catch (Exception e) {
        log.error("Failed to parse Places API (New) v1 response: {}", e.getMessage(), e);
        return AddressValidationResponse.builder()
            .verificationStatus(AddressValidationResponse.VerificationStatus.FAILED)
            .errorMessage("Failed to parse address details: " + e.getMessage())
            .build();
      }

      // placeDetails cannot be null here - Jackson readValue either throws exception or returns
      // object
      AddressValidationResponse response =
          mapPlaceDetailsToValidationResponse(placeDetails, originalInput);

      // Debug: Log mapped response for domain field verification
      log.debug("🔍 Mapped AddressValidationResponse → Domain Fields:");
      log.debug("  - streetAddress: '{}' → Address.streetAddress", response.getStreetAddress());
      log.debug("  - flatNumber: '{}' → (not in domain, only in DTO)", response.getFlatNumber());
      log.debug("  - city: '{}' → Address.city", response.getCity());
      log.debug("  - state: '{}' → Address.state", response.getState());
      log.debug("  - district: '{}' → Address.district", response.getDistrict());
      log.debug("  - postalCode: '{}' → Address.postalCode", response.getPostalCode());
      log.debug("  - country: '{}' → Address.country", response.getCountry());
      log.debug("  - countryCode: '{}' → Address.countryCode", response.getCountryCode());
      log.debug("  - latitude: {} → Address.latitude", response.getLatitude());
      log.debug("  - longitude: {} → Address.longitude", response.getLongitude());
      log.debug("  - placeId: '{}' → Address.placeId", response.getPlaceId());
      log.debug(
          "  - formattedAddress: '{}' → Address.formattedAddress", response.getFormattedAddress());

      return response;

    } catch (org.springframework.web.client.HttpClientErrorException e) {
      log.error(
          "Error calling Google Places API (New) v1: status={}, body={}",
          e.getStatusCode(),
          e.getResponseBodyAsString());

      // Handle API errors
      if (e.getStatusCode().value() == 400) {
        return AddressValidationResponse.builder()
            .verificationStatus(AddressValidationResponse.VerificationStatus.FAILED)
            .errorMessage("Invalid placeId: " + placeId)
            .build();
      } else if (e.getStatusCode().value() == 403) {
        throw new IllegalStateException(
            "Google Places API access denied. Please check API key configuration and enabled APIs.");
      }

      return AddressValidationResponse.builder()
          .verificationStatus(AddressValidationResponse.VerificationStatus.FAILED)
          .errorMessage("Places API error: " + e.getMessage())
          .build();
    } catch (Exception e) {
      log.error("Error calling Google Places API (New) v1: {}", e.getMessage(), e);
      return AddressValidationResponse.builder()
          .verificationStatus(AddressValidationResponse.VerificationStatus.FAILED)
          .errorMessage("Validation error: " + e.getMessage())
          .build();
    }
  }

  /**
   * Search addresses by postcode using Google Geocoding API (Global).
   *
   * <p>Returns all addresses matching the postcode globally or in specified country.
   *
   * <p><b>Global Search:</b> If country is not provided, searches globally across all countries.
   *
   * @param postcode Postal/ZIP code (required)
   * @param country Optional country code (ISO 3166-1 alpha-2, e.g., "TR", "GB", "US"). If null,
   *     searches globally.
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
      log.debug(
          "Searching addresses by postcode (global): postcode={}, country={}", postcode, country);

      // Normalize postcode: trim whitespace, normalize multiple spaces to single space
      // UK postcodes can have space (MK5 7GE) or no space (MK57GE) - normalize to single space
      String normalizedPostcode = postcode != null ? postcode.trim().replaceAll("\\s+", " ") : "";
      if (normalizedPostcode.isBlank()) {
        log.warn("Postcode is blank, returning empty list");
        return new ArrayList<>();
      }

      // Normalize country: accept ISO code (e.g., "GB", "TR") or country name (e.g., "United
      // Kingdom")
      String normalizedCountry = country != null ? country.trim() : null;
      String countryCode = null;

      if (normalizedCountry != null && !normalizedCountry.isBlank()) {
        String upper = normalizedCountry.toUpperCase();

        // If it's a 2-letter code (ISO 3166-1 alpha-2), use it as country code
        if (upper.length() == 2 && upper.matches("[A-Z]{2}")) {
          countryCode = upper;
        } else {
          // Map common country names to ISO codes for better Google API accuracy
          countryCode = CountryCodeMapper.mapToIsoCode(upper);
        }
      }

      // Build address query: postcode only (country handled via components parameter for better
      // accuracy)
      // If we have ISO code, use components parameter instead of adding country to address query
      String addressQuery = normalizedPostcode;
      // Only add country to address query if we don't have ISO code (fallback)
      if (normalizedCountry != null && !normalizedCountry.isBlank() && countryCode == null) {
        // Fallback: use country name in address query if ISO code mapping failed
        addressQuery = normalizedPostcode + ", " + normalizedCountry;
      }

      log.debug("Google Geocoding query: address={}, country={}", addressQuery, normalizedCountry);

      UriComponentsBuilder uriBuilder =
          UriComponentsBuilder.fromHttpUrl(GEOCODING_API_URL)
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

      ResponseEntity<GeocodingResponse> response =
          restTemplate.getForEntity(finalUrl, GeocodingResponse.class);

      GeocodingResponse body = response.getBody();

      if (body != null) {
        log.debug("Google Geocoding API response status: {}", body.getStatus());

        if ("OK".equals(body.getStatus())
            && body.getResults() != null
            && !body.getResults().isEmpty()) {
          List<AddressValidationResponse> results =
              body.getResults().stream()
                  .map(this::mapToValidationResponse)
                  .filter(
                      r ->
                          r.getVerificationStatus()
                              != AddressValidationResponse.VerificationStatus.FAILED)
                  .toList();

          log.info(
              "Postcode search returned {} addresses for postcode: {}, country: {}",
              results.size(),
              normalizedPostcode,
              countryCode);
          return results;
        } else if ("ZERO_RESULTS".equals(body.getStatus())) {
          log.warn(
              "No addresses found for postcode: {}, country: {} (ZERO_RESULTS)",
              normalizedPostcode,
              countryCode);
        } else if ("REQUEST_DENIED".equals(body.getStatus())) {
          String errorDetails =
              body.getErrorMessage() != null ? body.getErrorMessage() : "No error details provided";
          String errorMsg =
              String.format(
                  "Google Maps API request denied. Error: %s. Please check: 1) IP address restrictions (add your IP: 212.139.3.25), 2) API key configuration, 3) Enabled APIs (Geocoding API, Places API), 4) Billing account. Note: IP restriction changes may take up to 5 minutes to take effect.",
                  errorDetails);
          log.error(
              "❌ Google Geocoding API REQUEST_DENIED for postcode: {}, country: {}. Error details: {}",
              normalizedPostcode,
              countryCode,
              errorDetails);
          throw new IllegalStateException(errorMsg);
        } else if ("OVER_QUERY_LIMIT".equals(body.getStatus())) {
          String errorMsg =
              "Google Maps API quota exceeded. Please check billing account and quota limits.";
          log.error(
              "❌ Google Geocoding API OVER_QUERY_LIMIT for postcode: {}, country: {}. {}",
              normalizedPostcode,
              countryCode,
              errorMsg);
          throw new IllegalStateException(errorMsg);
        } else if ("INVALID_REQUEST".equals(body.getStatus())) {
          log.warn(
              "⚠️ Google Geocoding API INVALID_REQUEST for postcode: {}, country: {} (invalid postcode format)",
              normalizedPostcode,
              countryCode);
          // Don't throw, just return empty list (invalid postcode is not a system error)
        } else {
          log.warn(
              "Google Geocoding API returned status: {} for postcode: {}, country: {}",
              body.getStatus(),
              normalizedPostcode,
              countryCode);
        }
      }

      log.debug("No addresses found for postcode: {} (global search)", normalizedPostcode);
      return new ArrayList<>();

    } catch (IllegalStateException e) {
      // Re-throw API configuration errors (REQUEST_DENIED, OVER_QUERY_LIMIT, etc.)
      // These should be handled by controller and returned as proper error responses
      log.error("Google Maps API configuration error: {}", e.getMessage());
      throw e;
    } catch (Exception e) {
      log.error(
          "Error calling Google Geocoding API for postcode search: postcode={}, country={}, error={}",
          postcode,
          country,
          e.getMessage(),
          e);
      return new ArrayList<>();
    }
  }

  /**
   * Validate address using Google Geocoding API by address string (fallback only).
   *
   * <p><b>FALLBACK METHOD:</b> Only used when placeId is not available (e.g., manual address
   * entry). Main flow uses validateByPlaceId() with Places API (New) v1 for better accuracy.
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

      UriComponentsBuilder uriBuilder =
          UriComponentsBuilder.fromHttpUrl(GEOCODING_API_URL)
              .queryParam("address", address)
              .queryParam("key", properties.getApiKey());

      // Region bias
      if (properties.getRegionBias() != null && !properties.getRegionBias().isBlank()) {
        uriBuilder.queryParam("region", properties.getRegionBias().split(",")[0]);
      }

      ResponseEntity<GeocodingResponse> response =
          restTemplate.getForEntity(uriBuilder.toUriString(), GeocodingResponse.class);

      GeocodingResponse body = response.getBody();

      // Handle API errors (REQUEST_DENIED, OVER_QUERY_LIMIT, etc.)
      if (body != null && body.getStatus() != null && !"OK".equals(body.getStatus())) {
        String status = body.getStatus();
        String errorMessage = body.getErrorMessage() != null ? body.getErrorMessage() : status;

        // Critical configuration errors should throw IllegalStateException
        if ("REQUEST_DENIED".equals(status)) {
          String detailedMessage =
              String.format(
                  "Google Maps API request denied. Error: %s. Please check: 1) IP address restrictions (add your IP: 212.139.3.25), 2) API key configuration, 3) Enabled APIs (Geocoding API, Places API), 4) Billing account. Note: IP restriction changes may take up to 5 minutes to take effect.",
                  errorMessage);
          log.error("❌ Google Geocoding API REQUEST_DENIED: {}", errorMessage);
          throw new IllegalStateException(detailedMessage);
        }

        if ("OVER_QUERY_LIMIT".equals(status)) {
          throw new IllegalStateException(
              "Google Maps API quota exceeded. Please check your billing account or upgrade your plan.");
        }

        if ("INVALID_REQUEST".equals(status)) {
          throw new IllegalStateException("Invalid Google Maps API request: " + errorMessage);
        }

        // Other errors return FAILED status
        return AddressValidationResponse.builder()
            .verificationStatus(AddressValidationResponse.VerificationStatus.FAILED)
            .errorMessage(
                "Geocoding failed: " + status + (errorMessage != null ? " - " + errorMessage : ""))
            .build();
      }

      if (body != null
          && "OK".equals(body.getStatus())
          && body.getResults() != null
          && !body.getResults().isEmpty()) {
        GeocodingResponse.GeocodingResult result = body.getResults().get(0);
        return mapToValidationResponse(result);
      }

      return AddressValidationResponse.builder()
          .verificationStatus(AddressValidationResponse.VerificationStatus.FAILED)
          .errorMessage("Geocoding failed: " + (body != null ? body.getStatus() : "Unknown error"))
          .build();

    } catch (IllegalStateException e) {
      // Re-throw API configuration errors
      log.error("Google Maps API configuration error: {}", e.getMessage());
      throw e;
    } catch (Exception e) {
      log.error("Error calling Google Geocoding API: {}", e.getMessage(), e);
      return AddressValidationResponse.builder()
          .verificationStatus(AddressValidationResponse.VerificationStatus.FAILED)
          .errorMessage("Validation error: " + e.getMessage())
          .build();
    }
  }

  /**
   * Map Places API (New) v1 Place Details response to AddressValidationResponse.
   *
   * <p>Extracts detailed address components including flat numbers, apartment names, etc. Uses
   * region-based mapping strategy for accurate address component mapping.
   *
   * @param placeDetails Place details response from Google
   * @param originalInput Original input from autocomplete (optional, used for flat number
   *     extraction)
   */
  private AddressValidationResponse mapPlaceDetailsToValidationResponse(
      PlaceDetailsResponse placeDetails, String originalInput) {
    AddressComponents components = addressComponentMapper.map(placeDetails, originalInput);

    AddressValidationResponse.VerificationStatus status = determineVerificationStatus(components);

    return AddressValidationResponse.builder()
        .verificationStatus(status)
        .placeId(placeDetails.getId())
        .formattedAddress(placeDetails.getFormattedAddress())
        .streetAddress(components.getStreetAddress())
        .flatNumber(components.getFlatNumber())
        .city(components.getCity())
        .state(components.getState())
        .district(components.getDistrict())
        .postalCode(components.getPostalCode())
        .country(components.getCountry())
        .countryCode(components.getCountryCode())
        .latitude(
            placeDetails.getLocation() != null ? placeDetails.getLocation().getLatitude() : null)
        .longitude(
            placeDetails.getLocation() != null ? placeDetails.getLocation().getLongitude() : null)
        .build();
  }

  /**
   * Map old Geocoding API response to AddressValidationResponse (for backward compatibility). Uses
   * region-based mapping strategy for accurate address component mapping.
   */
  private AddressValidationResponse mapToValidationResponse(
      GeocodingResponse.GeocodingResult result) {
    AddressComponents components = addressComponentMapper.map(result);

    AddressValidationResponse.VerificationStatus status = determineVerificationStatus(components);

    return AddressValidationResponse.builder()
        .verificationStatus(status)
        .placeId(result.getPlaceId())
        .formattedAddress(result.getFormattedAddress())
        .streetAddress(components.getStreetAddress())
        .flatNumber(components.getFlatNumber())
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

  private AddressValidationResponse.VerificationStatus determineVerificationStatus(
      AddressComponents components) {
    boolean hasStreet =
        components.getStreetAddress() != null && !components.getStreetAddress().isBlank();
    boolean hasCity = components.getCity() != null && !components.getCity().isBlank();
    boolean hasCountry =
        components.getCountryCode() != null && !components.getCountryCode().isBlank();
    boolean hasPostalCode =
        components.getPostalCode() != null && !components.getPostalCode().isBlank();

    // For postcode searches, area-level results (neighborhood, city) are valid even without street
    // address
    // Street address is optional for postcode search results (user will select specific address
    // from list)
    if (hasStreet && hasCity && hasCountry) {
      return AddressValidationResponse.VerificationStatus.VERIFIED;
    } else if (hasCity && hasCountry) {
      // Partial: has city and country (postcode search area-level result)
      // User can select this and then use autocomplete to find specific street addresses
      return AddressValidationResponse.VerificationStatus.PARTIAL;
    } else if (hasPostalCode && hasCountry) {
      // Even partial: has postal code and country (minimal valid result)
      return AddressValidationResponse.VerificationStatus.PARTIAL;
    } else {
      return AddressValidationResponse.VerificationStatus.FAILED;
    }
  }
}
