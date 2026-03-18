package com.fabricmanagement.notification.hub.app;

import com.fabricmanagement.notification.hub.domain.NotificationQueue;
import com.fabricmanagement.notification.hub.domain.NotificationQueueStatus;
import com.fabricmanagement.notification.hub.infra.repository.NotificationQueueRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Bildirim kuyruğu scheduler'ı.
 *
 * <p>Periyodik olarak PENDING kayıtları alır ve {@link NotificationItemProcessor} ile işler.
 *
 * <p>Ayrıca 5+ dakikadır PROCESSING statüsünde kalan kayıtları kurtarır (stuck recovery).
 *
 * <p>İşleme mantığı ayrı bir {@code @Service}'te tutulur — böylece Spring AOP proxy üzerinden
 * {@code @Transactional} doğru çalışır.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationQueueProcessor {

  @Value("${application.notification.queue.batch-size:50}")
  private int batchSize;

  @Value("${application.notification.queue.stuck-threshold-minutes:5}")
  private int stuckThresholdMinutes;

  private final NotificationQueueRepository queueRepo;
  private final NotificationItemProcessor itemProcessor;

  /** Ana kuyruk işleme döngüsü — her 10 saniyede bir çalışır. */
  @Scheduled(fixedDelayString = "${application.notification.queue.poll-interval-ms:10000}")
  public void processQueue() {
    // 1. Stuck recovery — PROCESSING'de takılı kalmış kayıtları kurtar
    recoverStuckItems();

    // 2. PENDING kayıtları al ve işle
    List<NotificationQueue> pending =
        queueRepo.findPendingForProcessing(
            NotificationQueueStatus.PENDING, PageRequest.of(0, batchSize));

    if (pending.isEmpty()) {
      return;
    }

    log.info("Processing {} pending notification(s)", pending.size());

    for (NotificationQueue item : pending) {
      try {
        itemProcessor.processItem(item);
      } catch (Exception ex) {
        // itemProcessor kendi içinde hataları yönetiyor,
        // ama beklenmedik bir hata olursa (ör: DB bağlantısı) diğer kayıtları engellemesin
        log.error(
            "Unexpected error processing notification queueId={}: {}",
            item.getId(),
            ex.getMessage());
      }
    }
  }

  /**
   * 5+ dakikadır PROCESSING statüsünde kalan kayıtları PENDING'e geri çeker.
   *
   * <p>Uygulama çökerse veya thread kesilirse kayıtlar PROCESSING'de kalır. Bu metot onları
   * kurtarır.
   */
  private void recoverStuckItems() {
    Instant threshold = Instant.now().minus(stuckThresholdMinutes, ChronoUnit.MINUTES);
    List<NotificationQueue> stuck = queueRepo.findStuckProcessing(threshold);

    if (!stuck.isEmpty()) {
      log.warn(
          "Recovering {} stuck PROCESSING notification(s) (threshold={}min)",
          stuck.size(),
          stuckThresholdMinutes);

      for (NotificationQueue item : stuck) {
        item.markFailed("Stuck in PROCESSING — recovered by scheduler");
        queueRepo.save(item);
        log.warn(
            "Recovered stuck notification: queueId={} event={} retryCount={}",
            item.getId(),
            item.getEventType(),
            item.getRetryCount());
      }
    }
  }
}
