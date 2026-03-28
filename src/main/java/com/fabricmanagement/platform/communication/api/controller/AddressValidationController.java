package com.fabricmanagement.platform.communication.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.InternalEndpoint;
import com.fabricmanagement.platform.communication.api.facade.AddressValidationFacade;
import com.fabricmanagement.platform.communication.app.AddressValidationService;
import com.fabricmanagement.platform.communication.dto.AddressDto;
import com.fabricmanagement.platform.communication.dto.AddressValidationResponse;
import com.fabricmanagement.platform.communication.dto.AutocompleteResponse;
import com.fabricmanagement.platform.communication.dto.ValidateAddressRequest;
import com.fabricmanagement.platform.communication.infra.client.GoogleMapsClient;
import com.fabricmanagement.platform.communication.util.PostcodeValidator;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Address Validation Controller - REST endpoints for address autocomplete and validation.
 *
 * <p>Provides endpoints for:
 *
 * <ul>
 *   <li>Address autocomplete (Google Places API)
 *   <li>Address validation (Google Geocoding API)
 *   <li>Validate and create address
 * </ul>
 */
@RestController
@RequestMapping("/api/common/addresses/validation")
@RequiredArgsConstructor
@Slf4j
public class AddressValidationController {

  private final GoogleMapsClient googleMapsClient;
  private final AddressValidationService addressValidationService;
  private final AddressValidationFacade addressValidationFacade;
  private final PostcodeValidator postcodeValidator;

