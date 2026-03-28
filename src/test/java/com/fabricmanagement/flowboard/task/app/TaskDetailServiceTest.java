package com.fabricmanagement.flowboard.task.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskAction;
import com.fabricmanagement.flowboard.task.domain.TaskChecklist;
import com.fabricmanagement.flowboard.task.domain.TaskComment;
import com.fabricmanagement.flowboard.task.domain.TaskTimeEntry;
import com.fabricmanagement.flowboard.task.infra.repository.TaskAttachmentRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskChecklistRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskCommentRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskTimeEntryRepository;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TaskDetailService")
class TaskDetailServiceTest {

  @Mock private TaskChecklistRepository checklistRepo;
  @Mock private TaskCommentRepository commentRepo;
  @Mock private TaskRepository taskRepo;
  @Mock private UserFacade userFacade;
  @Mock private TaskTimeEntryRepository timeEntryRepo;
  @Mock private TaskAttachmentRepository attachmentRepo;
  @Mock private TaskActivityService activityService;

  @org.mockito.Spy
  private java.time.Clock clock =
      java.time.Clock.fixed(java.time.Instant.now(), java.time.ZoneId.of("UTC"));

  @InjectMocks private TaskDetailService detailService;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID taskId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  @Nested
  @DisplayName("Timer")
  class Timer {

    @Test
    @DisplayName("Aktif timer yoksa startTimer yeni başlatır")
    void startTimer_whenNoActiveTimer_startsNew() {
      when(timeEntryRepo.findActiveTimerByUserId(userId)).thenReturn(Optional.empty());

      TaskTimeEntry entry = detailService.startTimer(tenantId, taskId, userId);

      verify(timeEntryRepo).save(any(TaskTimeEntry.class));
      verify(activityService)
          .logActivity(
              eq(tenantId),
              eq(taskId),
              eq(userId),
              eq(TaskAction.TIMER_STARTED),
              isNull(),
              isNull(),
              isNull());
      assertThat(entry.getTaskId()).isEqualTo(taskId);
    }

    @Test
    @DisplayName("Aktif timer varsa önce onu durdurur sonra yenisini başlatır")
    void startTimer_whenActiveTimerExists_stopsOldAndStartsNew() {
      TaskTimeEntry activeTimer = new TaskTimeEntry(tenantId, UUID.randomUUID(), userId, clock);
      when(timeEntryRepo.findActiveTimerByUserId(userId)).thenReturn(Optional.of(activeTimer));

      TaskTimeEntry newEntry = detailService.startTimer(tenantId, taskId, userId);

      verify(timeEntryRepo, times(2)).save(any(TaskTimeEntry.class));
      assertThat(activeTimer.getEndedAt()).isNotNull();
      assertThat(newEntry.getTaskId()).isEqualTo(taskId);
    }

    @Test
    @DisplayName("stopTimer — farklı task'ın timer'ı durdurulmaya çalışılırsa hata fırlatır")
    void stopTimer_wrongTask_throwsException() {
      TaskTimeEntry activeTimer = new TaskTimeEntry(tenantId, UUID.randomUUID(), userId, clock);
      when(timeEntryRepo.findActiveTimerByUserId(userId)).thenReturn(Optional.of(activeTimer));

      assertThatThrownBy(() -> detailService.stopTimer(tenantId, taskId, userId))
          .isInstanceOf(
              com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException.class)
          .hasMessageContaining("different task");
    }

    @Test
    @DisplayName("[D3 FIX] stopTimer — farklı tenant'ın timer'ı durdurulmaya çalışılırsa hata")
    void stopTimer_wrongTenant_throwsException() {
      UUID otherTenant = UUID.randomUUID();
      TaskTimeEntry activeTimer = new TaskTimeEntry(otherTenant, taskId, userId, clock);
      when(timeEntryRepo.findActiveTimerByUserId(userId)).thenReturn(Optional.of(activeTimer));

      assertThatThrownBy(() -> detailService.stopTimer(tenantId, taskId, userId))
          .isInstanceOf(
              com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException.class)
          .hasMessageContaining("tenant");
    }
  }

  @Nested
  @DisplayName("Comment")
  class Comment {

