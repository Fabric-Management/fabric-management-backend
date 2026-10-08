package com.fabricmanagement.platform.auth.app;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The verified facts of a request's access token that a long-lived connection must keep (CEDIT-05
 * §4.1): who, which tenant, until when, and whether it is a partner account. The token is parsed
 * here, inside the auth boundary, and never kept: only these facts leave this service.
 */
@Service
@RequiredArgsConstructor
public class LiveAuthenticationQueryService {

  private final JwtService jwtService;

  /**
   * The facts of a valid, unexpired full access token; empty for a missing, invalid, expired or MFA
   * pre-auth token, or one without user, tenant or expiry.
   */
  public Optional<VerifiedAccessToken> verify(String accessToken) {
    if (accessToken == null
        || accessToken.isBlank()
        || !jwtService.validateToken(accessToken)
        || jwtService.isPreAuthToken(accessToken)) {
      return Optional.empty();
    }
    JwtService.AuthTokenClaims claims = jwtService.extractAuthContext(accessToken);
    Optional<Instant> expiresAt = jwtService.verifiedExpiry(accessToken);
    if (claims.userId() == null || claims.tenantId() == null || expiresAt.isEmpty()) {
      return Optional.empty();
    }
    boolean partner = "PARTNER".equalsIgnoreCase(claims.userType()) || claims.partnerId() != null;
    return Optional.of(
        new VerifiedAccessToken(claims.userId(), claims.tenantId(), expiresAt.get(), partner));
  }

  /** What a verified access token says, without the token. */
  public record VerifiedAccessToken(
      UUID userId, UUID tenantId, Instant expiresAt, boolean partner) {}
}
