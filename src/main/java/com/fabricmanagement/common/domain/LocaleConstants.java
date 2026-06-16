package com.fabricmanagement.common.domain;

import java.util.List;

public final class LocaleConstants {

  private LocaleConstants() {
    // Utility class
  }

  public static final String PLATFORM_DEFAULT_LOCALE = "EN";
  public static final String PLATFORM_DEFAULT_TIMEZONE = "Europe/London";
  public static final List<String> PLATFORM_DEFAULT_SUPPORTED_LOCALES = List.of("EN");
}
