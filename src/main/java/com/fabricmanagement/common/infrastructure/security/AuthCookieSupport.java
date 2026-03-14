package com.fabricmanagement.common.infrastructure.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Adds HttpOnly auth cookies to responses (login, refresh, etc.).
 *
 * <p>Cookie attributes: HttpOnly; Secure (configurable); SameSite=Strict; Path and Max-Age per
 * cookie. Use {@code application.auth.cookie.secure=false} for local HTTP (e.g. localhost).
 */
@Component
public class AuthCookieSupport {

  /** Cookie name for refresh token (sent only to /api/auth). */
  public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";

  /** Access token cookie Max-Age in seconds (15 minutes). */
  private static final int ACCESS_TOKEN_MAX_AGE_SECONDS = 900;

  /** Refresh token cookie Max-Age in seconds (7 days). */
  private static final int REFRESH_TOKEN_MAX_AGE_SECONDS = 604800;

  @Value("${application.auth.cookie.secure:true}")
  private boolean secureCookie;

  /**
   * Add access_token and refresh_token cookies to the response. Skips any token that is null or
   * blank.
   */
  public void addAuthCookies(
      HttpServletResponse response, String accessToken, String refreshToken) {
    if (StringUtils.hasText(accessToken)) {
      Cookie accessCookie =
          new Cookie(JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE_NAME, accessToken);
      accessCookie.setHttpOnly(true);
      accessCookie.setSecure(secureCookie);
      accessCookie.setPath("/api");
      accessCookie.setMaxAge(ACCESS_TOKEN_MAX_AGE_SECONDS);
      accessCookie.setAttribute("SameSite", "Strict");
      response.addCookie(accessCookie);
    }
    if (StringUtils.hasText(refreshToken)) {
      Cookie refreshCookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, refreshToken);
      refreshCookie.setHttpOnly(true);
      refreshCookie.setSecure(secureCookie);
      refreshCookie.setPath("/api/auth");
      refreshCookie.setMaxAge(REFRESH_TOKEN_MAX_AGE_SECONDS);
      refreshCookie.setAttribute("SameSite", "Strict");
      response.addCookie(refreshCookie);
    }
  }

  /**
   * Clear access_token and refresh_token cookies (Max-Age=0). Use on refresh failure or logout so
   * the browser removes them.
   */
  public void clearAuthCookies(HttpServletResponse response) {
    Cookie accessCookie = new Cookie(JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE_NAME, "");
    accessCookie.setHttpOnly(true);
    accessCookie.setSecure(secureCookie);
    accessCookie.setPath("/api");
    accessCookie.setMaxAge(0);
    accessCookie.setAttribute("SameSite", "Strict");
    response.addCookie(accessCookie);

    Cookie refreshCookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, "");
    refreshCookie.setHttpOnly(true);
    refreshCookie.setSecure(secureCookie);
    refreshCookie.setPath("/api/auth");
    refreshCookie.setMaxAge(0);
    refreshCookie.setAttribute("SameSite", "Strict");
    response.addCookie(refreshCookie);
  }
}
