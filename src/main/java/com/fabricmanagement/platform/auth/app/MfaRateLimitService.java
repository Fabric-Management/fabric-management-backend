package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * MFA brute-force protection using in-memory Caffeine cache.
 *
 * <p>Tracks failed MFA verification attempts per user and locks out further attempts when the
 * threshold is exceeded. The lockout window auto-expires via cache TTL, so no DB migration is
 * needed.
 *
 * <p>Covers both TOTP and OTP (EMAIL/SMS/WHATSAPP) verification paths.
 */
@Service
@Slf4j
public class MfaRateLimitService {

  private final Cache<UUID, AtomicInteger> failedAttemptCache;
  private final int maxAttempts;
  private final long lockoutSeconds;

  public MfaRateLimitService(
      @Value("${auth.mfa.rate-limit.max-attempts:5}") int maxAttempts,
      @Value("${auth.mfa.rate-limit.lockout-seconds:900}") long lockoutSeconds) {
    this.maxAttempts = maxAttempts;
    this.lockoutSeconds = lockoutSeconds;
    this.failedAttemptCache =
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(lockoutSeconds))
            .maximumSize(50_000)
            .build();
  }

  /**
   * Check if the user is currently locked out from MFA verification.
   *
   * @param userId user to check
   * @throws IllegalArgumentException if user has exceeded max MFA attempts
   */
  public void checkRateLimit(UUID userId) {
    AtomicInteger counter = failedAttemptCache.getIfPresent(userId);
    if (counter != null && counter.get() >= maxAttempts) {
      log.warn(
          "MFA rate limit exceeded: userId={}, attempts={}, lockoutSeconds={}",
          userId,
          counter.get(),
          lockoutSeconds);
      throw new PlatformDomainException(
          "Too many failed MFA attempts. Please try again in "
              + (lockoutSeconds / 60)
              + " minutes.",
          "AUTH_MFA_RATE_LIMIT",
          429);
    }
  }

  /** Record a failed MFA verification attempt for the given user. */
  public void recordFailedAttempt(UUID userId) {
    AtomicInteger counter = failedAttemptCache.get(userId, k -> new AtomicInteger(0));
    int attempts = counter.incrementAndGet();
    log.info("MFA failed attempt recorded: userId={}, totalAttempts={}", userId, attempts);

    if (attempts >= maxAttempts) {
      log.warn(
          "MFA account locked: userId={}, attempts={}, lockoutMinutes={}",
          userId,
          attempts,
          lockoutSeconds / 60);
    }
  }

  /** Clear failed attempts after a successful MFA verification. */
  public void clearAttempts(UUID userId) {
    failedAttemptCache.invalidate(userId);
  }
}
