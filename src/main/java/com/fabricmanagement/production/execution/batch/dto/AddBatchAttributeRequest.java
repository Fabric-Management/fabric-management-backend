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
public class AddBatchAttributeRequest {

  @NotNull(message = "Attribute ID is required")
  private UUID attributeId;

  private String value;
}
