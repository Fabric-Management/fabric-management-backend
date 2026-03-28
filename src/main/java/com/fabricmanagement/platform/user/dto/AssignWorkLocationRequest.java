package com.fabricmanagement.platform.user.dto;

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
public class AssignWorkLocationRequest {

  @NotNull(message = "Organization address ID is required")
  private UUID orgAddressId;

  private Boolean isPrimary;

  private String notes;
}
