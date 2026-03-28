package com.fabricmanagement.platform.communication.dto;

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
public class AssignAddressRequest {

  @NotNull(message = "Address ID is required")
  private UUID addressId;

  @Builder.Default private Boolean isPrimary = false;

  private Boolean isWorkAddress; // For UserAddress only
  private Boolean isHeadquarters; // For CompanyAddress only
}
