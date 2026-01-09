package com.fabricmanagement.common.platform.user.dto;

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
public class AssignDepartmentRequest {

  @NotNull(message = "Department ID is required")
  private UUID departmentId;

  @Builder.Default private Boolean isPrimary = false;
}
