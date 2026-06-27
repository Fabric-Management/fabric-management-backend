package com.fabricmanagement.common.infrastructure.web;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantAccessPort;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class PlaygroundQuotaInterceptor implements HandlerInterceptor {

  private static final int MAX_MUTATIONS_PER_PLAYGROUND = 5_000;

  // In-memory counter for playground quotas.
  // In a multi-node environment with Redis, this should be replaced with a Redis counter.
  private final Cache<UUID, AtomicInteger> quotaCache =
      Caffeine.newBuilder().expireAfterWrite(14, TimeUnit.DAYS).maximumSize(10_000).build();

  private final Optional<TenantAccessPort> tenantAccessPort;

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
      UUID tenantId = ctx.tenantId();
      if (tenantId != null && shouldApplyPlaygroundQuota(ctx, tenantId)) {

        // 3. Increment first, then check (atomic — prevents TOCTOU race)
        AtomicInteger counter = quotaCache.get(tenantId, k -> new AtomicInteger(0));
        int currentCount = counter.incrementAndGet();

        if (currentCount > MAX_MUTATIONS_PER_PLAYGROUND) {
          log.warn(
              "Playground quota exceeded for tenantId: {}, guestId: {}",
              tenantId,
              Optional.ofNullable(ctx.guestId()).orElse("<none>"));
          response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
          response.setContentType("application/json");
          response.setIntHeader("X-Playground-Quota-Remaining", 0);
          response
              .getWriter()
              .write(
                  "{\"error\": \"Playground quota exceeded. You have reached the 5,000 transaction limit for this session.\"}");
          return false;
        }

        response.setIntHeader(
            "X-Playground-Quota-Remaining",
            Math.max(0, MAX_MUTATIONS_PER_PLAYGROUND - currentCount));
      }
    }

    return true;
  }

  private boolean shouldApplyPlaygroundQuota(AuthenticatedUserContext ctx, UUID tenantId) {
    if (ctx.isPlayground()) {
      return true;
    }
    return tenantAccessPort.map(port -> port.isDemoMode(tenantId)).orElse(false);
  }
}
