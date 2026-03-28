package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException;
import com.fabricmanagement.flowboard.task.domain.AttachmentType;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskAction;
import com.fabricmanagement.flowboard.task.domain.TaskAttachment;
import com.fabricmanagement.flowboard.task.domain.TaskChecklist;
import com.fabricmanagement.flowboard.task.domain.TaskComment;
import com.fabricmanagement.flowboard.task.domain.TaskTimeEntry;
import com.fabricmanagement.flowboard.task.domain.event.TaskChecklistCompletedEvent;
import com.fabricmanagement.flowboard.task.dto.AddTaskCommentRequest;
import com.fabricmanagement.flowboard.task.dto.TaskCommentResponse;
import com.fabricmanagement.flowboard.task.infra.repository.TaskAttachmentRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskChecklistRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskCommentRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskTimeEntryRepository;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import com.fabricmanagement.platform.user.dto.UserDto;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private final TaskRepository taskRepo;
  private final UserFacade userFacade;

  private final TaskActivityService activityService;
  private final DomainEventPublisher eventPublisher;
  private final Clock clock;

  @Transactional
  public TaskChecklist addChecklist(
      UUID tenantId, UUID taskId, String title, int displayOrder, UUID requestedByUserId) {
    var checklist = new TaskChecklist(tenantId, taskId, title, displayOrder);
    checklistRepo.save(checklist);
    activityService.logActivity(
        tenantId, taskId, requestedByUserId, TaskAction.CHECKLIST_ADDED, null, title, null);
    return checklist;
  }

  @Transactional
  public void completeChecklist(
      UUID tenantId, UUID taskId, UUID checklistId, UUID requestedByUserId) {
    var checklist = checklistRepo.findById(checklistId).orElseThrow();
    if (!checklist.getTenantId().equals(tenantId)) {
      throw new FlowBoardDomainException(
          "Checklist does not belong to this tenant", "FLOWBOARD_CHECKLIST_TENANT_MISMATCH", 403);
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

    Task task =
        taskRepo
            .findById(taskId)
            .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
    eventPublisher.publish(
        new TaskChecklistCompletedEvent(
            tenantId, taskId, task.getBoardId(), checklistId, requestedByUserId));
  }

  @Transactional(readOnly = true)
  public List<TaskCommentResponse> getComments(UUID tenantId, UUID taskId) {
    requireTaskInTenant(tenantId, taskId);
    List<TaskComment> comments = commentRepo.findAllByTaskIdOrderByCreatedAtAsc(taskId);
    if (comments.isEmpty()) {
      return List.of();
    }
    Set<UUID> userIds = new HashSet<>();
    for (TaskComment c : comments) {
      userIds.add(c.getUserId());
    }
    Map<UUID, UserDto> userMap = new HashMap<>();
    for (UUID uid : userIds) {
      userFacade.findById(tenantId, uid).ifPresent(u -> userMap.put(uid, u));
    }
    return comments.stream()
        .map(c -> TaskCommentResponse.from(c, userMap.get(c.getUserId())))
        .toList();
  }

  @Transactional
  public TaskComment addComment(
      UUID tenantId, UUID taskId, UUID userId, String content, List<UUID> mentionedUserIds) {
    requireTaskInTenant(tenantId, taskId);
    var comment = new TaskComment(tenantId, taskId, userId, content, mentionedUserIds);
    commentRepo.save(comment);
    activityService.logActivity(tenantId, taskId, userId, TaskAction.COMMENTED, null, null, null);
    return comment;
  }

  @Transactional
  public TaskCommentResponse addCommentFromRequest(
      UUID tenantId, UUID taskId, UUID userId, AddTaskCommentRequest req) {
    List<UUID> mentions = req.mentionedUserIds() != null ? req.mentionedUserIds() : List.of();
    TaskComment saved = addComment(tenantId, taskId, userId, req.content(), mentions);
    UserDto author = userFacade.findById(tenantId, userId).orElse(null);
    return TaskCommentResponse.from(saved, author);
  }

  private void requireTaskInTenant(UUID tenantId, UUID taskId) {
    Task task =
        taskRepo
            .findById(taskId)
            .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
    if (!task.getTenantId().equals(tenantId)) {
      throw new FlowBoardDomainException(
          "Task not found or access denied", "FLOWBOARD_TASK_ACCESS_DENIED", 403);
    }
  }

  @Transactional
  public TaskTimeEntry startTimer(UUID tenantId, UUID taskId, UUID userId) {
    timeEntryRepo
        .findActiveTimerByUserId(userId)
        .ifPresent(
            activeTimer -> {
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
    if (!activeTimer.getTenantId().equals(tenantId)) {
      throw new FlowBoardDomainException(
          "Timer does not belong to this tenant", "FLOWBOARD_TIMER_TENANT_MISMATCH", 403);
    }
    if (!activeTimer.getTaskId().equals(taskId)) {
      throw new FlowBoardDomainException(
          "Active timer is on a different task", "FLOWBOARD_TIMER_TASK_MISMATCH", 409);
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
