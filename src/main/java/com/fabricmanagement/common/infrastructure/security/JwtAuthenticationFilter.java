package com.fabricmanagement.common.infrastructure.security;

import com.fabricmanagement.common.platform.auth.app.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT Authentication Filter.
 *
 * <p>Reads the JWT token from the {@code Authorization: Bearer <token>} header, validates it, and
 * populates Spring Security's {@link SecurityContextHolder} with an authenticated principal and the
 * user's roles as {@link SimpleGrantedAuthority} objects.
 *
 * <p>Without this filter, {@code @PreAuthorize} annotations see every request as anonymous, causing
 * {@code AccessDeniedException} even when a valid JWT is present.
 *
 * <h2>Role mapping:</h2>
 *
 * <p>JWT claims contain {@code role_code} (e.g., {@code "ADMIN"}, {@code "PLATFORM_ADMIN"}). Spring
 * Security's {@code hasRole("ADMIN")} check prepends {@code "ROLE_"} automatically, so we store
 * authorities as {@code "ROLE_ADMIN"}, {@code "ROLE_PLATFORM_ADMIN"}, etc.
 *
 * <p>Additionally, the raw {@code role_code} is added without prefix so that {@code hasAuthority}
 * checks also work.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      filterChain.doFilter(request, response);
      return;
    }

    if (SecurityContextHolder.getContext().getAuthentication() != null) {
      filterChain.doFilter(request, response);
      return;
    }

    String token = extractToken(request);
    if (token == null || !jwtService.validateToken(token)) {
      filterChain.doFilter(request, response);
      return;
    }

    if (jwtService.isPreAuthToken(token)) {
      log.warn(
          "Attempt to use MFA pre-auth token as an access token for path: {}",
          request.getRequestURI());
      filterChain.doFilter(request, response);
      return;
    }

    try {
      UUID userId = jwtService.getUserIdFromToken(token);
      String roleCode = jwtService.getRoleCodeFromToken(token);
      String userType = jwtService.getUserTypeFromToken(token);

      List<SimpleGrantedAuthority> authorities = buildAuthorities(roleCode, userType);

      // Build the user context so downstream security beans (e.g. ProductionAccessService)
      // can evaluate role + department without additional DB calls.
      List<String> departmentCodes = jwtService.getDepartmentCodesFromToken(token);
      String primaryDepartment = jwtService.getPrimaryDepartmentFromToken(token);
      AuthenticatedUserContext userContext =
          new AuthenticatedUserContext(userId, roleCode, departmentCodes, primaryDepartment);

      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(userId.toString(), null, authorities);
      authentication.setDetails(userContext);

      // Expose partner_id on the request so service/AOP layers can enforce isolation
      if ("PARTNER".equals(userType)) {
        UUID partnerId = jwtService.getPartnerIdFromToken(token);
        if (partnerId != null) {
          request.setAttribute("partnerId", partnerId);
        }
      }

      SecurityContextHolder.getContext().setAuthentication(authentication);

      log.debug(
          "JWT authentication set: userId={}, userType={}, roles={}, departments={}, path={}",
          userId,
          userType,
          authorities,
          departmentCodes,
          request.getRequestURI());

    } catch (Exception e) {
      log.warn(
          "Failed to set JWT authentication for path {}: {}",
          request.getRequestURI(),
          e.getMessage());
    }

    filterChain.doFilter(request, response);
  }

  private String extractToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
      return header.substring(7);
    }
    return null;
  }

  /**
   * Build Spring Security authority list from role code and user type.
   *
   * <ul>
   *   <li>{@code ROLE_<code>} — for {@code hasRole()} checks
   *   <li>{@code <code>} — for {@code hasAuthority()} checks
   *   <li>{@code ROLE_PARTNER_USER} — added for all partner tokens regardless of role_code
   * </ul>
   */
  private List<SimpleGrantedAuthority> buildAuthorities(String roleCode, String userType) {
    List<SimpleGrantedAuthority> authorities = new ArrayList<>();

    if ("PARTNER".equals(userType)) {
      authorities.add(new SimpleGrantedAuthority("ROLE_PARTNER_USER"));
      authorities.add(new SimpleGrantedAuthority("PARTNER_USER"));
    }

    if (roleCode == null) return authorities;

    String upper = roleCode.toUpperCase();
    authorities.add(new SimpleGrantedAuthority("ROLE_" + upper));
    authorities.add(new SimpleGrantedAuthority(upper));
    return authorities;
  }
}
