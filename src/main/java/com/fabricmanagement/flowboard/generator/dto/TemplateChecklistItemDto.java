package com.fabricmanagement.flowboard.generator.dto;

import com.fabricmanagement.flowboard.task.domain.Priority;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class TemplateChecklistItemDto {
  private String title;
  private Priority priority;
  private BigDecimal estimatedHours;
}
