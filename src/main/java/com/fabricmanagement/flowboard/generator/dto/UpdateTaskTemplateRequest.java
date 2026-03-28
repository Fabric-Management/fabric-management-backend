package com.fabricmanagement.flowboard.generator.dto;

import com.fabricmanagement.flowboard.generator.domain.AssigneeRole;
import com.fabricmanagement.flowboard.task.domain.ModuleType;
import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class UpdateTaskTemplateRequest {
  @NotBlank private String name;
  private String description;
  @NotBlank private String eventType;
  @NotBlank private String titleTemplate;
  @NotNull private TaskType taskType;
  private ModuleType moduleType;
  @NotNull private Priority defaultPriority;
  @NotNull private AssigneeRole defaultAssigneeRole;
  private BigDecimal estimatedHours;

  private List<TemplateChecklistItemDto> checkList;
  private List<String> labels;

  private boolean isActive;
}
