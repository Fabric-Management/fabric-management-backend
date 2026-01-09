package com.fabricmanagement.common.platform.communication.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for phone suggestion. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhoneSuggestion {
  private String value;
  private String source;
  private String label;
}
