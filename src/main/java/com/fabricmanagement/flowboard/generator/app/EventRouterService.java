package com.fabricmanagement.flowboard.generator.app;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.fabricmanagement.flowboard.board.domain.BoardType;
import com.fabricmanagement.flowboard.board.infra.repository.BoardRepository;
import com.fabricmanagement.flowboard.generator.app.adapter.DomainEventAdapter;
import com.fabricmanagement.flowboard.generator.app.adapter.TaskTemplateContext;
import com.fabricmanagement.flowboard.generator.domain.TaskTemplate;
import com.fabricmanagement.flowboard.generator.infra.repository.TaskTemplateRepository;
import com.fabricmanagement.flowboard.task.app.TaskLabelService;
import com.fabricmanagement.flowboard.task.app.TaskService;
import com.fabricmanagement.flowboard.task.domain.ModuleType;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.fabricmanagement.flowboard.task.dto.CreateTaskRequest;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import com.fabricmanagement.platform.user.domain.SystemUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Gelen Domain Event türüne göre uygun TaskTemplate'leri bulup task yaratan yönlendirici (Faz 4.1)
 */
@Service
@Slf4j
public class EventRouterService {

  private final Map<Class<?>, DomainEventAdapter<?>> adapterMap;
  private final TaskTemplateRepository templateRepo;
  private final TaskService taskService;
  private final TaskRepository taskRepo;
  private final BoardRepository boardRepo;
  private final TaskLabelService taskLabelService;

  public EventRouterService(
      List<DomainEventAdapter<?>> adapters,
      TaskTemplateRepository templateRepo,
      TaskService taskService,
      TaskRepository taskRepo,
      BoardRepository boardRepo,
      TaskLabelService taskLabelService) {
    this.adapterMap =
        adapters.stream().collect(toMap(DomainEventAdapter::getSupportedEventType, identity()));
    this.templateRepo = templateRepo;
    this.taskService = taskService;
    this.taskRepo = taskRepo;
    this.boardRepo = boardRepo;
    this.taskLabelService = taskLabelService;
  }

  @SuppressWarnings("unchecked")
  public <T> void route(T event) {
    DomainEventAdapter<T> adapter = (DomainEventAdapter<T>) adapterMap.get(event.getClass());
    if (adapter == null) {
      log.debug("EventRouter: No adapter found for event {}", event.getClass().getSimpleName());
      return;
    }

    String eventType = adapter.getEventTypeName();
    List<TaskTemplate> templates = templateRepo.findByEventTypeAndIsActiveTrue(eventType);
    if (templates.isEmpty()) {
      log.debug("EventRouter: No active TaskTemplate for {} — skipping", eventType);
      return;
    }

    // Uygulanabilir task tiplerinin filtrelenmesi (Örn: SalesOrder için StockControl analiz sonucu)
    List<TaskType> allTemplateTypes = templates.stream().map(TaskTemplate::getTaskType).toList();
    List<TaskType> allowedTypes = adapter.determineTaskTypes(event, allTemplateTypes);

    TaskTemplateContext ctx = adapter.buildContext(event);

    for (TaskTemplate template : templates) {
      if (!allowedTypes.contains(template.getTaskType())) {
        continue; // Adapter bu taskType için ret verdi
      }

      String title = interpolateTitle(template.getTitleTemplate(), ctx);
      createTaskFromTemplate(template, title, ctx);
    }
  }

  private String interpolateTitle(String titleTemplate, TaskTemplateContext ctx) {
    if (titleTemplate == null) {
      return ctx.entityType() + " — " + ctx.entityRef();
    }
    String result = titleTemplate.replace("{entityRef}", ctx.entityRef());
    if (ctx.templateVariables() != null) {
      for (Map.Entry<String, String> entry : ctx.templateVariables().entrySet()) {
        String key = "{" + entry.getKey() + "}";
        String val = entry.getValue() != null ? entry.getValue() : "";
        result = result.replace(key, val);
      }
    }
    return result;
  }

  private void createTaskFromTemplate(
      TaskTemplate template, String title, TaskTemplateContext ctx) {
    // Idempotency zorunlu
    if (ctx.entityId() == null) {
      throw new IllegalArgumentException(
          String.format(
              "SmartTaskGenerator: entityId cannot be null for eventType=%s. Idempotency is required.",
              template.getEventType()));
    }

    if (taskRepo.existsOpenTaskByEntityAndType(
        ctx.entityType(), ctx.entityId(), template.getTaskType())) {
      log.info(
          "Idempotency: {} task already open for entity {}={} — skipping",
          template.getTaskType(),
          ctx.entityType(),
          ctx.entityId());
      return;
    }

    UUID boardId = resolveBoardId(template, ctx.tenantId());
    if (boardId == null) {
      log.warn("No matching board for template={} — task not created", template.getId());
      return;
    }

    if (template.getAutoLabels() != null && !template.getAutoLabels().isBlank()) {
      log.info(
          "SmartTaskGenerator: auto_labels={} for taskType={} — label assignment pending",
          template.getAutoLabels(),
          template.getTaskType());
    }

    var req =
        new CreateTaskRequest(
            boardId,
            title,
            null,
            template.getTaskType(),
            template.getModuleType() != null ? template.getModuleType() : ModuleType.GENERAL,
            template.getDefaultPriority(),
            ctx.deadline(),
            template.getEstimatedHours(),
            ctx.entityType(),
            ctx.entityId(),
            "TEMPLATE",
            template.getId());

    var task = taskService.createTask(req);
    log.info(
        "SmartTaskGenerator created: taskId={} taskType={} entityType={}",
        task.getId(),
        template.getTaskType(),
        ctx.entityType());

    if (template.getAutoLabels() != null && !template.getAutoLabels().isBlank()) {
      for (String label : template.getAutoLabels().split(",")) {
        String trimmedLabel = label.trim();
        if (!trimmedLabel.isEmpty()) {
          try {
            taskLabelService.assignLabelByName(
                ctx.tenantId(), boardId, task.getId(), trimmedLabel, SystemUser.ID);
          } catch (Exception e) {
            log.warn(
                "SmartTaskGenerator: auto_label assignment failed for label='{}' taskId={}: {}",
                trimmedLabel,
                task.getId(),
                e.getMessage());
          }
        }
      }
    }
  }

  private UUID resolveBoardId(TaskTemplate template, UUID tenantId) {
    if (template.getModuleType() != null) {
      try {
        BoardType boardType = BoardType.valueOf(template.getModuleType().name());
        return boardRepo
            .findByTenantIdAndBoardType(tenantId, boardType)
            .map(b -> b.getId())
            .orElse(null);
      } catch (IllegalArgumentException e) {
        log.debug(
            "No matching BoardType for moduleType={} — falling back to GLOBAL",
            template.getModuleType());
      }
    }
    return boardRepo
        .findByTenantIdAndBoardType(tenantId, BoardType.GLOBAL)
        .map(b -> b.getId())
        .orElse(null);
  }
}
