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
 *   <li>{@code Authorization: Bearer <token>} header
 *   <li>{@code access_token} cookie (HttpOnly cookie support)
 * </ol>
 *
 * <p>An explicit header wins over the cookie: a demo tab acting as a persona sends that persona's
 * token as a header while the browser still sends the signed-in owner's cookie. The browser's own
 * requests never carry an {@code Authorization} header, so for them nothing changes.
 *
 * @see JwtAuthenticationFilter
 * @see com.fabricmanagement.common.infrastructure.web.JwtContextInterceptor
 */
public final class JwtTokenExtractor {

  public static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";

  private JwtTokenExtractor() {}

  /**
   * Extract JWT token from request: first from the Authorization header, then from the cookie.
   *
   * @param request the HTTP request
   * @return JWT token string, or null if not found
   */
  public static String extract(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
      return header.substring(7);
    }

    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    return Arrays.stream(cookies)
        .filter(c -> ACCESS_TOKEN_COOKIE_NAME.equals(c.getName()))
        .findFirst()
        .map(Cookie::getValue)
        .filter(StringUtils::hasText)
        .orElse(null);
  }
}
