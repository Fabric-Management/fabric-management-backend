package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.flowboard.task.domain.TaskAction;
import com.fabricmanagement.flowboard.task.domain.TaskLabelAssignment;
import com.fabricmanagement.flowboard.task.infra.repository.TaskLabelAssignmentRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskLabelRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Task etiketleme operasyonlarını yönetir (AUT3 çözümü). */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskLabelService {

  private final TaskLabelAssignmentRepository labelAssignmentRepository;
  private final TaskLabelRepository labelRepository;
  private final TaskActivityService activityService;

  @Transactional
  public void assignLabel(UUID tenantId, UUID taskId, UUID labelId, UUID requestedByUserId) {
    if (labelAssignmentRepository.existsByTaskIdAndLabelId(taskId, labelId)) {
      return; // Zaten atanmış
    }

    var assignment = TaskLabelAssignment.create(tenantId, taskId, labelId);
    labelAssignmentRepository.save(assignment);

    activityService.logActivity(
        tenantId,
        taskId,
        requestedByUserId,
        TaskAction.LABEL_ADDED,
        null,
        labelId.toString(),
        null);
  }

  @Transactional
  public void removeLabel(UUID tenantId, UUID taskId, UUID labelId, UUID requestedByUserId) {
    var assignment = labelAssignmentRepository.findByTaskIdAndLabelId(taskId, labelId);
    if (assignment.isPresent()) {
      labelAssignmentRepository.delete(assignment.get());
      activityService.logActivity(
          tenantId,
          taskId,
          requestedByUserId,
          TaskAction.LABEL_REMOVED,
          labelId.toString(),
          null,
          null);
    }
  }

  @Transactional
  public void assignLabelByName(
      UUID tenantId, UUID boardId, UUID taskId, String labelName, UUID requestedByUserId) {
    // [O5 FIX] Doğrudan DB sorgusu — tüm label'ları belleğe yüklemek yerine tek sorgu.
    var label =
        labelRepository
            .findByTenantIdAndBoardIdAndNameIgnoreCase(tenantId, boardId, labelName)
            .orElse(null);
    if (label != null) {
      assignLabel(tenantId, taskId, label.getId(), requestedByUserId);
    } else {
      log.warn("Label not found by name: {} for board {}", labelName, boardId);
    }
  }
}
