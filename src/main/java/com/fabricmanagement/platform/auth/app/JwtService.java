package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.dto.UserDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * JWT Service - Token generation and validation.
 *
 * <p>Generates JWT tokens with comprehensive claims:
 *
 * <ul>
 *   <li>sub: contactValue (email/phone)
 *   <li>tenant_id: UUID (references common_tenant)
 *   <li>tenant_uid: Human-readable
 *   <li>user_id: UUID
 *   <li>user_uid: Human-readable
 *   <li>organization_id: UUID (references common_organization)
 *   <li>department: String
 * </ul>
 *
 * <h2>Token Types:</h2>
 *
 * <ul>
 *   <li>Access Token: 15 minutes (configurable)
 *   <li>Refresh Token: 7 days (configurable)
 * </ul>
 *
 * <h2>Migration Note (Faz 3):</h2>
 *
 * <p>Uses TenantRepository and OrganizationRepository instead of deprecated CompanyRepository.
 */
@Service
@Slf4j
public class JwtService {

  private final TenantRepository tenantRepository;
  private final OrganizationRepository organizationRepository;
  private final SecretKey secretKey;
  private final long accessTokenExpiration;

  public JwtService(
      TenantRepository tenantRepository,
      OrganizationRepository organizationRepository,
      @Value("${application.jwt.secret}") String secret,
      @Value("${application.jwt.expiration:900000}") long accessTokenExpiration) {
    this.tenantRepository = tenantRepository;
    this.organizationRepository = organizationRepository;
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTokenExpiration = accessTokenExpiration;

    log.info("JwtService initialized - accessTokenExpiration: {}ms", accessTokenExpiration);
  }

  public String generateAccessToken(User user) {
    String contactValue = getVerifiedContactOrThrow(user);
    log.debug("Generating access token for user: {}", contactValue);

    Map<String, Object> claims = buildCommonClaims(user);

    // Load organization for organization_type (category equivalent)
    Optional<Organization> orgOpt =
        organizationRepository.findByTenantIdAndId(user.getTenantId(), user.getOrganizationId());
    orgOpt.ifPresent(o -> claims.put("organization_type", o.getOrganizationType().name()));

    return buildToken(contactValue, claims, accessTokenExpiration);
  }

  /**
   * Generates a JWT token for a playground guest session. Includes is_playground and guest_id
   * claims.
   */
  public String generatePlaygroundAccessToken(User user, String guestId) {
    String contactValue = getVerifiedContactOrThrow(user);
    return generatePlaygroundAccessToken(user, guestId, contactValue);
  }

  /**
   * Overload that accepts a pre-resolved contactValue. Use this when the caller has already fetched
   * the contact (e.g. via a direct query) to avoid LazyInitializationException on
   * User.userContacts.
   */
  public String generatePlaygroundAccessToken(User user, String guestId, String contactValue) {
    log.debug(
        "Generating playground access token for user: {}, guestId: {}", contactValue, guestId);

    Map<String, Object> claims = buildCommonClaims(user);
    claims.put("is_playground", true);
    claims.put("guest_id", guestId);

    return buildToken(contactValue, claims, accessTokenExpiration);
  }

  private String getVerifiedContactOrThrow(User user) {
    return user.getAnyVerifiedContact()
        .map(contact -> contact.getContactValue())
        .orElseThrow(
            () ->
                new PlatformDomainException(
                    "User has no verified contact", "AUTH_NO_VERIFIED_CONTACT", 400));
  }

  private Map<String, Object> buildCommonClaims(User user) {
    Optional<Tenant> tenantOpt = tenantRepository.findById(user.getTenantId());
    String tenantUid =
        tenantOpt.map(Tenant::getUid).orElseGet(() -> extractTenantUid(user.getUid()));

    Map<String, Object> claims = new HashMap<>();
    claims.put("tenant_id", user.getTenantId().toString());
    claims.put("tenant_uid", tenantUid);
    claims.put("user_id", user.getId().toString());
    claims.put("user_uid", user.getUid());
    claims.put("organization_id", user.getOrganizationId().toString());
    claims.put("firstName", user.getFirstName());
    claims.put("lastName", user.getLastName());

    List<String> departmentCodes = new ArrayList<>();
    String primaryDepartmentCode = null;
    String department = null;
    for (var ud : user.getUserDepartments()) {
      if (ud.getDepartment() == null) {
        continue;
      }
      String code = ud.getDepartment().getDepartmentCode();
      if (code != null) {
        departmentCodes.add(code);
      }
      if (Boolean.TRUE.equals(ud.getIsPrimary())) {
        primaryDepartmentCode = ud.getDepartment().getDepartmentCode();
        department = ud.getDepartment().getDepartmentName();
      }
    }
    claims.put("department", department);
    if (!departmentCodes.isEmpty()) {
      claims.put("department_codes", departmentCodes);
    }
    if (primaryDepartmentCode != null) {
      claims.put("primary_department", primaryDepartmentCode);
    }
    if (user.getRole() != null) {
      claims.put("role_id", user.getRole().getId().toString());
      claims.put("role", user.getRole().getRoleName());
      claims.put("role_code", user.getRole().getRoleCode());
    }
    return claims;
  }

