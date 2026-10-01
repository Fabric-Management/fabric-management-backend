package com.fabricmanagement.common.infrastructure.events;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotentEventHandler {

  private final ProcessedEventRepository repository;
  private final MeterRegistry meterRegistry;

  /**
   * Executes the handler only if this (eventId, listenerId) pair hasn't been processed. Records the
   * processing atomically in the same transaction.
   *
   * <p><b>listener_id türetme kuralı:</b> {@code ClassName#methodName} formatında otomatik
   * türetilir. Elle string yazmak yasak — sinsi duplicate listener_id bug'ı oluşturur.
   *
   * @param eventId DomainEvent.getEventId() — globally unique
   * @param listenerClass handler'ı çağıran sınıf
   * @param methodName handler metot adı
   * @param handler yan-etki üreten iş mantığı
   */
  // REQUIRED, not REQUIRES_NEW: every @ApplicationModuleListener already runs in its own
  // REQUIRES_NEW transaction, so a second one here made each async listener hold TWO pooled
  // connections — under an event burst (hundreds of stock-unit events from one seed) the pool
  // deadlocked: every thread held its first connection while waiting 30 s for a second. Joining
  // the listener's transaction keeps the processed-marker and the side effects atomic (a failing
  // handler rolls both back, so the retry/republish path still sees the event as unprocessed).
  @Transactional(propagation = Propagation.REQUIRED)
  public void executeOnce(
      UUID eventId, Class<?> listenerClass, String methodName, Runnable handler) {
    String listenerId = ClassUtils.getUserClass(listenerClass).getSimpleName() + "#" + methodName;

    int inserted = repository.tryInsert(eventId, listenerId);
    if (inserted == 0) {
      log.debug("Event already processed: eventId={}, listener={}", eventId, listenerId);
      meterRegistry.counter("events.processing.duplicate", "listener", listenerId).increment();
      return;
    }

    try {
      handler.run();
      meterRegistry.counter("events.processing.success", "listener", listenerId).increment();
    } catch (Exception e) {
      meterRegistry.counter("events.processing.failure", "listener", listenerId).increment();
      throw e;
    }
  }
}
