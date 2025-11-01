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

