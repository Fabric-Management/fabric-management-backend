package com.fabricmanagement.product.yarn.domain.reference;

import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

final class YarnCatalogueRules {

  private static final Pattern UPPER_SNAKE = Pattern.compile("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$");

  private YarnCatalogueRules() {}

  static UUID requireTenantId(UUID tenantId) {
    if (tenantId == null) {
      throw new YarnDomainException("Yarn catalogue tenantId must not be null");
    }
    return tenantId;
  }

  static String requireCode(String code) {
    if (code == null || !code.equals(code.trim()) || !UPPER_SNAKE.matcher(code).matches()) {
      throw new YarnDomainException("Yarn catalogue code must be upper snake case: " + code);
    }
    return code;
  }

  static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new YarnDomainException("Yarn catalogue name must not be blank");
    }
    return name.trim();
  }

  static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  static void requireUnchanged(String field, Object current, Object requested) {
    if (!Objects.equals(current, requested)) {
      throw new YarnDomainException(
          "Yarn catalogue semantic field is immutable after insert: " + field);
    }
  }
}
