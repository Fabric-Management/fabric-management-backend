package com.fabricmanagement.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * Shared utilities for HTTP request handling (e.g. client IP).
 *
 * <p>Used by auth controllers to avoid duplicating getClientIpAddress logic (DRY).
 */
public final class WebRequestUtils {

  private WebRequestUtils() {}

  /**
   * Resolve client IP from request, honoring X-Forwarded-For when behind a proxy.
   *
   * @param request the HTTP request
   * @return client IP (first hop from X-Forwarded-For, or remote address)
   */
  public static String getClientIpAddress(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (StringUtils.hasText(xForwardedFor)) {
      return xForwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