    @Test
    @DisplayName("Yorum ekler ve activity log kaydeder")
    void addComment_savesAndLogs() {
      Task task = org.mockito.Mockito.mock(Task.class);
      org.mockito.Mockito.when(task.getId()).thenReturn(taskId);
      org.mockito.Mockito.when(task.getTenantId()).thenReturn(tenantId);
      when(taskRepo.findById(taskId)).thenReturn(Optional.of(task));

      TaskComment comment = detailService.addComment(tenantId, taskId, userId, "Test yorum", null);

      verify(commentRepo).save(any(TaskComment.class));
      verify(activityService)
          .logActivity(
              eq(tenantId),
              eq(taskId),
              eq(userId),
              eq(TaskAction.COMMENTED),
              isNull(),
              isNull(),
              isNull());
      assertThat(comment.getTaskId()).isEqualTo(taskId);
    }
  }

  @Nested
  @DisplayName("Checklist")
  class Checklist {

    @Test
    @DisplayName("Checklist ekler ve activity log kaydeder [K2 FIX]")
    void addChecklist_savesAndLogs() {
      TaskChecklist checklist =
          detailService.addChecklist(tenantId, taskId, "Alt görev", 1, userId);

      verify(checklistRepo).save(any(TaskChecklist.class));
      verify(activityService)
          .logActivity(
              eq(tenantId),
              eq(taskId),
              eq(userId),
              eq(TaskAction.CHECKLIST_ADDED),
              isNull(),
              eq("Alt görev"),
              isNull());
      assertThat(checklist.getTaskId()).isEqualTo(taskId);
    }

    @Test
    @DisplayName("[D2 FIX] Farklı tenant'ın checklistini tamamlamaya çalışırsa hata")
    void completeChecklist_wrongTenant_throwsException() {
      UUID otherTenant = UUID.randomUUID();
      TaskChecklist checklist = new TaskChecklist(otherTenant, taskId, "item", 1);
      UUID checklistId = UUID.randomUUID();
      when(checklistRepo.findById(checklistId)).thenReturn(Optional.of(checklist));

      assertThatThrownBy(
              () -> detailService.completeChecklist(tenantId, taskId, checklistId, userId))
          .isInstanceOf(
              com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException.class)
          .hasMessageContaining("tenant");
    }

    @Test
    @DisplayName("Checklist tamamlar — Clock ile completedAt set edilir ve activity loglanır")
    void completeChecklist_happyPath_completesAndLogs() {
      TaskChecklist checklist = new TaskChecklist(tenantId, taskId, "Alt görev", 1);
      UUID checklistId = UUID.randomUUID();
      when(checklistRepo.findById(checklistId)).thenReturn(Optional.of(checklist));

      detailService.completeChecklist(tenantId, taskId, checklistId, userId);

      assertThat(checklist.isCompleted()).isTrue();
      assertThat(checklist.getCompletedAt()).isNotNull();
      assertThat(checklist.getCompletedByUserId()).isEqualTo(userId);
      verify(checklistRepo).save(checklist);
      verify(activityService)
          .logActivity(
              eq(tenantId),
              eq(taskId),
              eq(userId),
              eq(TaskAction.CHECKLIST_COMPLETED),
              isNull(),
              eq("Alt görev"),
              isNull());
    }
  }

  @Nested
  @DisplayName("Timer — Happy Path")
  class TimerHappyPath {

    @Test
    @DisplayName("stopTimer — doğru task ve tenant ile timer durdurulur ve loglanır")
    void stopTimer_happyPath_stopsAndLogs() {
      TaskTimeEntry activeTimer = new TaskTimeEntry(tenantId, taskId, userId, clock);
      when(timeEntryRepo.findActiveTimerByUserId(userId)).thenReturn(Optional.of(activeTimer));

      detailService.stopTimer(tenantId, taskId, userId);

      assertThat(activeTimer.getEndedAt()).isNotNull();
      assertThat(activeTimer.getDurationMinutes()).isNotNull();
      verify(timeEntryRepo).save(activeTimer);
      verify(activityService)
          .logActivity(
              eq(tenantId),
              eq(taskId),
              eq(userId),
              eq(TaskAction.TIMER_STOPPED),
              isNull(),
              any(String.class),
              isNull());
    }
  }
}
