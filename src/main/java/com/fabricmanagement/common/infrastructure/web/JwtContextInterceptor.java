package com.fabricmanagement.common.infrastructure.web;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.JwtTokenExtractor;
import com.fabricmanagement.platform.auth.app.JwtService;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT Context Interceptor - Global interceptor for JWT-based tenant context management.
 *
 * <p><b>Purpose:</b> Automatically extracts JWT token from requests, validates it, and sets
 * TenantContext (tenantId, userId) for all authenticated requests.
 *
 * <p><b>Benefits:</b>
 *
 * <ul>
 *   <li>✅ Eliminates code duplication across controllers
 *   <li>✅ Centralized JWT parsing logic
 *   <li>✅ Consistent TenantContext management
 *   <li>✅ Automatic context cleanup after request
 * </ul>
 *
 * <p><b>How it works:</b>
 *
 * <ol>
 *   <li>Extracts JWT token from Authorization header
 *   <li>Validates token using JwtService
 *   <li>Extracts tenantId and userId from token claims
 *   <li>Sets TenantContext for the request thread
 *   <li>Clears TenantContext after request completion
 * </ol>
 *
 * <p><b>Public endpoints:</b> This interceptor should be excluded for public endpoints (e.g.,
 * /api/public/**, /api/auth/**). See WebMvcConfig for exclusion patterns.
 *
 * <h2>Migration Note (Faz 3):</h2>
 *
 * <p>Uses TenantRepository instead of deprecated CompanyRepository. Tenant UID is now stored
 * directly in common_tenant table.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtContextInterceptor implements HandlerInterceptor {

  private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
  private static final String[] OPTIONAL_TENANT_PATHS = {"/api/common/company-types/**"};

  private final JwtService jwtService;
  private final TenantRepository tenantRepository;

  /** Extract JWT token and set TenantContext before handler execution. */
  @Override
  public boolean preHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler) {
    // Skip OPTIONS requests (CORS preflight) - no JWT processing needed
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }

    String token = JwtTokenExtractor.extract(request);

    if (token != null && jwtService.validateToken(token)) {
      try {
        UUID userId = jwtService.getUserIdFromToken(token);
        UUID tenantId = jwtService.getTenantIdFromToken(token);

        // Set TenantContext for this request thread
        TenantContext.setCurrentTenantId(tenantId);
        TenantContext.setCurrentUserId(userId);

        // Load tenant UID from JWT token (no DB query needed)
        // ⚠️ Performance: JWT already contains tenant_uid claim, no need for DB query
        try {
          String tenantUidFromJwt = jwtService.getTenantUidFromToken(token);
          if (tenantUidFromJwt != null) {
            // After Faz 3: tenant_uid is stored directly, no need to extract from company UID
            // Backward compat: Handle old tokens with Company UID format
            String tenantUid = tenantUidFromJwt;
            if (tenantUidFromJwt.contains("-COMP-")) {
              String[] parts = tenantUidFromJwt.split("-COMP-");
              tenantUid = parts[0]; // First part is tenant UID
              log.debug(
                  "Extracted tenant UID from legacy JWT format: {} → {}",
                  tenantUidFromJwt,
                  tenantUid);
            }
            TenantContext.setCurrentTenantUid(tenantUid);
            log.trace("Tenant UID set from JWT: {} for tenantId={}", tenantUid, tenantId);
          }
        } catch (Exception e) {
          // Fallback: Load from DB if JWT claim missing (backward compatibility)
          log.debug("Tenant UID not in JWT, loading from Tenant table: tenantId={}", tenantId);
          tenantRepository
              .findById(tenantId)
              .ifPresent(
                  tenant -> {
                    String tenantUid = tenant.getUid();
                    if (tenantUid != null) {
                      TenantContext.setCurrentTenantUid(tenantUid);
                      log.debug(
                          "Tenant UID loaded from Tenant table: {} for tenantId={}",
                          tenantUid,
                          tenantId);
                    }
                  });
        }

        log.debug(
            "JWT context set: userId={}, tenantId={}, path={}",
            userId,
            tenantId,
            request.getRequestURI());
      } catch (Exception e) {
        log.warn(
            "Failed to parse JWT token for path {}: {}", request.getRequestURI(), e.getMessage());
      }
    }

    // Diagnostic log: Warn if tenant context not set (only for non-public endpoints)
    if (TenantContext.getCurrentTenantIdOrNull() == null) {
      if (isOptionalTenantRequest(request)) {
        log.debug("Tenant context not required for request {}", request.getRequestURI());
      } else {
        log.warn(
            "No tenant context found for request {} (missing or invalid JWT token)",
            request.getRequestURI());
      }
    }

    return true; // Always continue with request processing
  }

  /**
   * Clear TenantContext after request completion.
   *
   * <p>This ensures no context leaks between requests.
   */
  @Override
  public void afterCompletion(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler,
      @Nullable Exception ex) {
    // Always clear context, even if exception occurred
    TenantContext.clear();
    log.trace("TenantContext cleared for path: {}", request.getRequestURI());
  }

  private boolean isOptionalTenantRequest(HttpServletRequest request) {
    if (!"GET".equalsIgnoreCase(request.getMethod())) {
      return false;
    }
    String path = request.getRequestURI();
    for (String pattern : OPTIONAL_TENANT_PATHS) {
      if (PATH_MATCHER.match(pattern, path)) {
        return true;
      }
    }
    return false;
  }
}
