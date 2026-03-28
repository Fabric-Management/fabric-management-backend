package com.fabricmanagement.platform.communication.infra.client.googlemaps.response;

import lombok.Data;

/**
 * Helper class for extracting and holding address components. Used internally by GoogleMapsClient
 * for mapping Google API responses.
 */
@Data
public class AddressComponents {
  private String streetAddress;
  private String flatNumber; // Apartment/Flat number (subpremise)
  private String city;
  private String state;
  private String district;
  private String postalCode;
  private String country;
  private String countryCode;
}
