package com.fabricmanagement.production.execution.batch.dto;

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
public class TransferBatchRequest {

  @NotNull(message = "New location ID is required")
  private UUID newLocationId;

  private String remarks;
}