  /**
   * Autocomplete endpoint - Get address suggestions as user types.
   *
   * <p>Uses Google Places Autocomplete API (New) to provide real-time suggestions.
   *
   * <p><b>REST Best Practice:</b> GET method for read operations, supports caching.
   *
   * @param input Address input text (required)
   * @param country Optional country code (ISO 3166-1 alpha-2, e.g., "US", "GB")
   */
  @GetMapping("/autocomplete")
  public ResponseEntity<ApiResponse<AutocompleteResponse>> autocomplete(
      @RequestParam String input, @RequestParam(required = false) String country) {
    log.debug("Autocomplete request: input={}, country={}", input, country);

    if (input == null || input.isBlank()) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("INPUT_REQUIRED", "Input parameter is required"));
    }

    try {
      AutocompleteResponse response = googleMapsClient.autocomplete(input, country);
      return ResponseEntity.ok(ApiResponse.success(response));
    } catch (IllegalStateException e) {
      // Handle Google Maps API configuration errors (REQUEST_DENIED,
      // OVER_QUERY_LIMIT, etc.)
      log.error("Google Maps API error: {}", e.getMessage());
      return ResponseEntity.status(500).body(ApiResponse.error("GOOGLE_API_ERROR", e.getMessage()));
    } catch (Exception e) {
      log.error("Unexpected error during autocomplete: {}", e.getMessage(), e);
      return ResponseEntity.status(500)
          .body(
              ApiResponse.error(
                  "GOOGLE_API_ERROR", "Failed to search addresses. Please try again later."));
    }
  }

  /**
   * Validate address endpoint - Validate address without persisting.
   *
   * <p>Returns normalized address data without saving to database.
   */
  @PostMapping("/validate")
  public ResponseEntity<ApiResponse<AddressValidationResponse>> validateAddress(
      @Valid @RequestBody ValidateAddressRequest request) {
    log.info("Validation request: placeId={}", request.getPlaceId());

    AddressValidationResponse response = addressValidationService.validateAddress(request);

    if (response.getVerificationStatus() == AddressValidationResponse.VerificationStatus.FAILED) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("VALIDATION_FAILED", response.getErrorMessage()));
    }

    return ResponseEntity.ok(ApiResponse.success(response, "Address validated successfully"));
  }

  /**
   * Validate and create endpoint - Validate address and persist to database.
   *
   * <p>Validates address first, then creates Address entity with normalized data.
   *
   * <p>Only VERIFIED or PARTIAL addresses are persisted.
   */
  @PostMapping("/validate-and-create")
  public ResponseEntity<ApiResponse<AddressDto>> validateAndCreate(
      @Valid @RequestBody ValidateAddressRequest request) {
    log.info(
        "Validate and create request: placeId={}, addressType={}",
        request.getPlaceId(),
        request.getAddressType());

    AddressDto address = addressValidationFacade.validateAndCreateAddress(request);

    return ResponseEntity.ok(
        ApiResponse.success(address, "Address validated and created successfully"));
  }

  /**
   * Revalidate existing address endpoint.
   *
   * <p>Revalidates an existing address by its placeId and updates with latest data.
   */
  @PostMapping("/{addressId}/revalidate")
  public ResponseEntity<ApiResponse<AddressDto>> revalidateAddress(@PathVariable UUID addressId) {
    log.info("Revalidate request: addressId={}", addressId);

    AddressDto address = addressValidationFacade.revalidateAddress(addressId);

    return ResponseEntity.ok(ApiResponse.success(address, "Address revalidated successfully"));
  }

  /**
   * Search addresses by postcode endpoint (Global).
   *
   * <p><b>NOTE:</b> This endpoint uses deprecated Geocoding API method. Main flow uses
   * autocomplete.
   *
   * <p>Uses Google Geocoding API to find all addresses matching a postcode globally.
   *
   * <p><b>Country Parameter (Optional):</b>
   *
   * <ul>
   *   <li><b>If provided:</b> Filters results to specific country (faster, more accurate)
   *   <li><b>If omitted:</b> Searches globally across all countries
   * </ul>
   *
   * <p><b>Country Format:</b> Accepts both ISO code ("GB", "TR") or country name ("United Kingdom",
   * "Turkey")
   *
   * <p><b>Use Cases:</b>
   *
   * <ul>
   *   <li>User knows country: Select country → Faster, accurate results
   *   <li>User doesn't know country: Skip country → Global search, may find multiple countries
   * </ul>
   *
   * <p><b>Examples:</b>
   *
   * <ul>
   *   <li>Global: <code>GET /search-by-postcode?postcode=34000</code> (searches worldwide)
   *   <li>Country-specific: <code>GET /search-by-postcode?postcode=34000&country=TR</code> (Turkey
   *       only)
   *   <li>Country name: <code>GET /search-by-postcode?postcode=MK5 7GE&country=United Kingdom
   *       </code>
   * </ul>
   *
   * @param postcode Postal/ZIP code (required)
   * @param country Optional country code (ISO 3166-1 alpha-2, e.g., "TR", "GB") or country name
   *     (e.g., "Turkey", "United Kingdom"). If omitted, searches globally across all countries.
   * @return List of addresses matching the postcode
   */
  @GetMapping("/search-by-postcode")
  public ResponseEntity<ApiResponse<List<AddressValidationResponse>>> searchByPostcode(
      @RequestParam String postcode, @RequestParam(required = false) String country) {
    log.debug("Postcode search request: postcode={}, country={}", postcode, country);

    if (postcode == null || postcode.isBlank()) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("POSTCODE_REQUIRED", "Postcode parameter is required"));
    }

    // Extract country code from country parameter (if provided)
    String countryCode = extractCountryCode(country);

    // Validate minimum length before making API call
    if (!postcodeValidator.isLongEnoughToSearch(postcode, countryCode)) {
      int minLength = postcodeValidator.getMinimumLength(countryCode);
      String countryName = countryCode != null ? countryCode : "selected country";
      return ResponseEntity.badRequest()
          .body(
              ApiResponse.error(
                  "POSTCODE_TOO_SHORT",
                  String.format(
                      "Postcode must be at least %d characters for %s. Please enter more characters.",
                      minLength, countryName)));
    }

    // Validate format if country is specified
    if (countryCode != null && !countryCode.isBlank()) {
      if (!postcodeValidator.isValidFormat(postcode, countryCode)) {
        log.debug(
            "Postcode format validation failed: postcode={}, country={}", postcode, countryCode);
        // Don't reject - let Google API handle it (format might be valid but not match
        // our regex)
        // This is just a pre-check to reduce unnecessary API calls
      }
    }

    try {
      List<AddressValidationResponse> results =
          googleMapsClient.searchByPostcode(postcode, country);
      return ResponseEntity.ok(ApiResponse.success(results));
    } catch (IllegalStateException e) {
      // Handle Google Maps API configuration errors (REQUEST_DENIED,
      // OVER_QUERY_LIMIT, etc.)
      log.error("Google Maps API error: {}", e.getMessage());
      return ResponseEntity.status(500).body(ApiResponse.error("GOOGLE_API_ERROR", e.getMessage()));
    } catch (Exception e) {
      log.error("Unexpected error during postcode search: {}", e.getMessage(), e);
      return ResponseEntity.status(500)
          .body(
              ApiResponse.error(
                  "GOOGLE_API_ERROR", "Failed to search addresses. Please try again later."));
    }
  }

  /**
   * Extracts ISO country code from country parameter. Handles both ISO codes ("GB", "TR") and
   * country names ("United Kingdom", "Turkey").
   */
  private String extractCountryCode(String country) {
    if (country == null || country.isBlank()) {
      return null;
    }

    String normalized = country.trim();

    // If it's a 2-letter code (ISO 3166-1 alpha-2), use it directly
    if (normalized.length() == 2 && normalized.matches("[A-Za-z]{2}")) {
      return normalized.toUpperCase();
    }

    // Map common country names to ISO codes
    String upper = normalized.toUpperCase();
    return switch (upper) {
      case "UNITED KINGDOM", "UK", "GREAT BRITAIN", "BRITAIN" -> "GB";
      case "UNITED STATES", "USA", "US", "AMERICA" -> "US";
      case "TURKEY", "TÜRKIYE", "TURKIYE" -> "TR";
      case "GERMANY", "DEUTSCHLAND" -> "DE";
      case "FRANCE" -> "FR";
      case "ITALY", "ITALIA" -> "IT";
      case "SPAIN", "ESPANA" -> "ES";
      case "NETHERLANDS", "HOLLAND" -> "NL";
      case "CANADA" -> "CA";
      case "AUSTRALIA" -> "AU";
      default -> null; // Unknown country name
    };
  }

  /**
   * Simple address search endpoint - House number + Postcode.
   *
   * <p>Simplified flow: User enters house number/street name + postcode, gets complete address.
   *
   * <p>Example: "1 MK5 7GE" or "Welsummer Grove MK5 7GE" → Returns full address with all fields.
   *
   * @param houseNumber House number or street name (optional, can be combined with postcode)
   * @param postcode Postal/ZIP code (required)
   * @param country Optional country code (ISO 3166-1 alpha-2, e.g., "GB", "TR")
   * @return Complete address with all fields filled
   */
  @InternalEndpoint(
      description = "Simple address lookup by house number and postcode",
      calledBy = {"frontend-web"})
  @GetMapping("/search-address")
  public ResponseEntity<ApiResponse<AddressValidationResponse>> searchAddress(
      @RequestParam(required = false) String houseNumber,
      @RequestParam String postcode,
      @RequestParam(required = false) String country) {
    log.debug(
        "Simple address search: houseNumber={}, postcode={}, country={}",
        houseNumber,
        postcode,
        country);

    if (postcode == null || postcode.isBlank()) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.error("POSTCODE_REQUIRED", "Postcode parameter is required"));
    }

    try {
      // Build address query: "houseNumber postcode" or just "postcode"
      String addressQuery;
      if (houseNumber != null && !houseNumber.trim().isBlank()) {
        String normalizedHouseNumber = houseNumber.trim();
        String normalizedPostcode = postcode.trim().replaceAll("\\s+", " ");
        addressQuery = normalizedHouseNumber + " " + normalizedPostcode;
      } else {
        addressQuery = postcode.trim().replaceAll("\\s+", " ");
      }

      // Add country to query if provided (for better accuracy)
      if (country != null && !country.isBlank()) {
        String countryCode = extractCountryCode(country);
        if (countryCode != null) {
          // Use validateByAddress with country context
          // Google API will use country for better results
          addressQuery = addressQuery + ", " + (countryCode.length() == 2 ? countryCode : country);
        }
      }

      // Use validateByAddress to get complete address
      AddressValidationResponse result = googleMapsClient.validateByAddress(addressQuery);

      if (result.getVerificationStatus() == AddressValidationResponse.VerificationStatus.FAILED) {
        return ResponseEntity.badRequest()
            .body(
                ApiResponse.error(
                    "ADDRESS_NOT_FOUND",
                    result.getErrorMessage() != null
                        ? result.getErrorMessage()
                        : "Address not found. Please check house number and postcode."));
      }

      return ResponseEntity.ok(ApiResponse.success(result));

    } catch (IllegalStateException e) {
      log.error("Google Maps API error: {}", e.getMessage());
      return ResponseEntity.status(500).body(ApiResponse.error("GOOGLE_API_ERROR", e.getMessage()));
    } catch (Exception e) {
      log.error("Unexpected error during address search: {}", e.getMessage(), e);
      return ResponseEntity.status(500)
          .body(
              ApiResponse.error(
                  "SEARCH_ERROR", "Failed to search address. Please try again later."));
    }
  }
}
