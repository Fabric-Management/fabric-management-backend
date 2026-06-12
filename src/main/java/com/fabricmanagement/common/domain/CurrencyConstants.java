package com.fabricmanagement.common.domain;

public final class CurrencyConstants {

  private CurrencyConstants() {
    // Utility class
  }

  /**
   * The platform's ultimate default currency (GBP). Used when no tenant-specific currency is
   * available or as a fallback for system-level operations.
   */
  public static final String PLATFORM_DEFAULT_CURRENCY = "GBP";
}
