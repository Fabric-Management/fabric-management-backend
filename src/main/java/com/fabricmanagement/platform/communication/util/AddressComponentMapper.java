package com.fabricmanagement.platform.communication.util;

import com.fabricmanagement.platform.communication.infra.client.googlemaps.response.AddressComponents;
import com.fabricmanagement.platform.communication.infra.client.googlemaps.response.GeocodingResponse;
import com.fabricmanagement.platform.communication.infra.client.googlemaps.response.PlaceDetailsResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Address Component Mapper - Maps Google Maps API address components to domain fields based on
 * region-specific address structures.
 *
 * <p>Each region has different address component hierarchies. This mapper applies the correct
 * mapping strategy based on the country's region group.
 */
@Slf4j
public class AddressComponentMapper {

  /**
   * Map Google Places API address components to domain AddressComponents.
   *
   * @param placeDetails Place details response from Google Places API
   * @return Mapped address components
   */
  public AddressComponents map(PlaceDetailsResponse placeDetails, String originalInput) {
    if (placeDetails == null || placeDetails.getAddressComponents() == null) {
      log.debug("🔍 AddressComponentMapper: addressComponents is null");
      return new AddressComponents();
    }

    List<PlaceDetailsResponse.PlaceAddressComponent> components =
        placeDetails.getAddressComponents();
    log.debug(
        "🔍 AddressComponentMapper: Processing {} address components (Places API)",
        components.size());

    AddressComponents result = new AddressComponents();

    Map<String, PlaceDetailsResponse.PlaceAddressComponent> componentMap =
        buildComponentMap(components);

    String countryCode = extractCountryCodeFromPlaceDetails(componentMap);
    AddressRegionMapper.AddressRegion region =
        AddressRegionMapper.getRegionByCountryCode(countryCode);

    if (region == null) {
      log.warn("⚠️ Unknown region for country code: {}, using default mapping", countryCode);
      applyDefaultMapping(components, result, countryCode);
    } else {
      log.debug("🌍 Region detected: {} for country: {}", region, countryCode);
      applyRegionMapping(components, result, region, countryCode);
    }

    // Fallback: Extract flat number from formatted address if subpremise was not found
    applyFlatNumberFallback(result, placeDetails.getFormattedAddress(), countryCode);

    // Last resort: Extract flat number from original input if still not found
    if ((result.getFlatNumber() == null || result.getFlatNumber().isBlank())
        && originalInput != null
        && !originalInput.isBlank()) {
      String extracted =
          extractFlatNumberFromOriginalInput(originalInput, result.getStreetAddress(), countryCode);
      if (extracted != null && !extracted.isBlank()) {
        result.setFlatNumber(extracted);
        log.debug(
            "  ✅ ORIGINAL INPUT: Extracted flatNumber='{}' from originalInput='{}'",
            extracted,
            originalInput);
      }
    }

    logFinalMapping(result);
    return result;
  }

  /** Backward compatibility - map without originalInput. */
  public AddressComponents map(PlaceDetailsResponse placeDetails) {
    return map(placeDetails, null);
  }

  /**
   * Map Google Geocoding API address components to domain AddressComponents.
   *
   * @param geocodingResult Geocoding result from Google Geocoding API
   * @return Mapped address components
   */
  public AddressComponents map(GeocodingResponse.GeocodingResult geocodingResult) {
    if (geocodingResult == null || geocodingResult.getAddressComponents() == null) {
      log.debug("🔍 AddressComponentMapper: addressComponents is null (Geocoding API)");
      return new AddressComponents();
    }

    List<GeocodingResponse.AddressComponent> components = geocodingResult.getAddressComponents();
    log.debug(
        "🔍 AddressComponentMapper: Processing {} address components (Geocoding API)",
        components.size());

    AddressComponents result = new AddressComponents();

    String countryCode = extractCountryCodeFromGeocoding(components);
    AddressRegionMapper.AddressRegion region =
        AddressRegionMapper.getRegionByCountryCode(countryCode);

    if (region == null) {
      log.warn("⚠️ Unknown region for country code: {}, using default mapping", countryCode);
      applyDefaultMappingGeocoding(components, result, countryCode);
    } else {
      log.debug("🌍 Region detected: {} for country: {} (Geocoding API)", region, countryCode);
      applyRegionMappingGeocoding(components, result, region, countryCode);
    }

    // Fallback: Extract flat number from formatted address if subpremise was not found
    applyFlatNumberFallback(result, geocodingResult.getFormattedAddress(), countryCode);

    logFinalMapping(result);
    return result;
  }

