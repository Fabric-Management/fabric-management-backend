package com.fabricmanagement.common.infrastructure.events;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProcessedEventPurgeJob {

  private final ProcessedEventRepository processedEventRepository;

  /** processed_event: 90 gün sonrası temizle (event_publication retention'dan uzun) */
  @Scheduled(cron = "0 30 3 * * *") // Her gün 03:30
  @Transactional
  public void purgeOldEntries() {
    int deleted =
        processedEventRepository.deleteOlderThan(Instant.now().minus(Duration.ofDays(90)));
    if (deleted > 0) {
      log.info("Purged {} processed_event entries older than 90 days", deleted);
    }
  }
}