  private String buildToken(String subject, Map<String, Object> claims, long expirationMillis) {
    return Jwts.builder()
        .subject(subject)
        .claims(claims)
        .issuedAt(Date.from(Instant.now()))
        .expiration(Date.from(Instant.now().plusMillis(expirationMillis)))
        .signWith(secretKey)
        .compact();
  }

  /** Generates a short-lived token granting permission to verify MFA only. */
  public String generatePreAuthToken(User user) {
    String contactValue =
        user.getAnyVerifiedContact()
            .map(contact -> contact.getContactValue())
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "User has no verified contact", "AUTH_NO_VERIFIED_CONTACT", 400));

    log.debug("Generating MFA pre-auth token for user: {}", contactValue);

    Map<String, Object> claims = new HashMap<>();
    claims.put("user_id", user.getId().toString());
    claims.put("tenant_id", user.getTenantId().toString());
    claims.put("mfa_pre_auth", true);

    return Jwts.builder()
        .subject(contactValue)
        .claims(claims)
        .issuedAt(Date.from(Instant.now()))
        // 5 minutes expiration for MFA token
        .expiration(Date.from(Instant.now().plusMillis(300000)))
        .signWith(secretKey)
        .compact();
  }

  /**
   * Generate access token for a partner portal user.
   *
   * <p>Adds {@code user_type: "PARTNER"} and {@code partner_id} claims so the security layer can
   * enforce partner-scoped isolation. Department claims are omitted — partner users have no
   * internal department assignments.
   *
   * @param user Partner user entity
   * @param partnerId TradingPartner UUID the user belongs to
   * @return signed JWT access token
   */
  public String generatePartnerAccessToken(User user, UUID partnerId) {
    String contactValue =
        user.getAnyVerifiedContact()
            .map(contact -> contact.getContactValue())
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "User has no verified contact", "AUTH_NO_VERIFIED_CONTACT", 400));

    log.debug(
        "Generating partner access token for user: {}, partnerId: {}", contactValue, partnerId);

    Optional<Tenant> tenantOpt = tenantRepository.findById(user.getTenantId());
    String tenantUid =
        tenantOpt.map(Tenant::getUid).orElseGet(() -> extractTenantUid(user.getUid()));

    Map<String, Object> claims = new HashMap<>();
    claims.put("user_type", "PARTNER");
    claims.put("partner_id", partnerId.toString());
    claims.put("tenant_id", user.getTenantId().toString());
    claims.put("tenant_uid", tenantUid);
    claims.put("user_id", user.getId().toString());
    claims.put("user_uid", user.getUid());
    claims.put("organization_id", user.getOrganizationId().toString());
    claims.put("firstName", user.getFirstName());
    claims.put("lastName", user.getLastName());
    if (user.getRole() != null) {
      claims.put("role_id", user.getRole().getId().toString());
      claims.put("role", user.getRole().getRoleName());
      claims.put("role_code", user.getRole().getRoleCode());
    }

    return Jwts.builder()
        .subject(contactValue)
        .claims(claims)
        .issuedAt(Date.from(Instant.now()))
        .expiration(Date.from(Instant.now().plusMillis(accessTokenExpiration)))
        .signWith(secretKey)
        .compact();
  }

  /**
   * Extract {@code user_type} claim from token.
   *
   * @param token JWT access token
   * @return "PARTNER" or "INTERNAL", or null if absent
   */
  public String getUserTypeFromToken(String token) {
    return extractClaims(token).get("user_type", String.class);
  }

  /**
   * Extract {@code partner_id} claim from token.
   *
   * @param token JWT access token
   * @return partner UUID or null if not a partner token
   */
  public UUID getPartnerIdFromToken(String token) {
    String val = extractClaims(token).get("partner_id", String.class);
    return val != null ? UUID.fromString(val) : null;
  }

  public String generateRefreshToken(User user) {
    String contactValue =
        user.getAnyVerifiedContact()
            .map(contact -> contact.getContactValue())
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "User has no verified contact", "AUTH_NO_VERIFIED_CONTACT", 400));

    log.debug("Generating refresh token for user: {}", contactValue);

    return UUID.randomUUID().toString();
  }

  /**
   * Generate access token from UserDto (overload for DTO).
   *
   * <p>Note: UserDto should be created from User entity that has contacts/departments loaded.
   *
   * @param userDto User DTO (must have contactValue and department populated from entity)
   * @return JWT access token
   */
  public String generateAccessToken(UserDto userDto) {
    // UserDto no longer has contactValue - should use User entity directly
    // This method kept for backward compatibility but should use User entity
    // version
    throw new UnsupportedOperationException(
        "Use generateAccessToken(User) instead. UserDto no longer includes contactValue/department fields.");
  }

  /**
   * Generate refresh token from UserDto (overload for DTO).
   *
   * <p>Note: Should use User entity directly instead.
   */
  public String generateRefreshToken(UserDto userDto) {
    // UserDto no longer has contactValue - should use User entity directly
    throw new UnsupportedOperationException(
        "Use generateRefreshToken(User) instead. UserDto no longer includes contactValue field.");
  }

  public boolean validateToken(String token) {
    try {
      Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
      return true;
    } catch (Exception e) {
      log.trace("Invalid JWT token: {}", e.getMessage());
      return false;
    }
  }

  public String getContactValueFromToken(String token) {
    Claims claims = extractClaims(token);
    return claims.getSubject();
  }

  public UUID getTenantIdFromToken(String token) {
    Claims claims = extractClaims(token);
    String tenantId = claims.get("tenant_id", String.class);
    if (tenantId == null || tenantId.isBlank()) {
      throw new PlatformDomainException(
          "JWT missing tenant_id claim", "AUTH_JWT_MISSING_CLAIM", 401);
    }
    return UUID.fromString(tenantId);
  }

  public String getTenantUidFromToken(String token) {
    Claims claims = extractClaims(token);
    return claims.get("tenant_uid", String.class);
  }

  public UUID getUserIdFromToken(String token) {
    Claims claims = extractClaims(token);
    String userId = claims.get("user_id", String.class);
    if (userId == null || userId.isBlank()) {
      throw new PlatformDomainException("JWT missing user_id claim", "AUTH_JWT_MISSING_CLAIM", 401);
    }
    return UUID.fromString(userId);
  }

  /** Check if token is a pre-auth token */
  public boolean isPreAuthToken(String token) {
    Claims claims = extractClaims(token);
    Boolean isPreAuth = claims.get("mfa_pre_auth", Boolean.class);
    return Boolean.TRUE.equals(isPreAuth);
  }

  public String getUserUidFromToken(String token) {
    Claims claims = extractClaims(token);
    return claims.get("user_uid", String.class);
  }

  /**
   * Get organization ID from JWT token.
   *
   * @param token JWT token
   * @return Organization UUID
   */
  public UUID getOrganizationIdFromToken(String token) {
    Claims claims = extractClaims(token);
    // Try new claim first, fall back to old claim
    String orgId = claims.get("organization_id", String.class);
    if (orgId == null) {
      orgId = claims.get("company_id", String.class);
    }
    return UUID.fromString(orgId);
  }

  public String getDepartmentFromToken(String token) {
    Claims claims = extractClaims(token);
    return claims.get("department", String.class);
  }

  /**
   * Extract the list of department codes from a JWT token.
   *
   * <p>Maps to the {@code department_codes} claim populated by {@link #generateAccessToken(User)}.
   * Used by {@link com.fabricmanagement.common.infrastructure.security.JwtAuthenticationFilter} to
   * build an {@link com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext}.
   *
   * @param token JWT access token
   * @return unmodifiable list of department codes; empty list if claim is absent
   */
  public List<String> getDepartmentCodesFromToken(String token) {
    Claims claims = extractClaims(token);
    Object raw = claims.get("department_codes");
    if (raw instanceof List<?> list) {
      return list.stream()
          .filter(item -> item instanceof String)
          .map(item -> (String) item)
          .toList();
    }
    return List.of();
  }

  /**
   * Extract the primary department code from a JWT token.
   *
   * <p>Maps to the {@code primary_department} claim.
   *
   * @param token JWT access token
   * @return primary department code, or {@code null} if absent
   */
  public String getPrimaryDepartmentFromToken(String token) {
    Claims claims = extractClaims(token);
    return claims.get("primary_department", String.class);
  }

  /**
   * Extract the user's role_code from a JWT token.
   *
   * <p>Used by {@link com.fabricmanagement.common.infrastructure.security.JwtAuthenticationFilter}
   * to populate Spring Security's {@code SecurityContextHolder} with proper authorities.
   *
   * @param token JWT access token
   * @return role_code string (e.g. {@code "ADMIN"}, {@code "PLATFORM_ADMIN"}), or {@code null}
   */
  public String getRoleCodeFromToken(String token) {
    Claims claims = extractClaims(token);
    String roleCode = claims.get("role_code", String.class);
    if (roleCode != null) return roleCode;
    // Fallback: try role display name
    return claims.get("role", String.class);
  }

  public boolean isPlaygroundToken(String token) {
    Claims claims = extractClaims(token);
    Boolean isPlayground = claims.get("is_playground", Boolean.class);
    return Boolean.TRUE.equals(isPlayground);
  }

  public String getGuestIdFromToken(String token) {
    Claims claims = extractClaims(token);
    return claims.get("guest_id", String.class);
  }

  /**
   * Extract all authentication-relevant claims from a JWT token in a single parse operation.
   *
   * <p>This avoids redundant {@link #extractClaims(String)} calls when the filter needs multiple
   * claim values from the same token (CR2-5).
   *
   * @param token JWT access token
   * @return record containing all claims needed by {@link
   *     com.fabricmanagement.common.infrastructure.security.JwtAuthenticationFilter}
   */
  public AuthTokenClaims extractAuthContext(String token) {
    Claims claims = extractClaims(token);

    // user_id
    String userIdStr = claims.get("user_id", String.class);
    UUID userId = (userIdStr != null && !userIdStr.isBlank()) ? UUID.fromString(userIdStr) : null;

    // role_code (with fallback)
    String roleCode = claims.get("role_code", String.class);
    if (roleCode == null) {
      roleCode = claims.get("role", String.class);
    }

    // user_type
    String userType = claims.get("user_type", String.class);

    // department_codes
    List<String> departmentCodes = List.of();
    Object rawDeptCodes = claims.get("department_codes");
    if (rawDeptCodes instanceof List<?> list) {
      departmentCodes =
          list.stream().filter(item -> item instanceof String).map(item -> (String) item).toList();
    }

    // primary_department
    String primaryDepartment = claims.get("primary_department", String.class);

    // tenant_id
    UUID tenantId = null;
    String tenantIdStr = claims.get("tenant_id", String.class);
    if (tenantIdStr != null && !tenantIdStr.isBlank()) {
      try {
        tenantId = UUID.fromString(tenantIdStr);
      } catch (IllegalArgumentException ignored) {
        // malformed tenant_id
      }
    }

    // playground claims
    boolean isPlayground = Boolean.TRUE.equals(claims.get("is_playground", Boolean.class));
    String guestId = claims.get("guest_id", String.class);

    // partner_id
    UUID partnerId = null;
    String partnerIdStr = claims.get("partner_id", String.class);
    if (partnerIdStr != null && !partnerIdStr.isBlank()) {
      try {
        partnerId = UUID.fromString(partnerIdStr);
      } catch (IllegalArgumentException ignored) {
        // malformed partner_id
      }
    }

    return new AuthTokenClaims(
        userId,
        roleCode,
        userType,
        departmentCodes,
        primaryDepartment,
        tenantId,
        isPlayground,
        guestId,
        partnerId);
  }

  /** Bundle of all authentication-relevant claims extracted from a JWT token in a single parse. */
  public record AuthTokenClaims(
      UUID userId,
      String roleCode,
      String userType,
      List<String> departmentCodes,
      String primaryDepartment,
      UUID tenantId,
      boolean isPlayground,
      String guestId,
      UUID partnerId) {}

  private Claims extractClaims(String token) {
    return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
  }

  private String extractTenantUid(String userUid) {
    if (userUid == null) {
      return null;
    }
    String[] parts = userUid.split("-");
    if (parts.length >= 2) {
      return parts[0] + "-" + parts[1];
    }
    return null;
  }
}
