package com.fabricmanagement.common.platform.communication.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for address autocomplete suggestions. */
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
