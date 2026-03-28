package com.fabricmanagement.platform.user.dto;

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
public class UserDepartmentDto {

  private UUID userId;
  private UUID departmentId;
  private Boolean isPrimary;
  private Instant assignedAt;
  private UUID assignedBy;
}
