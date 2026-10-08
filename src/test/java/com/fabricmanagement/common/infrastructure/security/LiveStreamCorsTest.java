package com.fabricmanagement.common.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * CEDIT-05 L21: the live stream adds no CORS rule of its own. The production allowlist that covers
 * {@code /api/v1/**} answers a trusted origin with credentials and refuses an untrusted one. (The
 * development profiles allow every origin pattern; that is existing configuration, not this
 * endpoint's.)
 */
class LiveStreamCorsTest {

  @Test
  @DisplayName("L21: production CORS of the live path: credentials, trusted origin only")
  void productionCorsOfTheLivePath() {
    CorsConfigurationSource source =
        new SecurityConfig(null, null, null).productionCorsConfigurationSource();
    MockHttpServletRequest request =
        new MockHttpServletRequest(
            "GET", "/api/v1/sales/orders/" + UUID.randomUUID() + "/live-events");

    CorsConfiguration cors = source.getCorsConfiguration(request);

    assertThat(cors).isNotNull();
    assertThat(cors.getAllowCredentials()).isTrue();
    assertThat(cors.checkOrigin("https://evil.example")).isNull();
    assertThat(cors.checkOrigin("null")).isNull();
    String trusted =
        Optional.ofNullable(System.getenv("CORS_ALLOWED_ORIGINS"))
            .filter(origins -> !origins.isBlank())
            .map(origins -> origins.split(",")[0].trim())
            .orElse("https://app.fabricmanagement.com");
    assertThat(cors.checkOrigin(trusted)).isEqualTo(trusted);
  }
}
