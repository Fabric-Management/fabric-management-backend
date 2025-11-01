package com.fabricmanagement.common.platform.communication.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for address autocomplete suggestions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutocompleteResponse {

    private List<AutocompletePrediction> predictions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AutocompletePrediction {
        private String placeId;
        private String description;
        private String mainText;
        private String secondaryText;
    }
}

