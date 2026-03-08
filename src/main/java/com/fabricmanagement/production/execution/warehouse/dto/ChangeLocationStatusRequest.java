package com.fabricmanagement.production.execution.warehouse.dto;

import com.fabricmanagement.production.execution.warehouse.domain.LocationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeLocationStatusRequest {

  @NotNull(message = "Version is required for optimistic locking")
  private Long version;

  @NotNull(message = "Status is required")
  private LocationStatus status;

  @Size(max = 500, message = "Reason must not exceed 500 characters")
  private String reason;
}
