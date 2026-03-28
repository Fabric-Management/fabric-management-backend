package com.fabricmanagement.common.infrastructure.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import org.springframework.util.StringUtils;

/**
 * Centralised JWT token extraction from HTTP requests.
 *
 * <p>Resolution order:
 *
 * <ol>
 *   <li>{@code access_token} cookie (HttpOnly cookie support)
 *   <li>{@code Authorization: Bearer <token>} header (fallback)
 * </ol>
 *
 * @see JwtAuthenticationFilter
 * @see com.fabricmanagement.common.infrastructure.web.JwtContextInterceptor
 */
public final class JwtTokenExtractor {

  public static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";

  private JwtTokenExtractor() {}

  /**
   * Extract JWT token from request: first from cookie, then from Authorization header.
   *
   * @param request the HTTP request
   * @return JWT token string, or null if not found
   */
  public static String extract(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      String fromCookie =
          Arrays.stream(cookies)
              .filter(c -> ACCESS_TOKEN_COOKIE_NAME.equals(c.getName()))
              .findFirst()
              .map(Cookie::getValue)
              .filter(StringUtils::hasText)
              .orElse(null);
      if (fromCookie != null) {
        return fromCookie;
      }
    }

    String header = request.getHeader("Authorization");
    if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
      return header.substring(7);
    }

    return null;
  }
}
