package com.fabricmanagement.common.infrastructure.web;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.auth.app.JwtService;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * JWT Context Interceptor - Global interceptor for JWT-based tenant context management.
 *
 * <p><b>Purpose:</b> Automatically extracts JWT token from requests, validates it,
 * and sets TenantContext (tenantId, userId) for all authenticated requests.</p>
 *
 * <p><b>Benefits:</b></p>
 * <ul>
 *   <li>✅ Eliminates code duplication across controllers</li>
 *   <li>✅ Centralized JWT parsing logic</li>
 *   <li>✅ Consistent TenantContext management</li>
 *   <li>✅ Automatic context cleanup after request</li>
 * </ul>
 *
 * <p><b>How it works:</b></p>
 * <ol>
 *   <li>Extracts JWT token from Authorization header</li>
 *   <li>Validates token using JwtService</li>
 *   <li>Extracts tenantId and userId from token claims</li>
 *   <li>Sets TenantContext for the request thread</li>
 *   <li>Clears TenantContext after request completion</li>
 * </ol>
 *
 * <p><b>Public endpoints:</b> This interceptor should be excluded for public endpoints
 * (e.g., /api/public/**, /api/auth/**). See WebMvcConfig for exclusion patterns.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtContextInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;
    private final CompanyRepository companyRepository;

    /**
     * Extract JWT token and set TenantContext before handler execution.
     */
    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, 
                             @NonNull HttpServletResponse response, 
                             @NonNull Object handler) {
        // Skip OPTIONS requests (CORS preflight) - no JWT processing needed
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = extractTokenFromRequest(request);

        if (token != null && jwtService.validateToken(token)) {
            try {
                UUID userId = jwtService.getUserIdFromToken(token);
                UUID tenantId = jwtService.getTenantIdFromToken(token);

                // Set TenantContext for this request thread
                TenantContext.setCurrentTenantId(tenantId);
                TenantContext.setCurrentUserId(userId);

                // Load tenant UID from JWT token (no DB query needed)
                // ⚠️ Performance: JWT already contains tenant_uid claim, no need for DB query
                // ⚠️ CRITICAL: Extract tenant UID if JWT contains Company UID format (backward compatibility)
                try {
                    String tenantUidFromJwt = jwtService.getTenantUidFromToken(token);
                    if (tenantUidFromJwt != null) {
                        // Extract tenant UID from Company UID if it contains module code
                        // Format: {TENANT_UID}-COMP-{UUID} → {TENANT_UID}
                        String tenantUid = tenantUidFromJwt;
                        if (tenantUidFromJwt.contains("-COMP-")) {
                            String[] parts = tenantUidFromJwt.split("-COMP-");
                            tenantUid = parts[0]; // First part is tenant UID
                            log.debug("Extracted tenant UID from JWT Company UID: {} → {}", tenantUidFromJwt, tenantUid);
                        }
                        TenantContext.setCurrentTenantUid(tenantUid);
                        log.trace("Tenant UID set from JWT: {} for tenantId={}", tenantUid, tenantId);
                    }
                } catch (Exception e) {
                    // Fallback: Load from DB if JWT claim missing (backward compatibility)
                    log.debug("Tenant UID not in JWT, loading from DB: tenantId={}", tenantId);
                    companyRepository.findById(tenantId)
                        .ifPresent(company -> {
                            String companyUid = company.getUid();
                            if (companyUid != null) {
                                // Extract tenant UID from Company UID if it contains module code
                                // Format: {TENANT_UID}-COMP-{UUID} → {TENANT_UID}
                                String tenantUid = companyUid;
                                if (companyUid.contains("-COMP-")) {
                                    String[] parts = companyUid.split("-COMP-");
                                    tenantUid = parts[0]; // First part is tenant UID
                                }
                                TenantContext.setCurrentTenantUid(tenantUid);
                                log.debug("Tenant UID loaded from DB: {} (from company UID: {}) for tenantId={}", 
                                    tenantUid, companyUid, tenantId);
                            }
                        });
                }

                log.debug("JWT context set: userId={}, tenantId={}, path={}", 
                    userId, tenantId, request.getRequestURI());
            } catch (Exception e) {
                log.warn("Failed to parse JWT token for path {}: {}", 
                    request.getRequestURI(), e.getMessage());
            }
        }

        // Diagnostic log: Warn if tenant context not set (only for non-public endpoints)
        if (TenantContext.getCurrentTenantIdOrNull() == null) {
            log.warn("No tenant context found for request {} (missing or invalid JWT token)", 
                request.getRequestURI());
        }

        return true; // Always continue with request processing
    }

    /**
     * Clear TenantContext after request completion.
     * <p>This ensures no context leaks between requests.</p>
     */
    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, 
                                @NonNull HttpServletResponse response, 
                                @NonNull Object handler, 
                                @Nullable Exception ex) {
        // Always clear context, even if exception occurred
        TenantContext.clear();
        log.trace("TenantContext cleared for path: {}", request.getRequestURI());
    }

    /**
     * Extract JWT token from Authorization header.
     *
     * @param request HTTP request
     * @return JWT token string, or null if not found
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}

