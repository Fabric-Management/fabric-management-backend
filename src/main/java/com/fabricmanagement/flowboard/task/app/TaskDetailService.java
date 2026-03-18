package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.flowboard.task.domain.AttachmentType;
import com.fabricmanagement.flowboard.task.domain.TaskAction;
import com.fabricmanagement.flowboard.task.domain.TaskAttachment;
import com.fabricmanagement.flowboard.task.domain.TaskChecklist;
import com.fabricmanagement.flowboard.task.domain.TaskComment;
import com.fabricmanagement.flowboard.task.domain.TaskTimeEntry;
import com.fabricmanagement.flowboard.task.infra.repository.TaskAttachmentRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskChecklistRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskCommentRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskTimeEntryRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskDetailService {

  private final TaskChecklistRepository checklistRepo;
  private final TaskCommentRepository commentRepo;
  private final TaskTimeEntryRepository timeEntryRepo;
  private final TaskAttachmentRepository attachmentRepo;

  private final TaskActivityService activityService;
  private final Clock clock;

  @Transactional
  public TaskChecklist addChecklist(
      UUID tenantId, UUID taskId, String title, int displayOrder, UUID requestedByUserId) {
    var checklist = new TaskChecklist(tenantId, taskId, title, displayOrder);
    checklistRepo.save(checklist);
    // [K2 FIX] Audit trail bütünlüğü — checklist ekleme de loglanıyor
    activityService.logActivity(
        tenantId, taskId, requestedByUserId, TaskAction.CHECKLIST_ADDED, null, title, null);
    return checklist;
  }

  @Transactional
  public void completeChecklist(
      UUID tenantId, UUID taskId, UUID checklistId, UUID requestedByUserId) {
    var checklist = checklistRepo.findById(checklistId).orElseThrow();
    // [D2 FIX] Tenant guard — farklı tenant'ın checklistini tamamlama koruması
    if (!checklist.getTenantId().equals(tenantId)) {
      throw new IllegalStateException("Checklist does not belong to this tenant");
    }
    checklist.complete(requestedByUserId, clock);
    checklistRepo.save(checklist);
    activityService.logActivity(
        tenantId,
        taskId,
        requestedByUserId,
        TaskAction.CHECKLIST_COMPLETED,
        null,
        checklist.getTitle(),
        null);
  }

  @Transactional
  public TaskComment addComment(
      UUID tenantId, UUID taskId, UUID userId, String content, String mentionedUserIds) {
    var comment = new TaskComment(tenantId, taskId, userId, content, mentionedUserIds);
    commentRepo.save(comment);
    activityService.logActivity(tenantId, taskId, userId, TaskAction.COMMENTED, null, null, null);
    return comment;
  }

  @Transactional
  public TaskTimeEntry startTimer(UUID tenantId, UUID taskId, UUID userId) {
    // 1. Kullanıcının halihazırda açık bir timer'ı var mı kontrol et
    timeEntryRepo
        .findActiveTimerByUserId(userId)
        .ifPresent(
            activeTimer -> {
              // Öncesini otomatik stop et veya hata fırlat (Mondays.com stili otomatik stop daha
              // konforludur)
              activeTimer.stopTimer(clock);
              timeEntryRepo.save(activeTimer);
              activityService.logActivity(
                  activeTimer.getTenantId(),
                  activeTimer.getTaskId(),
                  userId,
                  TaskAction.TIMER_STOPPED,
                  null,
                  String.valueOf(activeTimer.getDurationMinutes()),
                  null);
            });

    var timer = new TaskTimeEntry(tenantId, taskId, userId, clock);
    timeEntryRepo.save(timer);
    activityService.logActivity(
        tenantId, taskId, userId, TaskAction.TIMER_STARTED, null, null, null);
    return timer;
  }

  @Transactional
  public void stopTimer(UUID tenantId, UUID taskId, UUID userId) {
    var activeTimer = timeEntryRepo.findActiveTimerByUserId(userId).orElseThrow();
    // [D3 FIX] Tenant guard — farklı tenant'ın timer'ını durdurma koruması
    if (!activeTimer.getTenantId().equals(tenantId)) {
      throw new IllegalStateException("Timer does not belong to this tenant");
    }
    if (!activeTimer.getTaskId().equals(taskId)) {
      throw new IllegalStateException("Active timer is on a different task");
    }

    activeTimer.stopTimer(clock);
    timeEntryRepo.save(activeTimer);
    activityService.logActivity(
        tenantId,
        taskId,
        userId,
        TaskAction.TIMER_STOPPED,
        null,
        String.valueOf(activeTimer.getDurationMinutes()),
        null);
  }

  @Transactional
  public TaskAttachment addAttachment(
      UUID tenantId,
      UUID taskId,
      String fileName,
      String fileType,
      long fileSize,
      String storagePath,
      UUID userId,
      AttachmentType type,
      String description) {
    var attachment =
        new TaskAttachment(
            tenantId, taskId, fileName, fileType, fileSize, storagePath, userId, type, description);
    attachmentRepo.save(attachment);
    activityService.logActivity(
        tenantId, taskId, userId, TaskAction.ATTACHMENT_ADDED, null, fileName, null);
    return attachment;
  }
}
