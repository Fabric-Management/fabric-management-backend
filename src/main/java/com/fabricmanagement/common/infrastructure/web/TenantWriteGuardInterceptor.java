package com.fabricmanagement.common.infrastructure.web;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantAccessPort;
import com.fabricmanagement.common.infrastructure.web.exception.TenantReadOnlyException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

/** Central write guard for read-only expired tenants. */
@Component
@RequiredArgsConstructor
public class TenantWriteGuardInterceptor implements HandlerInterceptor {

  private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
  private static final List<String> DEFAULT_ALLOW_PATHS =
      List.of(
          "/api/v1/auth/**",
          "/api/v1/public/**",
          "/api/v1/subscriptions/*/activate",
          "/api/v1/subscriptions/**/activate");

  private final Optional<TenantAccessPort> tenantAccessPort;
  private final Optional<TrialReadOnlyProperties> trialReadOnlyProperties;

  @Override
  public boolean preHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler) {
    if (isSafeMethod(request.getMethod())) {
      return true;
    }

    UUID tenantId = TenantContext.getCurrentTenantIdOrNull();
    if (tenantId == null) {
      return true;
    }

    if (isAllowedPath(request.getRequestURI())) {
      return true;
    }

    boolean writable = tenantAccessPort.map(port -> port.isWritable(tenantId)).orElse(true);
    if (!writable) {
      throw new TenantReadOnlyException();
    }
    return true;
  }

  private boolean isSafeMethod(String method) {
    return "GET".equalsIgnoreCase(method)
        || "HEAD".equalsIgnoreCase(method)
        || "OPTIONS".equalsIgnoreCase(method);
  }

  private boolean isAllowedPath(String path) {
    return DEFAULT_ALLOW_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path))
        || trialReadOnlyProperties
            .map(TrialReadOnlyProperties::getAllowPaths)
            .orElse(List.of())
            .stream()
            .anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
  }
}
