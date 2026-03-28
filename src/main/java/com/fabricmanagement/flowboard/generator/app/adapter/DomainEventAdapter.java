package com.fabricmanagement.flowboard.generator.app.adapter;

import com.fabricmanagement.flowboard.task.domain.TaskType;
import java.util.List;

/**
 * Domain event'lerini TaskTemplateContext'e çeviren ortak arayüz. Phase 4 mimari planı kapsamında
 * tasarlandı.
 *
 * @param <T> Domain Event tipi
 */
public interface DomainEventAdapter<T> {

  /** Desteklenen Domain Event tipini döner. Router'ın Map oluşturması için kullanılır. */
  Class<T> getSupportedEventType();

  /** TemplateRepository üzerinde arama yapılacak EventType adını döner. */
  String getEventTypeName();

  /** Event'ten TaskTemplateContext oluşturur. */
  TaskTemplateContext buildContext(T event);

  /**
   * Hangi TaskType'lar için şablon üretileceğine karar veren opsiyonel metot. Default davranış:
   * ilgili event için aktif tüm şablonların TaskType'larına izin ver. SalesOrder vb. özel
   * işlemlerde StockControlEngine üzerinden filtreleme yapmak için ezilir.
   */
  default List<TaskType> determineTaskTypes(T event, List<TaskType> activeTemplateTaskTypes) {
    return activeTemplateTaskTypes;
  }
}
