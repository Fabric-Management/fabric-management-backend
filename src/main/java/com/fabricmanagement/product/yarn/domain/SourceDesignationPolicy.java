package com.fabricmanagement.product.yarn.domain;

/** Single source of truth for the writable source-designation invariant. */
public final class SourceDesignationPolicy {

  public static final int MAX_CODE_POINTS = 255;

  private SourceDesignationPolicy() {}

  public static boolean isBlank(String value) {
    return value != null && value.isBlank();
  }

  public static boolean isOverlength(String value) {
    return value != null && value.codePointCount(0, value.length()) > MAX_CODE_POINTS;
  }
}
