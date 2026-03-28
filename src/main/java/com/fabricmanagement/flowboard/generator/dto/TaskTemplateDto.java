package com.fabricmanagement.flowboard.generator.dto;

import com.fabricmanagement.flowboard.generator.domain.AssigneeRole;
import com.fabricmanagement.flowboard.task.domain.ModuleType;
import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class TaskTemplateDto {
  private UUID id;
  private String name;
  private String description;
  private String eventType;
  private String titleTemplate;
  private TaskType taskType;
  private ModuleType moduleType;
  private Priority defaultPriority;
  private AssigneeRole defaultAssigneeRole;
  private BigDecimal estimatedHours;
  private List<TemplateChecklistItemDto> checkList;
  private List<String> labels;
  private boolean isActive;
}
