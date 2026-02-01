package com.fabricmanagement.common.infrastructure.web.rate;

import com.fabricmanagement.common.infrastructure.web.exception.TooManyRequestsException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Aspect that enforces {@link RateLimited} on controller methods.
 *
 * <p>Uses per-principal (user) key. Unauthenticated requests are not rate-limited (handled by
 * auth). Throws {@link TooManyRequestsException} when limit exceeded (mapped to 429).
 */
@Aspect
@Component
@Slf4j
public class RateLimitAspect {

  private static final class Window {
    final Object lock = new Object();
    final CopyOnWriteArrayList<Instant> timestamps = new CopyOnWriteArrayList<>();
  }

  private final ConcurrentHashMap<String, Window> windowByKey = new ConcurrentHashMap<>();

  @Around("@annotation(rateLimited)")
  public Object enforce(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
    String key = rateLimitKey();
    if (key == null) {
      return joinPoint.proceed();
    }

    int maxRequests = rateLimited.requests();
    int windowSeconds = rateLimited.windowSeconds();
    Instant since = Instant.now().minusSeconds(windowSeconds);

    Window window = windowByKey.computeIfAbsent(key, k -> new Window());

    synchronized (window.lock) {
      window.timestamps.removeIf(t -> t.isBefore(since));
      if (window.timestamps.size() >= maxRequests) {
        log.warn("Rate limit exceeded: key={}, requests={}", key, window.timestamps.size());
        throw new TooManyRequestsException("Too many requests. Try again later.");
      }
      window.timestamps.add(Instant.now());
    }

    return joinPoint.proceed();
  }

  private static String rateLimitKey() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
      return null;
    }
    String name = auth.getName();
    return "rate:" + (name != null ? name : "anonymous");
  }
}
