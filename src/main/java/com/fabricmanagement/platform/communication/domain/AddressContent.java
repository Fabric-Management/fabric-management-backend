package com.fabricmanagement.platform.communication.domain;

import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import java.util.Locale;

/** Provider-independent postal content, validated for HTTP and internal callers alike. */
public record AddressContent(
    String streetAddress,
    String addressLine2,
    String city,
    String state,
    String district,
    String postalCode,
    String country,
    String countryCode,
    AddressType addressType,
    String label) {

  public AddressContent {
    streetAddress = text(streetAddress, 500, true, "streetAddress");
    addressLine2 = text(addressLine2, 255, false, "addressLine2");
    city = text(city, 100, true, "city");
    state = text(state, 100, false, "state");
    district = text(district, 100, false, "district");
    postalCode = text(postalCode, 20, false, "postalCode");
    country = text(country, 100, true, "country");
    countryCode = text(countryCode, 2, false, "countryCode");
    if (countryCode != null) {
      countryCode = countryCode.toUpperCase(Locale.ROOT);
      if (!countryCode.matches("[A-Z]{2}")) {
        throw new PlatformDomainException(
            "Country code must contain two letters", "ADDRESS_COUNTRY_CODE_INVALID", 400);
      }
    }
    label = text(label, 100, true, "label");
    if (addressType == null) {
      throw new PlatformDomainException("Address type is required", "ADDRESS_TYPE_REQUIRED", 400);
    }
  }

  private static String text(String value, int maximum, boolean required, String field) {
    String result = value == null ? null : value.trim();
    if (result == null || result.isEmpty()) {
      if (required) {
        throw new PlatformDomainException(
            "Address field is required: " + field, "ADDRESS_FIELD_REQUIRED", 400);
      }
      return null;
    }
    if (result.length() > maximum) {
      throw new PlatformDomainException(
          "Address field is too long: " + field, "ADDRESS_FIELD_TOO_LONG", 400);
    }
    return result;
  }
}
