package com.fabricmanagement.common.platform.communication.infra.client.googlemaps.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/** Google Geocoding API Response DTO. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeocodingResponse {
  @JsonProperty("status")
  private String status;

  @JsonProperty("error_message")
  private String errorMessage;

  @JsonProperty("results")
  private List<GeocodingResult> results;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class GeocodingResult {
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
  public static class AddressComponent {
    @JsonProperty("long_name")
    private String longName;

    @JsonProperty("short_name")
    private String shortName;

    @JsonProperty("types")
    private List<String> types;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Geometry {
    @JsonProperty("location")
    private Location location;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Location {
    @JsonProperty("lat")
    private Double lat;

    @JsonProperty("lng")
    private Double lng;
  }
}
