package com.fabricmanagement.production.execution.workorder.dto;

import com.fabricmanagement.production.execution.workorder.domain.AssigneeRole;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderAssigneeRequest {

  @NotNull(message = "Role is mandatory")
  private AssigneeRole role;

  private UUID departmentId;

  private UUID userId;
}
