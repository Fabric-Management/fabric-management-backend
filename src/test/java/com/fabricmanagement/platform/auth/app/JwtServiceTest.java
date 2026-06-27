package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.UserContact;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JwtService")
class JwtServiceTest {

  private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-hs256";
  private static final long ACCESS_TOKEN_EXPIRATION = 900_000L;
  private static final long PLAYGROUND_TOKEN_EXPIRATION = 1_209_600_000L;

  private final TenantQueryPort tenantQueryPort = org.mockito.Mockito.mock(TenantQueryPort.class);
  private final OrganizationRepository organizationRepository =
      org.mockito.Mockito.mock(OrganizationRepository.class);
  private final JwtService jwtService =
      new JwtService(
          tenantQueryPort,
          organizationRepository,
          SECRET,
          ACCESS_TOKEN_EXPIRATION,
          PLAYGROUND_TOKEN_EXPIRATION);

  @Test
  @DisplayName("regular access token uses application.jwt.expiration")
  void generateAccessToken_usesRegularAccessTokenExpiration() {
    User user = buildUser();
    when(tenantQueryPort.findById(user.getTenantId()))
        .thenReturn(
            Optional.of(new TenantReference(user.getTenantId(), "TEST-001", "Test", "TENANT")));
    when(organizationRepository.findByTenantIdAndId(user.getTenantId(), user.getOrganizationId()))
        .thenReturn(Optional.empty());

    Claims claims = parse(jwtService.generateAccessToken(user));

    assertTokenTtl(claims, ACCESS_TOKEN_EXPIRATION);
    assertThat(claims.get("is_playground", Boolean.class)).isNull();
  }

  @Test
  @DisplayName("playground access token uses application.jwt.playground-expiration")
  void generatePlaygroundAccessToken_usesPlaygroundTokenExpiration() {
    User user = buildUser();
    when(tenantQueryPort.findById(user.getTenantId()))
        .thenReturn(
            Optional.of(new TenantReference(user.getTenantId(), "TEST-001", "Test", "TENANT")));

    Claims claims =
        parse(jwtService.generatePlaygroundAccessToken(user, "guest-123", "guest@example.com"));

    assertTokenTtl(claims, PLAYGROUND_TOKEN_EXPIRATION);
    assertThat(claims.get("is_playground", Boolean.class)).isTrue();
    assertThat(claims.get("guest_id", String.class)).isEqualTo("guest-123");
  }

  @Test
  @DisplayName("secondsUntilExpiry returns remaining regular access token lifetime")
  void secondsUntilExpiry_returnsRegularAccessTokenLifetime() {
    User user = buildUser();
    when(tenantQueryPort.findById(user.getTenantId()))
        .thenReturn(
            Optional.of(new TenantReference(user.getTenantId(), "TEST-001", "Test", "TENANT")));
    when(organizationRepository.findByTenantIdAndId(user.getTenantId(), user.getOrganizationId()))
        .thenReturn(Optional.empty());

    long secondsUntilExpiry = jwtService.secondsUntilExpiry(jwtService.generateAccessToken(user));

    assertThat(secondsUntilExpiry).isBetween(895L, 900L);
  }

  @Test
  @DisplayName("secondsUntilExpiry returns remaining playground access token lifetime")
  void secondsUntilExpiry_returnsPlaygroundAccessTokenLifetime() {
    User user = buildUser();
    when(tenantQueryPort.findById(user.getTenantId()))
        .thenReturn(
            Optional.of(new TenantReference(user.getTenantId(), "TEST-001", "Test", "TENANT")));

    long secondsUntilExpiry =
        jwtService.secondsUntilExpiry(
            jwtService.generatePlaygroundAccessToken(user, "guest-123", "guest@example.com"));

    assertThat(secondsUntilExpiry).isBetween(1_209_595L, 1_209_600L);
  }

  @Test
  @DisplayName("secondsUntilExpiry returns zero for expired token")
  void secondsUntilExpiry_returnsZeroForExpiredToken() {
    String expiredToken =
        Jwts.builder()
            .subject("guest@example.com")
            .issuedAt(Date.from(Instant.now().minusSeconds(120)))
            .expiration(Date.from(Instant.now().minusSeconds(60)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();

    assertThat(jwtService.secondsUntilExpiry(expiredToken)).isZero();
  }

  @Test
  @DisplayName("secondsUntilExpiry returns zero for token without exp")
  void secondsUntilExpiry_returnsZeroForTokenWithoutExpiration() {
    String tokenWithoutExpiration =
        Jwts.builder()
            .subject("guest@example.com")
            .issuedAt(Date.from(Instant.now()))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();

    assertThat(jwtService.secondsUntilExpiry(tokenWithoutExpiration)).isZero();
  }

  private static void assertTokenTtl(Claims claims, long expectedMillis) {
    long actualMillis = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();

    assertThat(actualMillis).isBetween(expectedMillis - 1_000L, expectedMillis + 1_000L);
  }

  private static Claims parse(String token) {
    SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }

  private static User buildUser() {
    UUID tenantId = UUID.randomUUID();
    UUID organizationId = UUID.randomUUID();

    Contact contact =
        Contact.builder()
            .contactType(ContactType.EMAIL)
            .contactValue("guest@example.com")
            .isVerified(true)
            .build();

    User user = User.create("Demo", "Guest", organizationId);
    user.setId(UUID.randomUUID());
    user.setTenantId(tenantId);
    user.setUid("TEST-001-USER-00001");
    user.getUserContacts().add(UserContact.builder().contact(contact).isDefault(true).build());
    return user;
  }
}
