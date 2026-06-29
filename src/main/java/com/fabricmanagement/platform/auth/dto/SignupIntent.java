package com.fabricmanagement.platform.auth.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum SignupIntent {
  PLAYGROUND,
  TRIAL;

  @JsonCreator
  public static SignupIntent from(String value) {
    if (value == null || value.isBlank()) {
      return PLAYGROUND;
    }
    try {
      return SignupIntent.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return PLAYGROUND;
    }
  }

  @JsonValue
  public String toJson() {
    return name();
  }
}
