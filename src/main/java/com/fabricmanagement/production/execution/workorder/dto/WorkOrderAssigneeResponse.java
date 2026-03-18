package com.fabricmanagement.production.execution.workorder.dto;

import com.fabricmanagement.production.execution.workorder.domain.AssigneeRole;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderAssigneeResponse {
  private UUID id;
  private UUID workOrderId;
  private AssigneeRole role;
  private UUID departmentId;
  private UUID userId;
  private Instant assignedAt;
}