  private Map<String, PlaceDetailsResponse.PlaceAddressComponent> buildComponentMap(
      List<PlaceDetailsResponse.PlaceAddressComponent> components) {
    return components.stream()
        .filter(comp -> comp.getTypes() != null && !comp.getTypes().isEmpty())
        .flatMap(comp -> comp.getTypes().stream().map(type -> Map.entry(type, comp)))
        .collect(
            Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first));
  }

  private String extractCountryCodeFromPlaceDetails(
      Map<String, PlaceDetailsResponse.PlaceAddressComponent> componentMap) {
    PlaceDetailsResponse.PlaceAddressComponent countryComp = componentMap.get("country");
    if (countryComp != null && countryComp.getShortText() != null) {
      return countryComp.getShortText().trim().toUpperCase();
    }
    return null;
  }

  private String extractCountryCodeFromGeocoding(
      List<GeocodingResponse.AddressComponent> components) {
    for (GeocodingResponse.AddressComponent comp : components) {
      if (comp.getTypes() != null && comp.getTypes().contains("country")) {
        if (comp.getShortName() != null) {
          return comp.getShortName().trim().toUpperCase();
        }
      }
    }
    return null;
  }

  private void applyRegionMapping(
      List<PlaceDetailsResponse.PlaceAddressComponent> components,
      AddressComponents result,
      AddressRegionMapper.AddressRegion region,
      String countryCode) {

    switch (region) {
      case WESTERN_EUROPE -> applyWesternEuropeMapping(components, result, countryCode);
      case EASTERN_EUROPE -> applyEasternEuropeMapping(components, result, countryCode);
      case NORTH_AMERICA -> applyNorthAmericaMapping(components, result);
      case ASIA_PACIFIC -> applyAsiaPacificMapping(components, result);
      case LATIN_AMERICA -> applyLatinAmericaMapping(components, result);
      case MIDDLE_EAST_AFRICA -> applyMiddleEastAfricaMapping(components, result);
    }
  }

  private void applyWesternEuropeMapping(
      List<PlaceDetailsResponse.PlaceAddressComponent> components,
      AddressComponents result,
      String countryCode) {

    boolean isUK = "GB".equals(countryCode) || "IE".equals(countryCode);

    // First pass: Handle UK postal_town (must be city) before processing other components
    if (isUK) {
      for (PlaceDetailsResponse.PlaceAddressComponent comp : components) {
        List<String> types = comp.getTypes();
        if (types != null && types.contains("postal_town")) {
          String longText = getLongText(comp);
          result.setCity(longText);
          logMapping("postal_town", longText, "city", longText);
          break; // Only one postal_town expected
        }
      }
    }

    // Second pass: Process all other components
    for (PlaceDetailsResponse.PlaceAddressComponent comp : components) {
      List<String> types = comp.getTypes();
      if (types == null || types.isEmpty()) {
        continue;
      }

      String longText = getLongText(comp);
      String shortText = getShortText(comp);

      if (types.contains("street_number")) {
        setStreetAddress(result, longText, true);
        logMapping("street_number", longText, "streetAddress", result.getStreetAddress());
      } else if (types.contains("route")) {
        // Check if route contains flat number pattern (e.g., "20/34 Selvi Sokak")
        // This can happen when Google combines street number with route
        if (result.getFlatNumber() == null || result.getFlatNumber().isBlank()) {
          String[] routeParts = extractFlatNumberFromRoute(longText);
          if (routeParts != null && routeParts.length == 3) {
            // Found pattern in route: [streetNumber, flatNumber, routeName]
            String streetNum = routeParts[0];
            String flatNum = routeParts[1];
            String routeName = routeParts[2];
            result.setFlatNumber(flatNum);
            // Set street address with parsed parts
            String currentStreet = result.getStreetAddress();
            if (currentStreet != null && !currentStreet.isBlank()) {
              result.setStreetAddress((streetNum + " " + routeName).trim());
            } else {
              setStreetAddress(result, streetNum, true);
              setStreetAddress(result, routeName, false);
            }
            log.debug(
                "  ✅ PARSED: Route '{}' contains flat number → streetNumber='{}', flatNumber='{}', route='{}'",
                longText,
                streetNum,
                flatNum,
                routeName);
            continue;
          }
        }
        setStreetAddress(result, longText, false);
        logMapping("route", longText, "streetAddress", result.getStreetAddress());
      } else if (types.contains("subpremise")) {
        result.setFlatNumber(longText);
        logMapping("subpremise", longText, "flatNumber", longText);
      } else if (types.contains("premise")) {
        appendToStreetAddress(result, longText);
        logMapping("premise", longText, "streetAddress", result.getStreetAddress());
      } else if (types.contains("postal_town") && isUK) {
        // UK/Ireland: postal_town is already processed in first pass
        // Skip here to avoid duplicate processing
        continue;
      } else if (types.contains("locality")) {
        if (isUK) {
          // UK/Ireland: locality is district/village (e.g., "Shenley Brook End")
          // If postal_town already set city, use locality as district
          // Otherwise, locality might be the city (fallback)
          if (result.getCity() != null) {
            // City already set from postal_town, use locality as district
            if (result.getDistrict() == null) {
              result.setDistrict(longText);
              logMapping("locality", longText, "district", longText);
            }
          } else {
            // No postal_town yet, locality might be city (fallback for some UK addresses)
            result.setCity(longText);
            logMapping("locality", longText, "city (fallback)", longText);
          }
        } else {
          // Other Western Europe: locality can be city if not already set
          if (result.getCity() == null) {
            result.setCity(longText);
            logMapping("locality", longText, "city", longText);
          }
        }
      } else if (types.contains("administrative_area_level_2")) {
        if (isUK) {
          // UK/Ireland: administrative_area_level_2 can be district if locality not set
          if (result.getDistrict() == null) {
            result.setDistrict(longText);
            logMapping("administrative_area_level_2", longText, "district", longText);
          }
        } else {
          // Other Western Europe: administrative_area_level_2 is district
          if (result.getDistrict() == null) {
            result.setDistrict(longText);
            logMapping("administrative_area_level_2", longText, "district", longText);
          }
        }
      } else if (types.contains("administrative_area_level_1")) {
        // UK/Ireland: administrative_area_level_1 is country component (England, Scotland, etc.)
        // Not a state - UK doesn't have states. Leave state empty.
        if (!isUK) {
          result.setState(getStateValue(longText, shortText));
          logMapping("administrative_area_level_1", longText, "state", result.getState());
        } else {
          log.debug(
              "  ⏭️  SKIPPED: administrative_area_level_1 '{}' (UK/Ireland - not a state)",
              longText);
        }
      } else if (types.contains("postal_code")) {
        result.setPostalCode(longText);
        logMapping("postal_code", longText, "postalCode", longText);
      } else if (types.contains("country")) {
        result.setCountry(longText);
        result.setCountryCode(shortText);
        logMapping("country", longText, "country/countryCode", longText + "/" + shortText);
      }
    }
  }

  private void applyEasternEuropeMapping(
      List<PlaceDetailsResponse.PlaceAddressComponent> components,
      AddressComponents result,
      String countryCode) {

    for (PlaceDetailsResponse.PlaceAddressComponent comp : components) {
      List<String> types = comp.getTypes();
      if (types == null || types.isEmpty()) {
        continue;
      }

      String longText = getLongText(comp);
      String shortText = getShortText(comp);

      if (types.contains("street_number")) {
        setStreetAddress(result, longText, true);
        logMapping("street_number", longText, "streetAddress", result.getStreetAddress());
      } else if (types.contains("route")) {
        // Check if route contains flat number pattern (e.g., "20/34 Selvi Sokak")
        if (result.getFlatNumber() == null || result.getFlatNumber().isBlank()) {
          String[] routeParts = extractFlatNumberFromRoute(longText);
          if (routeParts != null && routeParts.length == 3) {
            String streetNum = routeParts[0];
            String flatNum = routeParts[1];
            String routeName = routeParts[2];
            result.setFlatNumber(flatNum);
            String currentStreet = result.getStreetAddress();
            if (currentStreet != null && !currentStreet.isBlank()) {
              result.setStreetAddress((streetNum + " " + routeName).trim());
            } else {
              setStreetAddress(result, streetNum, true);
              setStreetAddress(result, routeName, false);
            }
            log.debug(
                "  ✅ PARSED: Route '{}' contains flat number → streetNumber='{}', flatNumber='{}', route='{}'",
                longText,
                streetNum,
                flatNum,
                routeName);
            continue;
          }
        }
        setStreetAddress(result, longText, false);
        logMapping("route", longText, "streetAddress", result.getStreetAddress());
      } else if (types.contains("subpremise")) {
        result.setFlatNumber(longText);
        logMapping("subpremise", longText, "flatNumber", longText);
      } else if (types.contains("premise")) {
        appendToStreetAddress(result, longText);
        logMapping("premise", longText, "streetAddress", result.getStreetAddress());
      } else if (types.contains("administrative_area_level_4") || types.contains("sublocality")) {
        // Turkey/Eastern Europe: administrative_area_level_4/sublocality is mahalle/neighborhood
        // (district)
        if (result.getDistrict() == null) {
          result.setDistrict(longText);
          logMapping("administrative_area_level_4/sublocality", longText, "district", longText);
        }
      } else if (types.contains("administrative_area_level_2")) {
        // Turkey/Eastern Europe: administrative_area_level_2 is ilçe (district), not city
        // City should come from administrative_area_level_1 (il)
        if (result.getDistrict() == null) {
          result.setDistrict(longText);
          logMapping("administrative_area_level_2", longText, "district", longText);
        }
      } else if (types.contains("administrative_area_level_1")) {
        // Turkey/Eastern Europe: administrative_area_level_1 is il (city/province)
        // For Turkey: Istanbul, Ankara, etc. are cities, not states
        if ("TR".equals(countryCode)) {
          result.setCity(longText);
          logMapping("administrative_area_level_1", longText, "city", longText);
        } else {
          // Other Eastern Europe countries might use it as state
          result.setState(longText);
          logMapping("administrative_area_level_1", longText, "state", longText);
        }
      } else if (types.contains("postal_code")) {
        result.setPostalCode(longText);
        logMapping("postal_code", longText, "postalCode", longText);
      } else if (types.contains("country")) {
        result.setCountry(longText);
        result.setCountryCode(shortText);
        logMapping("country", longText, "country/countryCode", longText + "/" + shortText);
      }
    }
  }

  private void applyNorthAmericaMapping(
      List<PlaceDetailsResponse.PlaceAddressComponent> components, AddressComponents result) {

    for (PlaceDetailsResponse.PlaceAddressComponent comp : components) {
      List<String> types = comp.getTypes();
      if (types == null || types.isEmpty()) {
        continue;
      }

      String longText = getLongText(comp);
      String shortText = getShortText(comp);

      if (types.contains("street_number")) {
        setStreetAddress(result, longText, true);
        logMapping("street_number", longText, "streetAddress", result.getStreetAddress());
      } else if (types.contains("route")) {
        // Check if route contains flat number pattern
        if (result.getFlatNumber() == null || result.getFlatNumber().isBlank()) {
          String[] routeParts = extractFlatNumberFromRoute(longText);
          if (routeParts != null && routeParts.length == 3) {
            String streetNum = routeParts[0];
            String flatNum = routeParts[1];
            String routeName = routeParts[2];
            result.setFlatNumber(flatNum);
            String currentStreet = result.getStreetAddress();
            if (currentStreet != null && !currentStreet.isBlank()) {
              result.setStreetAddress((streetNum + " " + routeName).trim());
            } else {
              setStreetAddress(result, streetNum, true);
              setStreetAddress(result, routeName, false);
            }
            log.debug(
                "  ✅ PARSED: Route '{}' contains flat number → streetNumber='{}', flatNumber='{}', route='{}'",
                longText,
                streetNum,
                flatNum,
                routeName);
            continue;
          }
        }
        setStreetAddress(result, longText, false);
        logMapping("route", longText, "streetAddress", result.getStreetAddress());
      } else if (types.contains("subpremise")) {
        result.setFlatNumber(longText);
        logMapping("subpremise", longText, "flatNumber", longText);
      } else if (types.contains("locality")) {
        if (result.getCity() == null) {
          result.setCity(longText);
          logMapping("locality", longText, "city", longText);
        }
      } else if (types.contains("administrative_area_level_2")) {
        if (result.getDistrict() == null) {
          result.setDistrict(longText);
          logMapping("administrative_area_level_2", longText, "district", longText);
        }
      } else if (types.contains("administrative_area_level_1")) {
        result.setState(getStateValue(longText, shortText));
        logMapping("administrative_area_level_1", longText, "state", result.getState());
      } else if (types.contains("postal_code")) {
        result.setPostalCode(longText);
        logMapping("postal_code", longText, "postalCode", longText);
      } else if (types.contains("country")) {
        result.setCountry(longText);
        result.setCountryCode(shortText);
        logMapping("country", longText, "country/countryCode", longText + "/" + shortText);
      }
    }
  }

  private void applyAsiaPacificMapping(
      List<PlaceDetailsResponse.PlaceAddressComponent> components, AddressComponents result) {

    for (PlaceDetailsResponse.PlaceAddressComponent comp : components) {
      List<String> types = comp.getTypes();
      if (types == null || types.isEmpty()) {
        continue;
      }

      String longText = getLongText(comp);
      String shortText = getShortText(comp);

      if (types.contains("street_number")) {
        setStreetAddress(result, longText, true);
        logMapping("street_number", longText, "streetAddress", result.getStreetAddress());
      } else if (types.contains("route")) {
        // Check if route contains flat number pattern
        if (result.getFlatNumber() == null || result.getFlatNumber().isBlank()) {
          String[] routeParts = extractFlatNumberFromRoute(longText);
          if (routeParts != null && routeParts.length == 3) {
            String streetNum = routeParts[0];
            String flatNum = routeParts[1];
            String routeName = routeParts[2];
            result.setFlatNumber(flatNum);
            String currentStreet = result.getStreetAddress();
            if (currentStreet != null && !currentStreet.isBlank()) {
              result.setStreetAddress((streetNum + " " + routeName).trim());
            } else {
              setStreetAddress(result, streetNum, true);
              setStreetAddress(result, routeName, false);
            }
            log.debug(
                "  ✅ PARSED: Route '{}' contains flat number → streetNumber='{}', flatNumber='{}', route='{}'",
                longText,
                streetNum,
                flatNum,
                routeName);
            continue;
          }
        }
        setStreetAddress(result, longText, false);
        logMapping("route", longText, "streetAddress", result.getStreetAddress());
      } else if (types.contains("subpremise")) {
        result.setFlatNumber(longText);
        logMapping("subpremise", longText, "flatNumber", longText);
      } else if (types.contains("premise")) {
        appendToStreetAddress(result, longText);
        logMapping("premise", longText, "streetAddress", result.getStreetAddress());
      } else if (types.contains("locality")) {
        // Asia Pacific: locality is city (e.g., Sydney, Melbourne)
        if (result.getCity() == null) {
          result.setCity(longText);
          logMapping("locality", longText, "city", longText);
        }
      } else if (types.contains("sublocality") || types.contains("sublocality_level_1")) {
        // Asia Pacific: sublocality is district
        if (result.getDistrict() == null) {
          result.setDistrict(longText);
          logMapping("sublocality", longText, "district", longText);
        }
      } else if (types.contains("administrative_area_level_2")) {
        // Asia Pacific: administrative_area_level_2 can be district or city (depends on country)
        // Prefer as district if city already set, otherwise as city
        if (result.getCity() != null && result.getDistrict() == null) {
          result.setDistrict(longText);
          logMapping("administrative_area_level_2", longText, "district", longText);
        } else if (result.getCity() == null) {
          result.setCity(longText);
          logMapping("administrative_area_level_2", longText, "city", longText);
        }
      } else if (types.contains("administrative_area_level_1")) {
        // Asia Pacific: administrative_area_level_1 is state/province
        result.setState(longText);
        logMapping("administrative_area_level_1", longText, "state", longText);
      } else if (types.contains("postal_code")) {
        result.setPostalCode(longText);
        logMapping("postal_code", longText, "postalCode", longText);
      } else if (types.contains("country")) {
        result.setCountry(longText);
        result.setCountryCode(shortText);
        logMapping("country", longText, "country/countryCode", longText + "/" + shortText);
      }
    }
  }

  private void applyLatinAmericaMapping(
      List<PlaceDetailsResponse.PlaceAddressComponent> components, AddressComponents result) {

    for (PlaceDetailsResponse.PlaceAddressComponent comp : components) {
      List<String> types = comp.getTypes();
      if (types == null || types.isEmpty()) {
        continue;
      }

      String longText = getLongText(comp);
      String shortText = getShortText(comp);

      if (types.contains("street_number")) {
        setStreetAddress(result, longText, true);
        logMapping("street_number", longText, "streetAddress", result.getStreetAddress());
      } else if (types.contains("route")) {
        // Check if route contains flat number pattern
        if (result.getFlatNumber() == null || result.getFlatNumber().isBlank()) {
          String[] routeParts = extractFlatNumberFromRoute(longText);
          if (routeParts != null && routeParts.length == 3) {
            String streetNum = routeParts[0];
            String flatNum = routeParts[1];
            String routeName = routeParts[2];
            result.setFlatNumber(flatNum);
            String currentStreet = result.getStreetAddress();
            if (currentStreet != null && !currentStreet.isBlank()) {
              result.setStreetAddress((streetNum + " " + routeName).trim());
            } else {
              setStreetAddress(result, streetNum, true);
              setStreetAddress(result, routeName, false);
            }
            log.debug(
                "  ✅ PARSED: Route '{}' contains flat number → streetNumber='{}', flatNumber='{}', route='{}'",
                longText,
                streetNum,
                flatNum,
                routeName);
            continue;
          }
        }
        setStreetAddress(result, longText, false);
        logMapping("route", longText, "streetAddress", result.getStreetAddress());
      } else if (types.contains("subpremise")) {
        result.setFlatNumber(longText);
        logMapping("subpremise", longText, "flatNumber", longText);
      } else if (types.contains("premise")) {
        appendToStreetAddress(result, longText);
        logMapping("premise", longText, "streetAddress", result.getStreetAddress());
      } else if (types.contains("sublocality") || types.contains("sublocality_level_1")) {
        // Latin America: sublocality is district
        if (result.getDistrict() == null) {
          result.setDistrict(longText);
          logMapping("sublocality", longText, "district", longText);
        }
      } else if (types.contains("locality")) {
        // Latin America: locality is city
        if (result.getCity() == null) {
          result.setCity(longText);
          logMapping("locality", longText, "city", longText);
        }
      } else if (types.contains("administrative_area_level_2")) {
        // Latin America: administrative_area_level_2 is district
        if (result.getDistrict() == null) {
          result.setDistrict(longText);
          logMapping("administrative_area_level_2", longText, "district", longText);
        }
      } else if (types.contains("administrative_area_level_1")) {
        // Latin America: administrative_area_level_1 is state
        result.setState(longText);
        logMapping("administrative_area_level_1", longText, "state", longText);
      } else if (types.contains("postal_code")) {
        result.setPostalCode(longText);
        logMapping("postal_code", longText, "postalCode", longText);
      } else if (types.contains("country")) {
        result.setCountry(longText);
        result.setCountryCode(shortText);
        logMapping("country", longText, "country/countryCode", longText + "/" + shortText);
      }
    }
  }

  private void applyMiddleEastAfricaMapping(
      List<PlaceDetailsResponse.PlaceAddressComponent> components, AddressComponents result) {

    for (PlaceDetailsResponse.PlaceAddressComponent comp : components) {
      List<String> types = comp.getTypes();
      if (types == null || types.isEmpty()) {
        continue;
      }

      String longText = getLongText(comp);
      String shortText = getShortText(comp);

      if (types.contains("street_number")) {
        setStreetAddress(result, longText, true);
        logMapping("street_number", longText, "streetAddress", result.getStreetAddress());
      } else if (types.contains("route")) {
        // Check if route contains flat number pattern
        if (result.getFlatNumber() == null || result.getFlatNumber().isBlank()) {
          String[] routeParts = extractFlatNumberFromRoute(longText);
          if (routeParts != null && routeParts.length == 3) {
            String streetNum = routeParts[0];
            String flatNum = routeParts[1];
            String routeName = routeParts[2];
            result.setFlatNumber(flatNum);
            String currentStreet = result.getStreetAddress();
            if (currentStreet != null && !currentStreet.isBlank()) {
              result.setStreetAddress((streetNum + " " + routeName).trim());
            } else {
              setStreetAddress(result, streetNum, true);
              setStreetAddress(result, routeName, false);
            }
            log.debug(
                "  ✅ PARSED: Route '{}' contains flat number → streetNumber='{}', flatNumber='{}', route='{}'",
                longText,
                streetNum,
                flatNum,
                routeName);
            continue;
          }
        }
        setStreetAddress(result, longText, false);
        logMapping("route", longText, "streetAddress", result.getStreetAddress());
      } else if (types.contains("subpremise")) {
        result.setFlatNumber(longText);
        logMapping("subpremise", longText, "flatNumber", longText);
      } else if (types.contains("premise")) {
        appendToStreetAddress(result, longText);
        logMapping("premise", longText, "streetAddress", result.getStreetAddress());
      } else if (types.contains("locality")) {
        // Middle East/Africa: locality is city
        if (result.getCity() == null) {
          result.setCity(longText);
          logMapping("locality", longText, "city", longText);
        }
      } else if (types.contains("sublocality") || types.contains("sublocality_level_1")) {
        // Middle East/Africa: sublocality is district
        if (result.getDistrict() == null) {
          result.setDistrict(longText);
          logMapping("sublocality", longText, "district", longText);
        }
      } else if (types.contains("administrative_area_level_2")) {
        // Middle East/Africa: administrative_area_level_2 is district
        if (result.getDistrict() == null) {
          result.setDistrict(longText);
          logMapping("administrative_area_level_2", longText, "district", longText);
        }
      } else if (types.contains("administrative_area_level_1")) {
        // Middle East/Africa: administrative_area_level_1 is state/province
        result.setState(longText);
        logMapping("administrative_area_level_1", longText, "state", longText);
      } else if (types.contains("postal_code")) {
        result.setPostalCode(longText);
        logMapping("postal_code", longText, "postalCode", longText);
      } else if (types.contains("country")) {
        result.setCountry(longText);
        result.setCountryCode(shortText);
        logMapping("country", longText, "country/countryCode", longText + "/" + shortText);
      }
    }
  }

  private void applyDefaultMapping(
      List<PlaceDetailsResponse.PlaceAddressComponent> components,
      AddressComponents result,
      String countryCode) {

    applyWesternEuropeMapping(components, result, countryCode);
  }

  private void applyRegionMappingGeocoding(
      List<GeocodingResponse.AddressComponent> components,
      AddressComponents result,
      AddressRegionMapper.AddressRegion region,
      String countryCode) {

    switch (region) {
      case WESTERN_EUROPE -> applyWesternEuropeMappingGeocoding(components, result, countryCode);
      case EASTERN_EUROPE -> applyEasternEuropeMappingGeocoding(components, result, countryCode);
      case NORTH_AMERICA -> applyNorthAmericaMappingGeocoding(components, result);
      case ASIA_PACIFIC -> applyAsiaPacificMappingGeocoding(components, result);
      case LATIN_AMERICA -> applyLatinAmericaMappingGeocoding(components, result);
      case MIDDLE_EAST_AFRICA -> applyMiddleEastAfricaMappingGeocoding(components, result);
    }
  }

  private void applyDefaultMappingGeocoding(
      List<GeocodingResponse.AddressComponent> components,
      AddressComponents result,
      String countryCode) {

    applyWesternEuropeMappingGeocoding(components, result, countryCode);
  }

  private void applyWesternEuropeMappingGeocoding(
      List<GeocodingResponse.AddressComponent> components,
      AddressComponents result,
      String countryCode) {

    boolean isUK = "GB".equals(countryCode) || "IE".equals(countryCode);

    // First pass: Handle UK postal_town (must be city) before processing other components
    if (isUK) {
      for (GeocodingResponse.AddressComponent comp : components) {
        List<String> types = comp.getTypes();
        if (types != null && types.contains("postal_town")) {
          String longName = getLongName(comp);
          result.setCity(longName);
          logMapping("postal_town", longName, "city", longName);
          break; // Only one postal_town expected
        }
      }
    }

    // Second pass: Process all other components
    for (GeocodingResponse.AddressComponent comp : components) {
      List<String> types = comp.getTypes();
      if (types == null || types.isEmpty()) {
        continue;
      }

      String longName = getLongName(comp);
      String shortName = getShortName(comp);

      if (types.contains("street_number")) {
        setStreetAddress(result, longName, true);
        logMapping("street_number", longName, "streetAddress", result.getStreetAddress());
      } else if (types.contains("route")) {
        setStreetAddress(result, longName, false);
        logMapping("route", longName, "streetAddress", result.getStreetAddress());
      } else if (types.contains("subpremise")) {
        result.setFlatNumber(longName);
        logMapping("subpremise", longName, "flatNumber", longName);
      } else if (types.contains("postal_town") && isUK) {
        // UK/Ireland: postal_town is already processed in first pass
        // Skip here to avoid duplicate processing
        continue;
      } else if (types.contains("locality")) {
        if (isUK) {
          // UK/Ireland: locality is district/village (e.g., "Shenley Brook End")
          // Only set if city is already set (from postal_town)
          if (result.getCity() != null && result.getDistrict() == null) {
            result.setDistrict(longName);
            logMapping("locality", longName, "district", longName);
          }
        } else {
          // Other Western Europe: locality can be city if not already set
          if (result.getCity() == null) {
            result.setCity(longName);
            logMapping("locality", longName, "city", longName);
          }
        }
      } else if (types.contains("administrative_area_level_2")) {
        if (isUK) {
          // UK/Ireland: administrative_area_level_2 can be district if locality not set
          if (result.getDistrict() == null) {
            result.setDistrict(longName);
            logMapping("administrative_area_level_2", longName, "district", longName);
          }
        } else {
          // Other Western Europe: administrative_area_level_2 is district
          if (result.getDistrict() == null) {
            result.setDistrict(longName);
            logMapping("administrative_area_level_2", longName, "district", longName);
          }
        }
      } else if (types.contains("administrative_area_level_1")) {
        // UK/Ireland: administrative_area_level_1 is country component (England, Scotland, etc.)
        // Not a state - UK doesn't have states. Leave state empty.
        if (!isUK) {
          result.setState(getStateValue(longName, shortName));
          logMapping("administrative_area_level_1", longName, "state", result.getState());
        } else {
          log.debug(
              "  ⏭️  SKIPPED: administrative_area_level_1 '{}' (UK/Ireland - not a state)",
              longName);
        }
      } else if (types.contains("postal_code")) {
        result.setPostalCode(longName);
        logMapping("postal_code", longName, "postalCode", longName);
      } else if (types.contains("country")) {
        result.setCountry(longName);
        result.setCountryCode(shortName);
        logMapping("country", longName, "country/countryCode", longName + "/" + shortName);
      }
    }
  }

  private void applyEasternEuropeMappingGeocoding(
      List<GeocodingResponse.AddressComponent> components,
      AddressComponents result,
      String countryCode) {

    for (GeocodingResponse.AddressComponent comp : components) {
      List<String> types = comp.getTypes();
      if (types == null || types.isEmpty()) {
        continue;
      }

      String longName = getLongName(comp);
      String shortName = getShortName(comp);

      if (types.contains("street_number")) {
        setStreetAddress(result, longName, true);
        logMapping("street_number", longName, "streetAddress", result.getStreetAddress());
      } else if (types.contains("route")) {
        setStreetAddress(result, longName, false);
        logMapping("route", longName, "streetAddress", result.getStreetAddress());
      } else if (types.contains("subpremise")) {
        result.setFlatNumber(longName);
        logMapping("subpremise", longName, "flatNumber", longName);
      } else if (types.contains("administrative_area_level_4") || types.contains("sublocality")) {
        // Turkey/Eastern Europe: administrative_area_level_4/sublocality is mahalle/neighborhood
        // (district)
        if (result.getDistrict() == null) {
          result.setDistrict(longName);
          logMapping("administrative_area_level_4/sublocality", longName, "district", longName);
        }
      } else if (types.contains("administrative_area_level_2")) {
        // Turkey/Eastern Europe: administrative_area_level_2 is ilçe (district), not city
        // City should come from administrative_area_level_1 (il)
        if (result.getDistrict() == null) {
          result.setDistrict(longName);
          logMapping("administrative_area_level_2", longName, "district", longName);
        }
      } else if (types.contains("administrative_area_level_1")) {
        // Turkey/Eastern Europe: administrative_area_level_1 is il (city/province)
        // For Turkey: Istanbul, Ankara, etc. are cities, not states
        if ("TR".equals(countryCode)) {
          result.setCity(longName);
          logMapping("administrative_area_level_1", longName, "city", longName);
        } else {
          // Other Eastern Europe countries might use it as state
          result.setState(longName);
          logMapping("administrative_area_level_1", longName, "state", longName);
        }
      } else if (types.contains("postal_code")) {
        result.setPostalCode(longName);
        logMapping("postal_code", longName, "postalCode", longName);
      } else if (types.contains("country")) {
        result.setCountry(longName);
        result.setCountryCode(shortName);
        logMapping("country", longName, "country/countryCode", longName + "/" + shortName);
      }
    }
  }

  private void applyNorthAmericaMappingGeocoding(
      List<GeocodingResponse.AddressComponent> components, AddressComponents result) {

    for (GeocodingResponse.AddressComponent comp : components) {
      List<String> types = comp.getTypes();
      if (types == null || types.isEmpty()) {
        continue;
      }

      String longName = getLongName(comp);
      String shortName = getShortName(comp);

      if (types.contains("street_number")) {
        setStreetAddress(result, longName, true);
        logMapping("street_number", longName, "streetAddress", result.getStreetAddress());
      } else if (types.contains("route")) {
        setStreetAddress(result, longName, false);
        logMapping("route", longName, "streetAddress", result.getStreetAddress());
      } else if (types.contains("subpremise")) {
        result.setFlatNumber(longName);
        logMapping("subpremise", longName, "flatNumber", longName);
      } else if (types.contains("locality")) {
        if (result.getCity() == null) {
          result.setCity(longName);
          logMapping("locality", longName, "city", longName);
        }
      } else if (types.contains("administrative_area_level_2")) {
        if (result.getDistrict() == null) {
          result.setDistrict(longName);
          logMapping("administrative_area_level_2", longName, "district", longName);
        }
      } else if (types.contains("administrative_area_level_1")) {
        result.setState(getStateValue(longName, shortName));
        logMapping("administrative_area_level_1", longName, "state", result.getState());
      } else if (types.contains("postal_code")) {
        result.setPostalCode(longName);
        logMapping("postal_code", longName, "postalCode", longName);
      } else if (types.contains("country")) {
        result.setCountry(longName);
        result.setCountryCode(shortName);
        logMapping("country", longName, "country/countryCode", longName + "/" + shortName);
      }
    }
  }

  private void applyAsiaPacificMappingGeocoding(
      List<GeocodingResponse.AddressComponent> components, AddressComponents result) {

    for (GeocodingResponse.AddressComponent comp : components) {
      List<String> types = comp.getTypes();
      if (types == null || types.isEmpty()) {
        continue;
      }

      String longName = getLongName(comp);
      String shortName = getShortName(comp);

      if (types.contains("street_number")) {
        setStreetAddress(result, longName, true);
        logMapping("street_number", longName, "streetAddress", result.getStreetAddress());
      } else if (types.contains("route")) {
        setStreetAddress(result, longName, false);
        logMapping("route", longName, "streetAddress", result.getStreetAddress());
      } else if (types.contains("subpremise")) {
        result.setFlatNumber(longName);
        logMapping("subpremise", longName, "flatNumber", longName);
      } else if (types.contains("sublocality") || types.contains("sublocality_level_1")) {
        if (result.getDistrict() == null) {
          result.setDistrict(longName);
          logMapping("sublocality", longName, "district", longName);
        }
      } else if (types.contains("locality")) {
        if (result.getCity() == null && result.getDistrict() == null) {
          result.setDistrict(longName);
          logMapping("locality", longName, "district", longName);
        }
      } else if (types.contains("administrative_area_level_2")) {
        if (result.getCity() == null) {
          result.setCity(longName);
          logMapping("administrative_area_level_2", longName, "city", longName);
        }
      } else if (types.contains("administrative_area_level_1")) {
        result.setState(longName);
        logMapping("administrative_area_level_1", longName, "state", longName);
      } else if (types.contains("postal_code")) {
        result.setPostalCode(longName);
        logMapping("postal_code", longName, "postalCode", longName);
      } else if (types.contains("country")) {
        result.setCountry(longName);
        result.setCountryCode(shortName);
        logMapping("country", longName, "country/countryCode", longName + "/" + shortName);
      }
    }
  }

  private void applyLatinAmericaMappingGeocoding(
      List<GeocodingResponse.AddressComponent> components, AddressComponents result) {

    for (GeocodingResponse.AddressComponent comp : components) {
      List<String> types = comp.getTypes();
      if (types == null || types.isEmpty()) {
        continue;
      }

      String longName = getLongName(comp);
      String shortName = getShortName(comp);

      if (types.contains("street_number")) {
        setStreetAddress(result, longName, true);
        logMapping("street_number", longName, "streetAddress", result.getStreetAddress());
      } else if (types.contains("route")) {
        setStreetAddress(result, longName, false);
        logMapping("route", longName, "streetAddress", result.getStreetAddress());
      } else if (types.contains("subpremise")) {
        result.setFlatNumber(longName);
        logMapping("subpremise", longName, "flatNumber", longName);
      } else if (types.contains("sublocality") || types.contains("sublocality_level_1")) {
        if (result.getDistrict() == null) {
          result.setDistrict(longName);
          logMapping("sublocality", longName, "district", longName);
        }
      } else if (types.contains("locality")) {
        if (result.getCity() == null) {
          result.setCity(longName);
          logMapping("locality", longName, "city", longName);
        }
      } else if (types.contains("administrative_area_level_2")) {
        if (result.getDistrict() == null) {
          result.setDistrict(longName);
          logMapping("administrative_area_level_2", longName, "district", longName);
        }
      } else if (types.contains("administrative_area_level_1")) {
        result.setState(longName);
        logMapping("administrative_area_level_1", longName, "state", longName);
      } else if (types.contains("postal_code")) {
        result.setPostalCode(longName);
        logMapping("postal_code", longName, "postalCode", longName);
      } else if (types.contains("country")) {
        result.setCountry(longName);
        result.setCountryCode(shortName);
        logMapping("country", longName, "country/countryCode", longName + "/" + shortName);
      }
    }
  }

  private void applyMiddleEastAfricaMappingGeocoding(
      List<GeocodingResponse.AddressComponent> components, AddressComponents result) {

    for (GeocodingResponse.AddressComponent comp : components) {
      List<String> types = comp.getTypes();
      if (types == null || types.isEmpty()) {
        continue;
      }

      String longName = getLongName(comp);
      String shortName = getShortName(comp);

      if (types.contains("street_number")) {
        setStreetAddress(result, longName, true);
        logMapping("street_number", longName, "streetAddress", result.getStreetAddress());
      } else if (types.contains("route")) {
        setStreetAddress(result, longName, false);
        logMapping("route", longName, "streetAddress", result.getStreetAddress());
      } else if (types.contains("subpremise")) {
        result.setFlatNumber(longName);
        logMapping("subpremise", longName, "flatNumber", longName);
      } else if (types.contains("sublocality") || types.contains("sublocality_level_1")) {
        if (result.getDistrict() == null) {
          result.setDistrict(longName);
          logMapping("sublocality", longName, "district", longName);
        }
      } else if (types.contains("administrative_area_level_2")) {
        if (result.getCity() == null) {
          result.setCity(longName);
          logMapping("administrative_area_level_2", longName, "city", longName);
        }
      } else if (types.contains("administrative_area_level_1")) {
        result.setState(longName);
        logMapping("administrative_area_level_1", longName, "state", longName);
      } else if (types.contains("postal_code")) {
        result.setPostalCode(longName);
        logMapping("postal_code", longName, "postalCode", longName);
      } else if (types.contains("country")) {
        result.setCountry(longName);
        result.setCountryCode(shortName);
        logMapping("country", longName, "country/countryCode", longName + "/" + shortName);
      }
    }
  }

  /**
   * Set street address and detect flat number from combined format (e.g., "20/34", "20 34",
   * "20-34").
   *
   * @param result Address components result
   * @param value Street number or route value
   * @param isFirst If true, this is the first part (street number); if false, it's route
   */
  private void setStreetAddress(AddressComponents result, String value, boolean isFirst) {
    if (value == null || value.isBlank()) {
      return;
    }

    // Detect flat number pattern in street number (e.g., "20/34", "20 34", "20-34")
    // Pattern: number + separator + number (where separator is /, space, or -)
    if (isFirst && (result.getFlatNumber() == null || result.getFlatNumber().isBlank())) {
      String originalValue = value;
      String[] parts = parseStreetNumberWithFlat(value);
      if (parts != null && parts.length == 2) {
        // Found flat number pattern: set street number and flat number separately
        String streetNum = parts[0];
        String flatNum = parts[1];
        value = streetNum; // Use only street number part
        result.setFlatNumber(flatNum);
        log.debug(
            "  ✅ PARSED: Street number '{}' contains flat number → streetNumber='{}', flatNumber='{}'",
            originalValue,
            streetNum,
            flatNum);
      }
    }

    String current = result.getStreetAddress();
    if (isFirst) {
      result.setStreetAddress((value + " " + (current != null ? current : "")).trim());
    } else {
      result.setStreetAddress(((current != null ? current : "") + " " + value).trim());
    }
  }

  /**
   * Parse street number that may contain flat number (e.g., "20/34", "20 34", "20-34").
   *
   * @param streetNumber Street number value (may contain flat number)
   * @return Array with [streetNumber, flatNumber] if pattern found, null otherwise
   */
  private String[] parseStreetNumberWithFlat(String streetNumber) {
    if (streetNumber == null || streetNumber.isBlank()) {
      return null;
    }

    // Pattern: number + separator + number
    // Separators: /, space (one or more), or -
    // Examples: "20/34", "20 34", "20-34", "20/34A", "20A/34B"
    // Note: \\s in character class matches any whitespace (space, tab, etc.)
    String pattern = "^\\s*(\\d+[A-Za-z]?)\\s*[/\\s-]+\\s*(\\d+[A-Za-z]?)\\s*$";
    java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
    java.util.regex.Matcher matcher = regex.matcher(streetNumber.trim());

    if (matcher.matches() && matcher.groupCount() >= 2) {
      String streetNum = matcher.group(1);
      String flatNum = matcher.group(2);
      return new String[] {streetNum, flatNum};
    }

    return null;
  }

  /**
   * Extract flat number from route if it contains pattern (e.g., "20/34 Selvi Sokak").
   *
   * @param route Route value (may contain street number + flat number + route name)
   * @return Array with [streetNumber, flatNumber, routeName] if pattern found, null otherwise
   */
  private String[] extractFlatNumberFromRoute(String route) {
    if (route == null || route.isBlank()) {
      return null;
    }

    // Pattern: number + separator + number + space + route name
    // Examples: "20/34 Selvi Sokak", "20 34 Main Street", "20-34 Avenue"
    String pattern = "^\\s*(\\d+[A-Za-z]?)\\s*[/\\s-]+\\s*(\\d+[A-Za-z]?)\\s+(.+)$";
    java.util.regex.Pattern regex =
        java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
    java.util.regex.Matcher matcher = regex.matcher(route.trim());

    if (matcher.matches() && matcher.groupCount() >= 3) {
      String streetNum = matcher.group(1);
      String flatNum = matcher.group(2);
      String routeName = matcher.group(3);

      // Validate: flat number should be reasonable (not postal code or year)
      if (isValidFlatNumberFromRoute(streetNum, flatNum)) {
        return new String[] {streetNum, flatNum, routeName};
      }
    }

    return null;
  }

  /** Validate if route parts form a valid street number + flat number combination. */
  private boolean isValidFlatNumberFromRoute(String streetNum, String flatNum) {
    try {
      String streetClean = streetNum.replaceAll("[A-Za-z]", "");
      String flatClean = flatNum.replaceAll("[A-Za-z]", "");

      int street = Integer.parseInt(streetClean);
      int flat = Integer.parseInt(flatClean);

      // Valid: flat should be reasonable (1-9999), not equal to street, street should be reasonable
      return flat >= 1 && flat <= 9999 && street != flat && street <= 100000;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private void appendToStreetAddress(AddressComponents result, String value) {
    String current = result.getStreetAddress();
    if (current == null || !current.contains(value)) {
      result.setStreetAddress(((current != null ? current : "") + ", " + value).trim());
    }
  }

  private String getStateValue(String longText, String shortText) {
    if (longText.length() > 3 && !longText.equals(shortText)) {
      return longText;
    }
    return shortText;
  }

  private String getLongText(PlaceDetailsResponse.PlaceAddressComponent comp) {
    return comp.getLongText() != null ? comp.getLongText() : "";
  }

  private String getShortText(PlaceDetailsResponse.PlaceAddressComponent comp) {
    return comp.getShortText() != null ? comp.getShortText() : "";
  }

  private String getLongName(GeocodingResponse.AddressComponent comp) {
    return comp.getLongName() != null ? comp.getLongName() : "";
  }

  private String getShortName(GeocodingResponse.AddressComponent comp) {
    return comp.getShortName() != null ? comp.getShortName() : "";
  }

  private void logMapping(String type, String input, String field, String output) {
    log.debug("  ✅ MAPPED: {} '{}' → {}='{}'", type, input, field, output);
  }

  /**
   * Apply flat number fallback extraction from formatted address if subpremise was not found.
   *
   * @param result Address components result
   * @param formattedAddress Google's formatted address string
   * @param countryCode ISO 3166-1 alpha-2 country code
   */
  private void applyFlatNumberFallback(
      AddressComponents result, String formattedAddress, String countryCode) {
    // Only apply fallback if flatNumber is not already set from subpremise or street number parsing
    if (result.getFlatNumber() == null || result.getFlatNumber().isBlank()) {
      // Priority 1: Try combined format pattern in formattedAddress (e.g., "20/34", "20 34")
      // This handles cases where Google normalized the address but pattern is still visible
      String combinedExtracted =
          extractCombinedFormatFromFormattedAddress(formattedAddress, result.getStreetAddress());
      if (combinedExtracted != null && !combinedExtracted.isBlank()) {
        result.setFlatNumber(combinedExtracted);
        log.debug(
            "  ✅ FALLBACK: Extracted flatNumber='{}' from formattedAddress combined format",
            combinedExtracted);
        return;
      }

      // Priority 2: Try country-specific patterns via FlatNumberExtractor
      String extracted = FlatNumberExtractor.extractFlatNumber(formattedAddress, countryCode);

      // If extracted value equals street number, it's likely wrong
      if (extracted != null && !extracted.isBlank()) {
        String streetAddress = result.getStreetAddress();
        if (streetAddress != null && !streetAddress.isBlank()) {
          java.util.regex.Pattern streetNumPattern =
              java.util.regex.Pattern.compile("^\\s*(\\d+[A-Za-z]?)\\s+");
          java.util.regex.Matcher streetMatcher = streetNumPattern.matcher(streetAddress);
          if (streetMatcher.find()) {
            String streetNum = streetMatcher.group(1).replaceAll("[A-Za-z]", "");
            String extractedClean = extracted.replaceAll("[A-Za-z]", "");
            if (streetNum.equals(extractedClean)) {
              log.debug(
                  "  ⚠️  REJECTED: Extracted flatNumber '{}' equals street number '{}' - likely false positive",
                  extracted,
                  streetNum);
              extracted = null;
            }
          }
        }

        if (extracted != null && !extracted.isBlank()) {
          result.setFlatNumber(extracted);
          log.debug(
              "  ✅ FALLBACK: Extracted flatNumber='{}' from formatted address using pattern",
              extracted);
        }
      }
    } else {
      log.debug("  ⏭️  SKIPPED: flatNumber fallback (already set: '{}')", result.getFlatNumber());
    }
  }

  /**
   * Extract flat number from formattedAddress using combined format pattern (20/34, 20 34, 20-34).
   * This is more reliable than regex patterns as it checks the actual street address context.
   */
  private String extractCombinedFormatFromFormattedAddress(
      String formattedAddress, String streetAddress) {
    if (formattedAddress == null
        || formattedAddress.isBlank()
        || streetAddress == null
        || streetAddress.isBlank()) {
      return null;
    }

    // Extract street number from street address (e.g., "20 Selvi Sokak" -> "20")
    java.util.regex.Pattern streetNumPattern =
        java.util.regex.Pattern.compile("^\\s*(\\d+[A-Za-z]?)\\s+");
    java.util.regex.Matcher streetMatcher = streetNumPattern.matcher(streetAddress);
    if (!streetMatcher.find()) {
      return null;
    }

    String knownStreetNum = streetMatcher.group(1);
    String knownStreetNumClean = knownStreetNum.replaceAll("[A-Za-z]", "");

    // Search for "knownStreetNum/XX" or "knownStreetNum XX" or "knownStreetNum-XX" pattern in
    // formattedAddress
    // Example: If street number is "20", look for "20/34", "20 34", "20-34" in formattedAddress
    // Escape special regex characters in street number
    String escapedStreetNum = java.util.regex.Pattern.quote(knownStreetNum);
    String escapedStreetNumClean = java.util.regex.Pattern.quote(knownStreetNumClean);

    // Pattern: street number + separator + flat number
    String patternStr =
        "\\b("
            + escapedStreetNum
            + "|"
            + escapedStreetNumClean
            + ")\\s*[/\\s-]+\\s*(\\d+[A-Za-z]?)\\b";
    java.util.regex.Pattern pattern =
        java.util.regex.Pattern.compile(patternStr, java.util.regex.Pattern.CASE_INSENSITIVE);
    java.util.regex.Matcher matcher = pattern.matcher(formattedAddress);

    while (matcher.find()) {
      String matchedStreetNum = matcher.group(1);
      String flatNum = matcher.group(2);

      // Validate: flat number should be different from street number and reasonable
      String flatNumClean = flatNum.replaceAll("[A-Za-z]", "");
      String matchedStreetNumClean = matchedStreetNum.replaceAll("[A-Za-z]", "");

      if (!flatNumClean.equals(matchedStreetNumClean)) {
        try {
          int flat = Integer.parseInt(flatNumClean);
          int street = Integer.parseInt(matchedStreetNumClean);
          if (flat >= 1 && flat <= 9999 && flat != street && street <= 100000) {
            log.debug(
                "  ✅ Found combined format in formattedAddress: '{}' -> flatNumber='{}' (street: {})",
                matcher.group(0),
                flatNum,
                matchedStreetNum);
            return flatNum;
          }
        } catch (NumberFormatException e) {
          // Continue to next match
        }
      }
    }

    return null;
  }

  /**
   * Extract flat number from original input (e.g., "20/34 selvi sokak", "13/2A welsummer grove").
   * This is used as last resort when Google normalizes flat numbers away.
   */
  private String extractFlatNumberFromOriginalInput(
      String originalInput, String streetAddress, String countryCode) {
    if (originalInput == null || originalInput.isBlank()) {
      return null;
    }

    // Extract street number from street address (e.g., "20 Selvi Sokak" -> "20")
    String knownStreetNum = null;
    if (streetAddress != null && !streetAddress.isBlank()) {
      java.util.regex.Pattern streetNumPattern =
          java.util.regex.Pattern.compile("^\\s*(\\d+[A-Za-z]?)\\s+");
      java.util.regex.Matcher streetMatcher = streetNumPattern.matcher(streetAddress);
      if (streetMatcher.find()) {
        knownStreetNum = streetMatcher.group(1);
      }
    }

    // Pattern 1: Combined format at start (e.g., "20/34 selvi sokak", "13/2A welsummer")
    if (knownStreetNum != null && !knownStreetNum.isBlank()) {
      String escapedStreetNum = java.util.regex.Pattern.quote(knownStreetNum);
      String patternStr = "^\\s*" + escapedStreetNum + "\\s*[/\\s-]+\\s*(\\d+[A-Za-z]?)\\s+";
      java.util.regex.Pattern pattern =
          java.util.regex.Pattern.compile(patternStr, java.util.regex.Pattern.CASE_INSENSITIVE);
      java.util.regex.Matcher matcher = pattern.matcher(originalInput);
      if (matcher.find() && matcher.groupCount() >= 1) {
        String flatNum = matcher.group(1);
        String flatNumClean = flatNum.replaceAll("[A-Za-z]", "");
        String streetNumClean = knownStreetNum.replaceAll("[A-Za-z]", "");
        if (!flatNumClean.equals(streetNumClean)) {
          try {
            int flat = Integer.parseInt(flatNumClean);
            int street = Integer.parseInt(streetNumClean);
            if (flat >= 1 && flat <= 9999 && flat != street && street <= 100000) {
              return flatNum;
            }
          } catch (NumberFormatException e) {
            // Continue to next pattern
          }
        }
      }
    }

    // Pattern 2: Global combined format pattern anywhere in input (e.g., "20/34", "20 34", "20-34")
    String globalPattern = "\\b(\\d+[A-Za-z]?)\\s*[/\\s-]+\\s*(\\d+[A-Za-z]?)\\b";
    java.util.regex.Pattern pattern =
        java.util.regex.Pattern.compile(globalPattern, java.util.regex.Pattern.CASE_INSENSITIVE);
    java.util.regex.Matcher matcher = pattern.matcher(originalInput);

    while (matcher.find()) {
      String firstNum = matcher.group(1);
      String secondNum = matcher.group(2);
      String firstClean = firstNum.replaceAll("[A-Za-z]", "");
      String secondClean = secondNum.replaceAll("[A-Za-z]", "");

      // Validate: second number should be reasonable flat number
      if (!firstClean.equals(secondClean)) {
        try {
          int first = Integer.parseInt(firstClean);
          int second = Integer.parseInt(secondClean);
          if (second >= 1 && second <= 9999 && second != first && first <= 100000) {
            // If we have known street number, prefer matches where first number matches
            if (knownStreetNum != null && !knownStreetNum.isBlank()) {
              String knownClean = knownStreetNum.replaceAll("[A-Za-z]", "");
              if (firstClean.equals(knownClean)) {
                return secondNum; // Perfect match: street number matches
              }
            } else {
              // No known street number, use first match that looks valid
              return secondNum;
            }
          }
        } catch (NumberFormatException e) {
          // Continue to next match
        }
      }
    }

    // Pattern 3: Country-specific patterns via FlatNumberExtractor
    return FlatNumberExtractor.extractFlatNumber(originalInput, countryCode);
  }

  private void logFinalMapping(AddressComponents result) {
    log.debug("🔍 Final AddressComponents mapping result:");
    log.debug("  - streetAddress: '{}'", result.getStreetAddress());
    log.debug("  - flatNumber: '{}'", result.getFlatNumber());
    log.debug("  - city: '{}'", result.getCity());
    log.debug("  - state: '{}'", result.getState());
    log.debug("  - district: '{}'", result.getDistrict());
    log.debug("  - postalCode: '{}'", result.getPostalCode());
    log.debug("  - country: '{}'", result.getCountry());
    log.debug("  - countryCode: '{}'", result.getCountryCode());
  }
}
