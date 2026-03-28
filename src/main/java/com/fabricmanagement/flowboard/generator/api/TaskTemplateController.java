package com.fabricmanagement.flowboard.generator.api;

import com.fabricmanagement.flowboard.generator.app.TaskTemplateService;
import com.fabricmanagement.flowboard.generator.dto.CreateTaskTemplateRequest;
import com.fabricmanagement.flowboard.generator.dto.TaskTemplateDto;
import com.fabricmanagement.flowboard.generator.dto.UpdateTaskTemplateRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/flowboard/templates")
@RequiredArgsConstructor
public class TaskTemplateController {

  private final TaskTemplateService service;

  @GetMapping
  @PreAuthorize("hasAnyAuthority('PLATFORM_ADMIN', 'DEPARTMENT_ADMIN', 'MANAGER')")
  public List<TaskTemplateDto> getAllTemplates() {
    return service.getAllTemplates();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('PLATFORM_ADMIN', 'DEPARTMENT_ADMIN', 'MANAGER')")
  public TaskTemplateDto getTemplate(@PathVariable UUID id) {
    return service.getTemplateById(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyAuthority('PLATFORM_ADMIN')")
  public TaskTemplateDto createTemplate(@Valid @RequestBody CreateTaskTemplateRequest request) {
    return service.createTemplate(request);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('PLATFORM_ADMIN')")
  public TaskTemplateDto updateTemplate(
      @PathVariable UUID id, @Valid @RequestBody UpdateTaskTemplateRequest request) {
    return service.updateTemplate(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyAuthority('PLATFORM_ADMIN')")
  public void deleteTemplate(@PathVariable UUID id) {
    service.deleteTemplate(id);
  }
}
