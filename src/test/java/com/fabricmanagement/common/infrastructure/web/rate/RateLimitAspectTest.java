package com.fabricmanagement.common.infrastructure.web.rate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.web.exception.TooManyRequestsException;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RateLimitAspectTest {

  private RateLimitAspect aspect;

  @BeforeEach
  void setUp() {
    aspect = new RateLimitAspect();
    anonymousFrom("203.0.113.10");
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  @DisplayName("an anonymous caller is refused once the endpoint's limit is used up")
  void anonymousCallerIsLimitedPerAddress() throws Throwable {
    ProceedingJoinPoint signup = joinPoint("PublicSignupController.signup(..)");

    for (int i = 0; i < 2; i++) {
      aspect.enforce(signup, limit("twoPerMinute"));
    }

    assertThatThrownBy(() -> aspect.enforce(signup, limit("twoPerMinute")))
        .isInstanceOf(TooManyRequestsException.class);
    verify(signup, times(2)).proceed();
  }

  @Test
  @DisplayName("another address has its own window")
  void anotherAddressHasItsOwnWindow() throws Throwable {
    ProceedingJoinPoint signup = joinPoint("PublicSignupController.signup(..)");
    aspect.enforce(signup, limit("twoPerMinute"));
    aspect.enforce(signup, limit("twoPerMinute"));

    anonymousFrom("198.51.100.20");
    aspect.enforce(signup, limit("twoPerMinute"));

    verify(signup, times(3)).proceed();
  }

  @Test
  @DisplayName("using one endpoint does not count against another endpoint's limit")
  void eachEndpointHasItsOwnWindow() throws Throwable {
    ProceedingJoinPoint check = joinPoint("PasswordController.checkSetupLink(..)");
    ProceedingJoinPoint signup = joinPoint("PublicSignupController.signup(..)");
    for (int i = 0; i < 5; i++) {
      aspect.enforce(check, limit("twentyPerMinute"));
    }

    aspect.enforce(signup, limit("twoPerMinute"));
    aspect.enforce(signup, limit("twoPerMinute"));

    verify(signup, times(2)).proceed();
  }

  @Test
  @DisplayName("a signed-in caller is counted per user, not per address")
  void signedInCallerIsCountedPerUser() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "olivia.hughes@example.com", null, List.of(new SimpleGrantedAuthority("USER"))));

    assertThat(RateLimitAspect.rateLimitKey("UserController.checkContactExists(..)"))
        .isEqualTo("rate:UserController.checkContactExists(..):user:olivia.hughes@example.com");
  }

  @Test
  @DisplayName("an anonymous token is keyed by address, so anonymous callers do not share a bucket")
  void anonymousTokenIsKeyedByAddress() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

    assertThat(RateLimitAspect.rateLimitKey("PublicSignupController.signup(..)"))
        .isEqualTo("rate:PublicSignupController.signup(..):ip:203.0.113.10");
  }

  @Test
  @DisplayName("the switch turns the limit off")
  void switchedOffLimitLetsEverythingThrough() throws Throwable {
    ReflectionTestUtils.setField(aspect, "enabled", false);
    ProceedingJoinPoint signup = joinPoint("PublicSignupController.signup(..)");

    for (int i = 0; i < 4; i++) {
      aspect.enforce(signup, limit("twoPerMinute"));
    }

    verify(signup, times(4)).proceed();
  }

  private static void anonymousFrom(String address) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(address);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  private static ProceedingJoinPoint joinPoint(String endpoint) {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    Signature signature = mock(Signature.class);
    when(signature.toShortString()).thenReturn(endpoint);
    when(joinPoint.getSignature()).thenReturn(signature);
    return joinPoint;
  }

  private static RateLimited limit(String method) {
    try {
      return Limits.class.getDeclaredMethod(method).getAnnotation(RateLimited.class);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings("unused")
  private static final class Limits {
    @RateLimited(requests = 2, windowSeconds = 60)
    void twoPerMinute() {}

    @RateLimited(requests = 20, windowSeconds = 60)
    void twentyPerMinute() {}
  }
}
