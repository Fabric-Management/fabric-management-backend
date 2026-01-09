package com.fabricmanagement.human.compliance.localization.app;

import java.util.UUID;

public record HrLocalizationContext(UUID tenantId, String tenantCountryCode) {

  public boolean hasCountry() {
    return tenantCountryCode != null && !tenantCountryCode.isBlank();
  }
}
