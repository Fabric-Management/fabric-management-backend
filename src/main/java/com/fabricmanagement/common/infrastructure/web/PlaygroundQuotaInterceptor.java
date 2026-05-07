package com.fabricmanagement.common.infrastructure.web;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor to enforce rate/quota limits specifically for PLAYGROUND sessions.
 *
 * <p>Prevents abuse of the playground environments by capping the number of state-mutating requests
 * (POST, PUT, DELETE, PATCH) a single playground tenant can make.
 */
@Component
@Slf4j
public class PlaygroundQuotaInterceptor implements HandlerInterceptor {

  private static final int MAX_MUTATIONS_PER_PLAYGROUND = 500;

  // In-memory counter for playground quotas.
  // In a multi-node environment with Redis, this should be replaced with a Redis counter.
  private final Cache<UUID, AtomicInteger> quotaCache =
      Caffeine.newBuilder().expireAfterWrite(14, TimeUnit.DAYS).maximumSize(10_000).build();

  @Override
  public boolean preHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler)
      throws Exception {

    // 1. Only track state-mutating requests
    String method = request.getMethod();
    if (!("POST".equalsIgnoreCase(method)
        || "PUT".equalsIgnoreCase(method)
        || "PATCH".equalsIgnoreCase(method)
        || "DELETE".equalsIgnoreCase(method))) {
      return true; // Let GET/OPTIONS through
    }

    // 2. Check if the user is in a PLAYGROUND session
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AuthenticatedUserContext ctx) {
      if (ctx.isPlayground() && ctx.tenantId() != null) {

        // 3. Increment and check quota
        AtomicInteger counter = quotaCache.get(ctx.tenantId(), k -> new AtomicInteger(0));

        if (counter.get() >= MAX_MUTATIONS_PER_PLAYGROUND) {
          log.warn(
              "Playground quota exceeded for tenantId: {} (Guest: {})",
              ctx.tenantId(),
              ctx.guestId());
          response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
          response.setContentType("application/json");
          response.setIntHeader("X-Playground-Quota-Remaining", 0);
          response
              .getWriter()
              .write(
                  "{\"error\": \"Playground quota exceeded. You have reached the 500 transaction limit for this session.\"}");
          return false;
        }

        int currentCount = counter.incrementAndGet();
        response.setIntHeader(
            "X-Playground-Quota-Remaining",
            Math.max(0, MAX_MUTATIONS_PER_PLAYGROUND - currentCount));
      }
    }

    return true;
  }
}
