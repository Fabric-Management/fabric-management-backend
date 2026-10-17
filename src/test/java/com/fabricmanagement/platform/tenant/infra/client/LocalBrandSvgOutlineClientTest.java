package com.fabricmanagement.platform.tenant.infra.client;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.platform.tenant.domain.exception.TenantBrandingDomainException;
import org.junit.jupiter.api.Test;

class LocalBrandSvgOutlineClientTest {
  @Test
  void aMissingRuntimeProducesAnActionableErrorInsteadOfSubstitutingArtwork() {
    var client =
        new LocalBrandSvgOutlineClient(
            "/nonexistent-branding-runtime/python", "inkscape", "fc-match");
    var failure =
        catchThrowableOfType(
            () -> client.outline("<svg xmlns='http://www.w3.org/2000/svg'/>"),
            TenantBrandingDomainException.class);
    assertThat(failure.getErrorCode()).isEqualTo("TENANT_BRANDING_CONVERSION_UNAVAILABLE");
    assertThat(failure.getHttpStatus()).isEqualTo(503);
  }
}
