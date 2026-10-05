package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.sales.common.exception.OrderDomainException;

/** Small text rules shared by the order's value objects: blank is absent, length is bounded. */
final class Text {

  private Text() {}

  static String trimmed(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  static String limited(String value, int max, String message) {
    if (value != null && value.length() > max) {
      throw new OrderDomainException(message);
    }
    return value;
  }
}
