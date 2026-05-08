package com.fabricmanagement.common.infrastructure.security;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Immutable snapshot of the authenticated user's identity, role and department memberships.
 *
 * <p>Populated by {@link JwtAuthenticationFilter} from JWT claims and stored as {@code
 * Authentication.details}. Consumed by security services (e.g. {@code PermissionEvaluator}) via
 * SpEL {@code @PreAuthorize} expressions without any additional DB round-trips.
 *
 * <p><b>JWT claim sources:</b>
 *
 * <ul>
 *   <li>{@code user_id} → {@link #userId}
 *   <li>{@code role_code} → {@link #roleCode}
 *   <li>{@code department_codes} → {@link #departmentCodes}
 *   <li>{@code primary_department} → {@link #primaryDepartmentCode}
 *   <li>{@code tenant_id} → {@link #tenantId}
 * </ul>
 */
public record AuthenticatedUserContext(
    UUID userId,
    String roleCode,
    List<String> departmentCodes,
    String primaryDepartmentCode,
    UUID tenantId,
    boolean isPlayground,
    String guestId) {

  public AuthenticatedUserContext {
    departmentCodes = departmentCodes != null ? List.copyOf(departmentCodes) : List.of();
  }

  /** Constructor for backward compatibility */
  public AuthenticatedUserContext(
      UUID userId,
      String roleCode,
      List<String> departmentCodes,
      String primaryDepartmentCode,
      UUID tenantId) {
    this(userId, roleCode, departmentCodes, primaryDepartmentCode, tenantId, false, null);
  }

  /** Returns true if the user's role matches the given code (case-insensitive). */
  public boolean hasRole(String role) {
    return role != null && role.equalsIgnoreCase(roleCode);
  }

  /** Returns true if the user's role matches any of the given codes (case-insensitive). */
  public boolean hasAnyRole(String... roles) {
    for (String role : roles) {
      if (hasRole(role)) return true;
    }
    return false;
  }

  /** Returns true if the user is a member of the given department code (case-insensitive). */
  public boolean isInDepartment(String departmentCode) {
    if (departmentCode == null) return false;
    return normalizedDepartmentCodes().contains(departmentCode.toUpperCase());
  }

  /**
   * Returns true if the user is a member of at least one department in the given set.
   *
   * <p>Uses O(1) Set-based lookup instead of O(n×m) nested stream (CR-12-05). Both the JWT-sourced
   * codes and the {@code TenantSeedService} output are uppercase, but this method is defensive by
   * normalizing to upper-case.
   */
  public boolean isInAnyDepartment(Collection<String> allowedCodes) {
    if (allowedCodes == null || allowedCodes.isEmpty()) return false;
    Set<String> normalized = normalizedDepartmentCodes();
    return allowedCodes.stream().anyMatch(code -> normalized.contains(code.toUpperCase()));
  }

  private Set<String> normalizedDepartmentCodes() {
    return departmentCodes.stream()
        .map(String::toUpperCase)
        .collect(Collectors.toUnmodifiableSet());
  }
}
