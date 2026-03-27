package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.task.domain.ModuleType;
import com.fabricmanagement.flowboard.task.domain.RecurringTaskTemplate;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.dto.CreateTaskRequest;
import com.fabricmanagement.flowboard.task.infra.repository.RecurringTaskTemplateRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringTaskService {

  private final RecurringTaskTemplateRepository templateRepo;
  private final TaskService taskService;
  private final Clock clock;

  // [O8 FIX] Controller'dan doğrudan repository erişimi yerine servis katmanı
  @Transactional(readOnly = true)
  public List<RecurringTaskTemplate> getTemplatesForBoard(UUID tenantId, UUID boardId) {
    return templateRepo.findByTenantIdAndBoardIdAndDeletedAtIsNull(tenantId, boardId);
  }

  @Transactional
  public void triggerDueTemplates() {
    OffsetDateTime now = OffsetDateTime.now(clock);
    List<RecurringTaskTemplate> templates = templateRepo.findDueTemplates(null, now);
    log.info("Found {} recurring task templates to trigger.", templates.size());

    for (RecurringTaskTemplate template : templates) {
      try {
        spawnTaskFromTemplate(template, now);
      } catch (Exception e) {
        log.error("Failed to spawn task from template: {}", template.getId(), e);
      }
    }
  }

  private void spawnTaskFromTemplate(RecurringTaskTemplate template, OffsetDateTime now) {
    log.info("Spawning task from template: {}", template.getId());

    TenantContext.executeInTenantContext(
        template.getTenantId(),
        () -> {
          CreateTaskRequest request =
              new CreateTaskRequest(
                  template.getBoardId(),
                  template.getTitle(),
                  template.getDescription(),
                  template.getTaskType(),
                  ModuleType.GENERAL,
                  template.getPriority(),
                  null,
                  null,
                  null,
                  null);
          Task newTask = taskService.createTask(request);

          if (template.getTargetAssigneeId() != null) {
            taskService.assignToUser(
                newTask.getId(),
                template.getTargetAssigneeId(),
                com.fabricmanagement.flowboard.task.domain.AssignedBy.SYSTEM,
                com.fabricmanagement.platform.user.domain.SystemUser.ID);
          }

          OffsetDateTime nextTrigger = calculateNextTrigger(template, now);
          template.markAsSpawned(newTask.getId(), nextTrigger, clock);
          templateRepo.save(template);
        });
  }

  private OffsetDateTime calculateNextTrigger(RecurringTaskTemplate template, OffsetDateTime now) {
    int interval = template.getIntervalValue() != null ? template.getIntervalValue() : 1;
    return switch (template.getFrequency()) {
      case DAILY -> now.plusDays(interval);
      case WEEKLY -> now.plusWeeks(interval);
      case MONTHLY -> now.plusMonths(interval);
      case QUARTERLY -> now.plusMonths(3L * interval);
      case YEARLY -> now.plusYears(interval);
      // ON_COMPLETION: nextTriggerAt null kalır, task tamamlandığında event listener tetikler
      // CUSTOM_CRON: Cron parser entegrasyonu ileride eklenecek
      case ON_COMPLETION, CUSTOM_CRON -> null;
    };
  }
}
