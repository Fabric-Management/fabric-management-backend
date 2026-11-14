package com.fabricmanagement.common.infrastructure.web.rate;

import com.fabricmanagement.common.infrastructure.web.exception.TooManyRequestsException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Aspect that enforces {@link RateLimited} on controller methods.
 *
 * <p>Each annotated endpoint keeps its own window per caller: a signed-in caller is counted per
 * user, an anonymous caller per connecting address (see {@link #rateLimitKey(String)}). Throws
 * {@link TooManyRequestsException} when the limit is exceeded (mapped to 429).
 *
 * <p>{@code application.rate-limit.enabled=false} switches the limit off. Only the test profile
 * does that: every MockMvc request comes from the same loopback address, and integration test
 * classes share one cached context, so unrelated tests would otherwise exhaust each other's
 * windows.
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

  @Value("${application.rate-limit.enabled:true}")
  private boolean enabled = true;

  @Around("@annotation(rateLimited)")
  public Object enforce(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
    if (!enabled) {
      return joinPoint.proceed();
    }
    String key = rateLimitKey(joinPoint.getSignature().toShortString());
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

  /**
   * The window key: the endpoint plus the caller, so a caller's use of one endpoint does not count
   * against another endpoint's limit. Signed-in callers are limited per user. Callers without a
   * login (the public signup) used to have no key and therefore no limit at all; they are now
   * limited per connecting address. That is the socket address, not the first {@code
   * X-Forwarded-For} hop, because the client writes that header and could rotate it to dodge the
   * limit. Behind a reverse proxy the server must be set up to resolve the real client address (for
   * example {@code server.forward-headers-strategy=native}), otherwise every caller shares the
   * proxy's bucket. An anonymous token counts as no login, otherwise every anonymous caller would
   * share one bucket.
   */
  static String rateLimitKey(String endpoint) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    boolean signedIn =
        auth != null
            && auth.isAuthenticated()
            && auth.getPrincipal() != null
            && !(auth instanceof AnonymousAuthenticationToken);
    if (signedIn) {
      String name = auth.getName();
      return "rate:" + endpoint + ":user:" + (name != null ? name : "anonymous");
    }
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      return "rate:" + endpoint + ":ip:" + attrs.getRequest().getRemoteAddr();
    }
    return null;
  }

  @Scheduled(fixedRate = 300_000)
  public void evictStaleEntries() {
    Instant threshold = Instant.now().minusSeconds(300);
    int removed =
        (int)
            windowByKey.entrySet().stream()
                .filter(
                    entry -> {
                      Window w = entry.getValue();
                      synchronized (w.lock) {
                        w.timestamps.removeIf(t -> t.isBefore(threshold));
                        return w.timestamps.isEmpty();
                      }
                    })
                .peek(entry -> windowByKey.remove(entry.getKey()))
                .count();
    if (removed > 0) {
      log.debug("Rate limit cleanup: evicted {} stale entries", removed);
    }
  }
}
