package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.platform.auth.infra.repository.RefreshTokenRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodically removes revoked and expired refresh tokens that are older than the retention
 * threshold (30 days). Keeps the table lean while preserving recent audit trail.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupJob {

  private static final long RETENTION_DAYS = 30;

  private final RefreshTokenRepository refreshTokenRepository;

  /** Runs every 6 hours. */
  @Scheduled(cron = "0 0 */6 * * *")
  @Transactional
  public void cleanupExpiredTokens() {
    Instant threshold = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
    int deleted = refreshTokenRepository.deleteRevokedAndExpiredBefore(threshold);

    if (deleted > 0) {
      log.info(
          "Refresh token cleanup: deleted {} revoked tokens older than {} days",
          deleted,
          RETENTION_DAYS);
    } else {
      log.debug("Refresh token cleanup: no tokens to clean up");
    }
  }
}
