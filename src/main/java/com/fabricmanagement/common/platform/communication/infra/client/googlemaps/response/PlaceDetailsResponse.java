package com.fabricmanagement.common.platform.communication.infra.client.googlemaps.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/** Google Places API (New) v1 Place Details Response DTO. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlaceDetailsResponse {
  @JsonProperty("id")
  private String id;

  @JsonProperty("formattedAddress")
  private String formattedAddress;

  @JsonProperty("addressComponents")
  private List<PlaceAddressComponent> addressComponents;

  @JsonProperty("location")
  private PlaceLocation location;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PlaceAddressComponent {
    @JsonProperty("longText")
    private String longText;

    @JsonProperty("shortText")
    private String shortText;

    @JsonProperty("types")
    private List<String> types;

    @JsonProperty("languageCode")
    private String languageCode;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PlaceLocation {
    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;
  }
}
