package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException;
import com.fabricmanagement.flowboard.task.domain.EscalationLog;
import com.fabricmanagement.flowboard.task.domain.EscalationType;
import com.fabricmanagement.flowboard.task.domain.event.EscalationTriggeredEvent;
import com.fabricmanagement.flowboard.task.infra.repository.EscalationLogRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Görevlerin gecikmesi veya sorun çıkması durumunda yöneticilere haber verir. (Mondays Automations
 * / FlowBoard AUT4)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscalationService {

  private final EscalationLogRepository escalationLogRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * Görevi yapılandırılmış kişiye eskale eder. Debounce kontrolü yapar (aynı sebeple çok sık
   * bildirim gitmesini engeller).
   *
   * @param debounceHours Kaç saat içinde aynı tip eskalasyon varsa yoksayılacak (örn 24)
   * @return Eğer eskale edildiyse true, debounce'a takıldıysa false
   */
  @Transactional
  public boolean escalate(
      UUID tenantId,
      UUID taskId,
      String taskNumber,
      EscalationType type,
      UUID escalateToUserId,
      String message,
      int debounceHours) {

    // Debounce: yakın zamanda aynı tür eskalasyon atılmış mı?
    // [K1 FIX] Clock injection — deterministik test desteği
    OffsetDateTime since = OffsetDateTime.now(clock).minusHours(debounceHours);
    if (escalationLogRepository.existsRecentEscalation(taskId, type, since)) {
      log.debug("Escalation skipped due to debounce: taskId={}, type={}", taskId, type);
      return false;
    }

    // 1. Log oluştur
    var logEntry = new EscalationLog(tenantId, taskId, type, escalateToUserId, message);
    escalationLogRepository.save(logEntry);

    // 2. Event fırlat (Notification modülü dinleyip mail/socket atacak)
    var event =
        new EscalationTriggeredEvent(
            tenantId, taskId, taskNumber, type.name(), escalateToUserId, message);
    eventPublisher.publishEvent(event);

    log.info("Task escalated: taskId={}, type={}, manager={}", taskId, type, escalateToUserId);
    return true;
  }

  /**
   * [D5 FIX] Eskalasyonu çözümler — resolveEscalation.
   *
   * @param escalationLogId Çözümlenecek eskalasyon kaydı
   * @param resolvedByUserId Çözümleyen kullanıcı
   * @param resolutionNote Çözüm açıklaması
   */
  @Transactional
  public void resolveEscalation(
      UUID escalationLogId, UUID resolvedByUserId, String resolutionNote) {
    EscalationLog logEntry =
        escalationLogRepository
            .findById(escalationLogId)
            .orElseThrow(
                () ->
                    new jakarta.persistence.EntityNotFoundException(
                        "EscalationLog not found: " + escalationLogId));

    if (logEntry.getResolvedAt() != null) {
      throw new FlowBoardDomainException(
          "Escalation already resolved",
          "FLOWBOARD_ESCALATION_ALREADY_RESOLVED",
          409,
          new Object[] {escalationLogId});
    }

    logEntry.resolve(resolvedByUserId, resolutionNote, clock);
    escalationLogRepository.save(logEntry);

    log.info("Escalation resolved: id={}, resolvedBy={}", escalationLogId, resolvedByUserId);
  }
}
