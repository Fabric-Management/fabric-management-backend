package com.fabricmanagement.flowboard.task.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.flowboard.task.domain.EscalationLog;
import com.fabricmanagement.flowboard.task.domain.EscalationType;
import com.fabricmanagement.flowboard.task.domain.event.EscalationTriggeredEvent;
import com.fabricmanagement.flowboard.task.infra.repository.EscalationLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EscalationService")
class EscalationServiceTest {

  @Mock private EscalationLogRepository logRepo;
  @Mock private ApplicationEventPublisher eventPublisher;

  // [K1 FIX] Clock mock eklendi
  private final Clock clock = Clock.fixed(Instant.parse("2026-03-18T12:00:00Z"), ZoneId.of("UTC"));

  private EscalationService escalationService;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID taskId = UUID.randomUUID();
  private final UUID managerId = UUID.randomUUID();

  private EscalationService createService() {
    return new EscalationService(logRepo, eventPublisher, clock);
  }

  @Nested
  @DisplayName("Happy Path")
  class HappyPath {

    @Test
    @DisplayName("Yakın zamanda eskalasyon yoksa log oluşturur ve event fırlatır")
    void escalate_whenNoRecentEscalation_createsLogAndFiresEvent() {
      escalationService = createService();
      when(logRepo.existsRecentEscalation(eq(taskId), eq(EscalationType.TIME_EXCEEDED), any()))
          .thenReturn(false);

      boolean result =
          escalationService.escalate(
              tenantId, taskId, "TSK-001", EscalationType.TIME_EXCEEDED, managerId, "Msg", 24);

      assertThat(result).isTrue();
      verify(logRepo).save(any(EscalationLog.class));
      verify(eventPublisher).publishEvent(any(EscalationTriggeredEvent.class));
    }
  }

  @Nested
  @DisplayName("Debounce")
  class Debounce {

    @Test
    @DisplayName("Debounce süresi içinde aynı tür eskalasyon varsa işlemi yoksayar")
    void escalate_whenRecentEscalationExists_debouncesAndReturnsFalse() {
      escalationService = createService();
      when(logRepo.existsRecentEscalation(eq(taskId), eq(EscalationType.TIME_EXCEEDED), any()))
          .thenReturn(true);

      boolean result =
          escalationService.escalate(
              tenantId, taskId, "TSK-001", EscalationType.TIME_EXCEEDED, managerId, "Msg", 24);

      assertThat(result).isFalse();
      verify(logRepo, never()).save(any());
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("debounceHours = 0 ile her zaman eskalasyon yapılır (anında)")
    void escalate_zeroDebounce_alwaysEscalates() {
      escalationService = createService();
      when(logRepo.existsRecentEscalation(eq(taskId), eq(EscalationType.DEADLINE_PASSED), any()))
          .thenReturn(false);

      boolean result =
          escalationService.escalate(
              tenantId, taskId, "TSK-002", EscalationType.DEADLINE_PASSED, managerId, "Msg", 0);

      assertThat(result).isTrue();
      verify(logRepo).save(any(EscalationLog.class));
    }
  }

  @Nested
  @DisplayName("Farklı Eskalasyon Tipleri")
  class DifferentTypes {

    @Test
    @DisplayName("Farklı eskalasyon tipleri birbirini engellemez")
    void escalate_differentType_doesNotDebounce() {
      escalationService = createService();
      // TIME_EXCEEDED debounced but BLOCKED_TOO_LONG is not
      when(logRepo.existsRecentEscalation(eq(taskId), eq(EscalationType.BLOCKED_TOO_LONG), any()))
          .thenReturn(false);

      boolean result =
          escalationService.escalate(
              tenantId,
              taskId,
              "TSK-003",
              EscalationType.BLOCKED_TOO_LONG,
              managerId,
              "Blocked",
              24);

      assertThat(result).isTrue();
      verify(logRepo).save(any(EscalationLog.class));
    }
  }

  @Nested
  @DisplayName("resolveEscalation")
  class ResolveEscalationTests {

    @Test
    @DisplayName("Happy path — eskalasyon bulunur ve çözümlenir")
    void resolvesSuccessfully() {
      escalationService = createService();
      UUID logId = UUID.randomUUID();
      EscalationLog logEntry =
          new EscalationLog(
              tenantId, taskId, EscalationType.DEADLINE_PASSED, managerId, "Task gecikti");
      when(logRepo.findById(logId)).thenReturn(java.util.Optional.of(logEntry));

      escalationService.resolveEscalation(logId, managerId, "Deadline uzatıldı");

      assertThat(logEntry.getResolvedAt()).isNotNull();
      assertThat(logEntry.getResolvedByUserId()).isEqualTo(managerId);
      assertThat(logEntry.getResolutionNote()).isEqualTo("Deadline uzatıldı");
      verify(logRepo).save(logEntry);
    }

    @Test
    @DisplayName("Kayıt bulunamazsa EntityNotFoundException fırlatır")
    void notFound_throwsException() {
      escalationService = createService();
      UUID logId = UUID.randomUUID();
      when(logRepo.findById(logId)).thenReturn(java.util.Optional.empty());

      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> escalationService.resolveEscalation(logId, managerId, "note"))
          .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
          .hasMessageContaining(logId.toString());
    }

    @Test
    @DisplayName("Zaten çözülmüş eskalasyon tekrar çözülemez — IllegalStateException")
    void alreadyResolved_throwsException() {
      escalationService = createService();
      UUID logId = UUID.randomUUID();
      EscalationLog logEntry =
          new EscalationLog(tenantId, taskId, EscalationType.DEADLINE_PASSED, managerId, "Test");
      logEntry.resolve(managerId, "İlk çözüm", clock);
      when(logRepo.findById(logId)).thenReturn(java.util.Optional.of(logEntry));

      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> escalationService.resolveEscalation(logId, managerId, "İkinci çözüm"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already resolved");

      verify(logRepo, never()).save(any());
    }
  }
}
