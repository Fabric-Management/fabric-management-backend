package com.fabricmanagement.common.platform.communication.infra.client.googlemaps.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Google Places API (New) v1 Autocomplete Response DTO.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlacesAutocompleteResponse {
    @JsonProperty("suggestions")
    private List<Suggestion> suggestions;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Suggestion {
        @JsonProperty("placePrediction")
        private PlacePrediction placePrediction;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlacePrediction {
        @JsonProperty("placeId")
        private String placeId;

        @JsonProperty("text")
        private Text text;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Text {
        @JsonProperty("text")
        private String text;

        public String getFullText() {
            return text;
        }
    }
}

