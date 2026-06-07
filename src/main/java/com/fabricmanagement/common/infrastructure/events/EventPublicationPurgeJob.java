package com.fabricmanagement.common.infrastructure.events;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublicationPurgeJob {

  private final CompletedEventPublications publications;
  private final ProcessedEventRepository processedEventRepo;

  /**
   * Günlük temizlik: &lt;ul&gt; &lt;li&gt;event_publication: tamamlanmış kayıtları 30 gün sonra
   * siler &lt;li&gt;processed_event: idempotency kayıtlarını 90 gün sonra siler &lt;/ul&gt;
   */
  @Scheduled(cron = "0 0 3 * * *") // Her gün 03:00
  @Transactional
  public void purgeCompletedPublications() {
    publications.deletePublicationsOlderThan(Duration.ofDays(30));
    int purged = processedEventRepo.deleteOlderThan(Instant.now().minus(Duration.ofDays(90)));
    log.info("Purged {} processed_event entries older than 90 days", purged);
  }
}
