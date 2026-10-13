package com.fabricmanagement.common.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class JwtTokenExtractorTest {

  @Test
  void explicitBearerHeaderWinsOverTheAccessCookie() {
    // A demo tab acting as a persona sends the persona's token while the browser still sends the
    // signed-in owner's cookie: the request is the persona's.
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(JwtTokenExtractor.ACCESS_TOKEN_COOKIE_NAME, "owner-token"));
    request.addHeader("Authorization", "Bearer persona-token");

    assertThat(JwtTokenExtractor.extract(request)).isEqualTo("persona-token");
  }

  @Test
  void accessCookieIsUsedWithoutABearerHeader() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(JwtTokenExtractor.ACCESS_TOKEN_COOKIE_NAME, "owner-token"));
    request.addHeader("Authorization", "Basic not-a-bearer");

    assertThat(JwtTokenExtractor.extract(request)).isEqualTo("owner-token");
  }

  @Test
  void nothingWithoutHeaderOrCookie() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(JwtTokenExtractor.ACCESS_TOKEN_COOKIE_NAME, ""));

    assertThat(JwtTokenExtractor.extract(request)).isNull();
  }
}
