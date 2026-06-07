package com.fabricmanagement.common.infrastructure.events;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventResubmissionJob {

  private final IncompleteEventPublications incompleteEvents;

  /**
   * Her 5 dakikada bir, 60 saniyeden eski incomplete publication'ları yeniden teslim eder.
   *
   * <p>Restart-only republish yetersizdir — uygulama haftalarca restart olmayabilir. Bu job, outbox
   * poller'ı rolünü üstlenir.
   *
   * <p>60-sn eşiği, hâlâ işlenmekte olan event'lerin yanlışlıkla yeniden teslim edilmesini önler.
   */
  @Scheduled(fixedDelayString = "${modulith.events.resubmit.interval-ms:300000}")
  public void resubmitStaleEvents() {
    log.debug("Checking for incomplete event publications older than 60s...");
    incompleteEvents.resubmitIncompletePublicationsOlderThan(Duration.ofSeconds(60));
  }
}
