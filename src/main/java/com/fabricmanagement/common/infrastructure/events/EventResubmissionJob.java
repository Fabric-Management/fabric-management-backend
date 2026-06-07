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
   *
   * <p><b>OBSERVABILITY FAZI NOTU (Poison-Message / DLQ):</b> Şu anki yapıda, işlenemeyen (zehirli)
   * bir event sonsuza dek her 5 dakikada bir yeniden denenir ve event_publication tablosunda takılı
   * kalır. Bu durum fail-closed prensibi için iyi olsa da operasyonel körlüğe yol açabilir.
   * Observability fazının ilk adımı olarak:
   *
   * <ul>
   *   <li>Micrometer ile "incomplete publication count/duration" metrikleri dışa açılmalı.
   *   <li>Bu metriklere alert kurularak, sistemde takılı kalan event'ler görünür kılınmalı.
   *   <li>İlerleyen safhalarda, belirli bir yeniden deneme (retry) limitini aşan event'leri bir
   *       Dead-Letter Queue (DLQ) tablosuna taşıyıp oradan "completed" işaretleyen otomatik bir
   *       mekanizma eklenebilir.
   * </ul>
   */
  @Scheduled(fixedDelayString = "${modulith.events.resubmit.interval-ms:300000}")
  public void resubmitStaleEvents() {
    log.debug("Checking for incomplete event publications older than 60s...");
    incompleteEvents.resubmitIncompletePublicationsOlderThan(Duration.ofSeconds(60));
  }
}
