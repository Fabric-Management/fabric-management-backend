package com.fabricmanagement.common.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.auth.app.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthCookieSupport")
class AuthCookieSupportTest {

  private static final String ACCESS_TOKEN = "access-token";
  private static final String REFRESH_TOKEN = "refresh-token";

  @Mock private JwtService jwtService;

  @Test
  @DisplayName("uses token lifetime for playground access cookie")
  void addAuthCookies_usesPlaygroundTokenLifetimeForAccessCookie() {
    AuthCookieSupport authCookieSupport = new AuthCookieSupport(jwtService);
    when(jwtService.secondsUntilExpiry(ACCESS_TOKEN)).thenReturn(7_775_999L);
    MockHttpServletResponse response = new MockHttpServletResponse();

    authCookieSupport.addAuthCookies(response, ACCESS_TOKEN, null);

    Cookie accessCookie = response.getCookie(JwtTokenExtractor.ACCESS_TOKEN_COOKIE_NAME);
    assertThat(accessCookie).isNotNull();
    assertThat(accessCookie.getMaxAge()).isEqualTo(7_775_999);
  }

  @Test
  @DisplayName("uses token lifetime for regular login access cookie and refresh constant")
  void addAuthCookies_usesLoginTokenLifetimeAndRefreshCookieConstant() {
    AuthCookieSupport authCookieSupport = new AuthCookieSupport(jwtService);
    when(jwtService.secondsUntilExpiry(ACCESS_TOKEN)).thenReturn(899L);
    MockHttpServletResponse response = new MockHttpServletResponse();

    authCookieSupport.addAuthCookies(response, ACCESS_TOKEN, REFRESH_TOKEN);

    Cookie accessCookie = response.getCookie(JwtTokenExtractor.ACCESS_TOKEN_COOKIE_NAME);
    Cookie refreshCookie = response.getCookie(AuthCookieSupport.REFRESH_TOKEN_COOKIE_NAME);
    assertThat(accessCookie).isNotNull();
    assertThat(accessCookie.getMaxAge()).isEqualTo(899);
    assertThat(refreshCookie).isNotNull();
    assertThat(refreshCookie.getMaxAge()).isEqualTo(604800);
  }

  @Test
  @DisplayName("falls back to 15 minute access cookie when token expiry cannot be read")
  void addAuthCookies_fallsBackToDefaultAccessCookieLifetimeWhenExpiryIsZero() {
    AuthCookieSupport authCookieSupport = new AuthCookieSupport(jwtService);
    when(jwtService.secondsUntilExpiry(ACCESS_TOKEN)).thenReturn(0L);
    MockHttpServletResponse response = new MockHttpServletResponse();

    authCookieSupport.addAuthCookies(response, ACCESS_TOKEN, null);

    Cookie accessCookie = response.getCookie(JwtTokenExtractor.ACCESS_TOKEN_COOKIE_NAME);
    assertThat(accessCookie).isNotNull();
    assertThat(accessCookie.getMaxAge()).isEqualTo(900);
  }
}
