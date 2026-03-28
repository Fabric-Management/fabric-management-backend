package com.fabricmanagement.flowboard.task.dto;

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
public class TaskAssigneeResponse {
  private UUID userId;
  private String fullName;
  private String avatarInitials;
  private String assignedBy;
  private Instant assignedAt;
}
