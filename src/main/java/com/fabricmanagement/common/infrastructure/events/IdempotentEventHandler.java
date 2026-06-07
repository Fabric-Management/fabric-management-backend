package com.fabricmanagement.common.infrastructure.events;

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
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void executeOnce(
      UUID eventId, Class<?> listenerClass, String methodName, Runnable handler) {
    String listenerId = ClassUtils.getUserClass(listenerClass).getSimpleName() + "#" + methodName;

    int inserted = repository.tryInsert(eventId, listenerId);
    if (inserted == 0) {
      log.debug("Event already processed: eventId={}, listener={}", eventId, listenerId);
      return;
    }
    handler.run();
  }
}
