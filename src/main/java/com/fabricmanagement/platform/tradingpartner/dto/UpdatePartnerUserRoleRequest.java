package com.fabricmanagement.platform.tradingpartner.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for updating a partner user's role. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePartnerUserRoleRequest {

  @NotBlank(message = "Partner role code is required")
  private String partnerRoleCode;
}
