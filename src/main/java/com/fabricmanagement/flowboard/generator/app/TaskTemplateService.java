package com.fabricmanagement.flowboard.generator.app;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.flowboard.generator.api.TaskTemplateMapper;
import com.fabricmanagement.flowboard.generator.domain.TaskTemplate;
import com.fabricmanagement.flowboard.generator.dto.CreateTaskTemplateRequest;
import com.fabricmanagement.flowboard.generator.dto.TaskTemplateDto;
import com.fabricmanagement.flowboard.generator.dto.UpdateTaskTemplateRequest;
import com.fabricmanagement.flowboard.generator.infra.repository.TaskTemplateRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskTemplateService {

  private final TaskTemplateRepository repository;
  private final TaskTemplateMapper mapper;

  @Transactional(readOnly = true)
  public List<TaskTemplateDto> getAllTemplates() {
    // Inactive templates stay manageable; soft-deleted (catalogue) rows are gone for the API.
    List<TaskTemplate> templates = repository.findAllByDeletedAtIsNull();
    return mapper.toDtoList(templates);
  }

  @Transactional(readOnly = true)
  public TaskTemplateDto getTemplateById(UUID id) {
    TaskTemplate template =
        repository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("TaskTemplate not found: " + id));
    return mapper.toDto(template);
  }

  @Transactional
  public TaskTemplateDto createTemplate(CreateTaskTemplateRequest request) {
    String checkListJson = mapper.toJson(request.getCheckList());
    String labelsJson = mapper.toJson(request.getLabels());

    TaskTemplate template =
        TaskTemplate.create(
            request.getName(),
            request.getDescription(),
            request.getEventType(),
            request.getTitleTemplate(),
            request.getTaskType(),
            request.getModuleType(),
            request.getDefaultPriority(),
            request.getDefaultAssigneeRole(),
            request.getEstimatedHours(),
            labelsJson,
            checkListJson);

    template = repository.save(template);
    return mapper.toDto(template);
  }

  @Transactional
  public TaskTemplateDto updateTemplate(UUID id, UpdateTaskTemplateRequest request) {
    TaskTemplate template =
        repository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("TaskTemplate not found: " + id));

    String checkListJson = mapper.toJson(request.getCheckList());
    String labelsJson = mapper.toJson(request.getLabels());

    template.update(
        request.getName(),
        request.getDescription(),
        request.getEventType(),
        request.getTitleTemplate(),
        request.getTaskType(),
        request.getModuleType(),
        request.getDefaultPriority(),
        request.getDefaultAssigneeRole(),
        request.getEstimatedHours(),
        labelsJson,
        checkListJson);

    if (!request.isActive()) {
      template.deactivate();
    } else {
      template.activate();
    }

    template = repository.save(template);
    return mapper.toDto(template);
  }

  @Transactional
  public void deleteTemplate(UUID id) {
    TaskTemplate template =
        repository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("TaskTemplate not found: " + id));
    if (template.getCatalogKey() != null) {
      // TASK-TEMPLATE-TENANCY-1: a catalogue row is soft-deleted so the backfill sees it (R0)
      // and never re-creates it; it is invisible to this API from now on (404).
      template.softDelete();
      repository.save(template);
      return;
    }
    repository.delete(template);
  }
}
