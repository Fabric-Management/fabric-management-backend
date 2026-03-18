package com.fabricmanagement.flowboard.task.app.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.flowboard.automation.application.port.out.AutomationNotificationPort;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskReminder;
import com.fabricmanagement.flowboard.task.infra.repository.TaskReminderRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FlowBoardReminderJob")
class FlowBoardReminderJobTest {

  @Mock private TaskReminderRepository reminderRepo;
  @Mock private TaskRepository taskRepo;
  @Mock private AutomationNotificationPort notificationPort;
  @Spy private Clock clock = Clock.fixed(Instant.parse("2026-03-18T12:00:00Z"), ZoneId.of("UTC"));

  @InjectMocks private FlowBoardReminderJob reminderJob;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID taskId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID boardId = UUID.randomUUID();

  @Test
  @DisplayName("Pending reminder varsa task'tan boardId çözümlenir ve notifyUser çağrılır")
  void processReminder_taskExists_notifiesUserWithBoardId() {
    TaskReminder reminder = createReminder();
    Task task = mockTask(taskId, boardId);

    when(reminderRepo.findByIsSentFalseAndTriggerAtBefore(
            any(OffsetDateTime.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(reminder)));
    when(taskRepo.findAllById(any())).thenReturn(List.of(task));

    reminderJob.runReminderChecks();

    verify(notificationPort)
        .notifyUser(eq(tenantId), eq(boardId), eq(userId), eq("Test hatırlatma"), eq(taskId));
    verify(reminderRepo).save(reminder);
    assertThat(reminder.isSent()).isTrue();
  }

  @Test
  @DisplayName("Task silinmişse boardId null olarak gönderilir")
  void processReminder_taskDeleted_notifiesWithNullBoardId() {
    TaskReminder reminder = createReminder();

    when(reminderRepo.findByIsSentFalseAndTriggerAtBefore(
            any(OffsetDateTime.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(reminder)));
    when(taskRepo.findAllById(any())).thenReturn(List.of()); // task yok

    reminderJob.runReminderChecks();

    ArgumentCaptor<UUID> boardIdCaptor = ArgumentCaptor.forClass(UUID.class);
    verify(notificationPort)
        .notifyUser(
            eq(tenantId), boardIdCaptor.capture(), eq(userId), eq("Test hatırlatma"), eq(taskId));
    assertThat(boardIdCaptor.getValue()).isNull();
  }

  @Test
  @DisplayName("Pending reminder yoksa hiçbir bildirim gönderilmez")
  void processReminder_noPending_noNotification() {
    when(reminderRepo.findByIsSentFalseAndTriggerAtBefore(
            any(OffsetDateTime.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    reminderJob.runReminderChecks();

    verify(notificationPort, never()).notifyUser(any(), any(), any(), any(), any());
    verify(notificationPort, never()).notifyManager(any(), any(), any(), any());
  }

  @Test
  @DisplayName("notifyUser exception fırlatırsa döngü devam eder, diğer reminder'lar işlenir")
  void processReminder_notifyThrows_continuesProcessing() {
    TaskReminder r1 = createReminder();
    TaskReminder r2 = createReminderWithMessage("İkinci hatırlatma");

    when(reminderRepo.findByIsSentFalseAndTriggerAtBefore(
            any(OffsetDateTime.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(r1, r2)));
    when(taskRepo.findAllById(any())).thenReturn(List.of());

    // İlk çağrıda hata fırlat
    org.mockito.Mockito.doThrow(new RuntimeException("Notification service down"))
        .doNothing()
        .when(notificationPort)
        .notifyUser(any(), any(), any(), any(), any());

    reminderJob.runReminderChecks();

    // İkinci reminder yine de işlenmeye çalışılmalı
    verify(notificationPort, times(2)).notifyUser(any(), any(), any(), any(), any());
  }

  // ---- Helpers ----

  private TaskReminder createReminder() {
    return new TaskReminder(
        tenantId,
        taskId,
        userId,
        com.fabricmanagement.flowboard.task.domain.ReminderType.MANUAL,
        OffsetDateTime.now(clock).minusMinutes(10),
        null,
        "Test hatırlatma",
        com.fabricmanagement.flowboard.task.domain.ReminderChannel.IN_APP);
  }

  private TaskReminder createReminderWithMessage(String message) {
    return new TaskReminder(
        tenantId,
        taskId,
        userId,
        com.fabricmanagement.flowboard.task.domain.ReminderType.MANUAL,
        OffsetDateTime.now(clock).minusMinutes(5),
        null,
        message,
        com.fabricmanagement.flowboard.task.domain.ReminderChannel.IN_APP);
  }

  private Task mockTask(UUID id, UUID boardId) {
    Task task = org.mockito.Mockito.mock(Task.class);
    when(task.getId()).thenReturn(id);
    when(task.getBoardId()).thenReturn(boardId);
    return task;
  }
}
